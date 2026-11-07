
package pe.atena.mic

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import java.lang.ref.WeakReference
import java.util.Locale
import kotlin.math.max
import kotlin.math.round

class WashListenerService : Service() {

    companion object {
        const val ACTION_START = "pe.atena.wash.START"
        const val ACTION_STOP = "pe.atena.wash.STOP"
        const val ACTION_REARM = "pe.atena.wash.REARM"
        const val ACTION_OPEN = "pe.atena.wash.OPEN"
        const val ACTION_STATUS = "pe.atena.wash.STATUS"
        const val ACTION_METER = "pe.atena.wash.METER"

        const val EXTRA_MODE = "mode"
        const val EXTRA_INPUT_ID = "input_id"
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_LEVEL = "level"
        const val EXTRA_SOURCE = "source"

        const val MODE_MONITOR = "monitor"
        const val MODE_LOCAL = "local"
        const val MODE_COMPATIBLE = "compatible"

        private const val CHANNEL_ID = "jarvis_v16"
        private const val NOTIFICATION_ID = 313

        private var current =
            WeakReference<WashListenerService>(null)

        fun openFromMenuIfRunning(): Boolean {
            val service = current.get() ?: return false
            if (!service.active) return false

            service.handler.post {
                service.openChatGPT()
            }
            return true
        }

        fun isWaitingForChat(): Boolean =
            current.get()?.let {
                it.active &&
                it.pausedForChat &&
                !it.monitoring
            } == true

        fun rearmAfterChatLeft() {
            current.get()?.let { service ->
                service.handler.post {
                    val autoRearm =
                        service.getSharedPreferences(
                            "atena",
                            MODE_PRIVATE
                        ).getBoolean("auto_rearm", true)

                    if (
                        service.active &&
                        service.pausedForChat &&
                        autoRearm
                    ) {
                        service.publish(
                            "Reactivando JARVIS después de ChatGPT."
                        )

                        service.stopCurrentCapture()
                        service.pausedForChat = false
                        service.failures = 0
                        service.mode = service.lastMode
                        service.scheduleRestart(1200)

                        service.notifyUser(
                            "JARVIS reactivando escucha"
                        )
                    }
                }
            }
        }
    }

    private lateinit var audioManager: AudioManager

    private val handler = Handler(Looper.getMainLooper())

    private var recognizer: SpeechRecognizer? = null
    private var audioRecord: AudioRecord? = null
    private var monitorThread: Thread? = null
    private var exitTts: TextToSpeech? = null

    @Volatile
    private var active = false

    @Volatile
    private var monitoring = false

    private var pausedForChat = false
    private var speakingExit = false

    private var generation = 0
    private var failures = 0
    private var mode = MODE_LOCAL
    private var lastMode = MODE_LOCAL
    private var inputId = -1

    private var savedAudioMode: Int? = null
    private var routeSelected = false
    private var lastLevelSent = 0L

    private var partialCandidate = ""
    private var partialConfirmations = 0

    private val restart = Runnable {
        if (
            active &&
            !pausedForChat &&
            !monitoring &&
            !speakingExit
        ) {
            startRecognition()
        }
    }

    private val watchdog = Runnable {
        if (
            active &&
            !pausedForChat &&
            !monitoring &&
            !speakingExit
        ) {
            publish(
                "El reconocedor dejó de responder. Reiniciando."
            )
            scheduleRestart(1200)
        }
    }

    override fun onCreate() {
        super.onCreate()

        audioManager =
            getSystemService(AudioManager::class.java)

        current = WeakReference(this)

        getSystemService(
            NotificationManager::class.java
        ).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "JARVIS · Voz",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                setSound(null, null)
                enableVibration(false)
            }
        )
    }

    private fun pendingService(
        actionName: String,
        requestCode: Int
    ): PendingIntent =
        PendingIntent.getForegroundService(
            this,
            requestCode,
            Intent(
                this,
                WashListenerService::class.java
            ).apply {
                action = actionName
            },
            PendingIntent.FLAG_IMMUTABLE or
                PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun notification(message: String): Notification {
        val mainIntent = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or
                PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder =
            Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(
                    android.R.drawable.ic_btn_speak_now
                )
                .setContentTitle("JARVIS")
                .setContentText(message.take(100))
                .setContentIntent(mainIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addAction(
                    Notification.Action.Builder(
                        null,
                        "REANUDAR JARVIS",
                        pendingService(ACTION_REARM, 2)
                    ).build()
                )
                .addAction(
                    Notification.Action.Builder(
                        null,
                        "DETENER",
                        pendingService(ACTION_STOP, 4)
                    ).build()
                )

        ChatOpener.intent(this)?.let { launch ->
            val openChat = PendingIntent.getActivity(
                this,
                3,
                launch,
                PendingIntent.FLAG_IMMUTABLE or
                    PendingIntent.FLAG_UPDATE_CURRENT
            )

            builder.addAction(
                Notification.Action.Builder(
                    null,
                    "ABRIR CHATGPT",
                    openChat
                ).build()
            )
        }

        return builder.build()
    }

    private fun notifyUser(message: String) {
        getSystemService(
            NotificationManager::class.java
        ).notify(
            NOTIFICATION_ID,
            notification(message)
        )
    }

    private fun publish(message: String) {
        getSharedPreferences(
            "atena",
            MODE_PRIVATE
        ).edit()
            .putString(
                "last_status",
                "• $message"
            )
            .apply()

        sendBroadcast(
            Intent(ACTION_STATUS).apply {
                setPackage(packageName)
                putExtra(EXTRA_MESSAGE, message)
            }
        )
    }

    private fun meter(level: Int, source: String) {
        val now = SystemClock.elapsedRealtime()

        if (now - lastLevelSent < 125) return

        lastLevelSent = now

        sendBroadcast(
            Intent(ACTION_METER).apply {
                setPackage(packageName)
                putExtra(
                    EXTRA_LEVEL,
                    level.coerceIn(0, 100)
                )
                putExtra(EXTRA_SOURCE, source)
            }
        )
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        try {
            when (intent?.action) {
                null -> {
                    val saved = getSharedPreferences(
                        "atena",
                        MODE_PRIVATE
                    )

                    if (
                        !saved.getBoolean(
                            "wash_should_listen",
                            false
                        )
                    ) {
                        stopSelf()
                        return START_NOT_STICKY
                    }

                    active = true
                    monitoring = false
                    pausedForChat = false
                    speakingExit = false
                    failures = 0

                    mode = saved.getString(
                        "wash_last_mode",
                        MODE_COMPATIBLE
                    ) ?: MODE_COMPATIBLE

                    lastMode = mode
                    inputId = saved.getInt(
                        "wash_input_id",
                        -1
                    )

                    startForeground(
                        NOTIFICATION_ID,
                        notification(
                            "Recuperando escucha JARVIS"
                        ),
                        PackageManager.FOREGROUND_SERVICE_TYPE_MICROPHONE
                    )

                    startRecognition()
                }

                ACTION_START -> {
                    if (
                        checkSelfPermission(
                            Manifest.permission.RECORD_AUDIO
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        publish(
                            "Falta permiso del micrófono."
                        )
                        stopSelf()
                        return START_NOT_STICKY
                    }

                    stopCurrentCapture()

                    pausedForChat = false
                    monitoring = false
                    speakingExit = false
                    active = true
                    failures = 0

                    inputId = intent.getIntExtra(
                        EXTRA_INPUT_ID,
                        -1
                    )

                    mode = intent.getStringExtra(
                        EXTRA_MODE
                    ) ?: MODE_COMPATIBLE

                    if (mode != MODE_MONITOR) {
                        lastMode = mode
                    }

                    getSharedPreferences(
                        "atena",
                        MODE_PRIVATE
                    ).edit()
                        .putBoolean(
                            "wash_should_listen",
                            mode != MODE_MONITOR
                        )
                        .putString(
                            "wash_last_mode",
                            lastMode
                        )
                        .putInt(
                            "wash_input_id",
                            inputId
                        )
                        .apply()

                    startForeground(
                        NOTIFICATION_ID,
                        notification("Inicializando $mode"),
                        PackageManager.FOREGROUND_SERVICE_TYPE_MICROPHONE
                    )

                    if (mode == MODE_MONITOR) {
                        startMicrophoneTest()
                    } else {
                        startRecognition()
                    }
                }

                ACTION_REARM -> {
                    if (!active) {
                        publish(
                            "Abre JARVIS para iniciar el micrófono."
                        )
                        stopSelf()
                        return START_NOT_STICKY
                    }

                    stopCurrentCapture()
                    pausedForChat = false
                    monitoring = false
                    failures = 0
                    mode = lastMode

                    startRecognition()
                }

                ACTION_OPEN -> {
                    openChatGPT()
                }

                ACTION_STOP -> {
                    getSharedPreferences(
                        "atena",
                        MODE_PRIVATE
                    ).edit()
                        .putBoolean(
                            "wash_should_listen",
                            false
                        )
                        .apply()

                    active = false
                    pausedForChat = false
                    stopCurrentCapture()

                    publish("JARVIS detenida.")

                    stopForeground(
                        STOP_FOREGROUND_REMOVE
                    )
                    stopSelf()
                }
            }
        } catch (ex: Exception) {
            active = false
            stopCurrentCapture()

            publish(
                "No se pudo iniciar JARVIS: ${ex.message}"
            )

            stopForeground(
                STOP_FOREGROUND_REMOVE
            )
            stopSelf()
        }

        return START_STICKY
    }

    private fun selectedDevice(): AudioDeviceInfo? =
        try {
            audioManager.getDevices(
                AudioManager.GET_DEVICES_INPUTS
            ).firstOrNull {
                it.id == inputId
            }
        } catch (_: SecurityException) {
            null
        }

    private fun setBluetoothRoute(
        target: AudioDeviceInfo?
    ) {
        if (
            target == null ||
            target.type !in listOf(
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                AudioDeviceInfo.TYPE_BLE_HEADSET
            )
        ) {
            return
        }

        try {
            if (savedAudioMode == null) {
                savedAudioMode = audioManager.mode
            }

            audioManager.mode =
                AudioManager.MODE_IN_COMMUNICATION

            val device =
                audioManager.availableCommunicationDevices
                    .firstOrNull {
                        it.type == target.type &&
                        it.productName.toString() ==
                            target.productName.toString()
                    }
                    ?: audioManager
                        .availableCommunicationDevices
                        .firstOrNull {
                            it.type == target.type
                        }

            if (device != null) {
                routeSelected =
                    audioManager.setCommunicationDevice(device)

                publish(
                    if (routeSelected) {
                        "Ruta Bluetooth solicitada: ${target.productName}"
                    } else {
                        "Android rechazó la ruta Bluetooth."
                    }
                )
            }
        } catch (ex: Exception) {
            publish(
                "Error de ruta Bluetooth: ${ex.message}"
            )
        }
    }

    private fun clearBluetoothRoute() {
        if (routeSelected) {
            try {
                audioManager.clearCommunicationDevice()
            } catch (_: Exception) {
            }

            routeSelected = false
        }

        savedAudioMode?.let {
            try {
                audioManager.mode = it
            } catch (_: Exception) {
            }
        }

        savedAudioMode = null
    }

    private fun startRecognition() {
        if (
            !active ||
            pausedForChat ||
            speakingExit
        ) {
            return
        }

        stopCurrentCapture()
        monitoring = false
        partialCandidate = ""
        partialConfirmations = 0

        if (
            !SpeechRecognizer.isRecognitionAvailable(this)
        ) {
            publish(
                "Motor de voz no disponible. Reintento automático."
            )

            notifyUser(
                "Motor de voz no disponible"
            )

            handler.postDelayed(
                restart,
                30000
            )
            return
        }

        val localAvailable =
            SpeechRecognizer.isOnDeviceRecognitionAvailable(
                this
            )

        if (
            mode == MODE_LOCAL &&
            !localAvailable
        ) {
            mode = MODE_COMPATIBLE

            publish(
                "Motor local no disponible. Usando compatible."
            )
        }

        setBluetoothRoute(selectedDevice())

        val session = generation

        try {
            val engine =
                if (mode == MODE_LOCAL) {
                    SpeechRecognizer
                        .createOnDeviceSpeechRecognizer(this)
                } else {
                    SpeechRecognizer
                        .createSpeechRecognizer(this)
                }

            recognizer = engine

            engine.setRecognitionListener(
                object : RecognitionListener {

                    override fun onReadyForSpeech(
                        params: Bundle?
                    ) {
                        if (!isCurrent(session)) return

                        failures = 0

                        publish(
                            "ESCUCHANDO «Jarvis» · modo $mode"
                        )

                        notifyUser(
                            "Escuchando «Jarvis»"
                        )
                    }

                    override fun onBeginningOfSpeech() {
                        if (!isCurrent(session)) return

                        publish(
                            "Voz detectada. Procesando comando."
                        )
                    }

                    override fun onRmsChanged(
                        rmsdB: Float
                    ) {
                        if (!isCurrent(session)) return

                        meter(
                            SoundMeter.fromRecognizerRms(
                                rmsdB
                            ),
                            "Nivel del reconocimiento Android"
                        )
                    }

                    override fun onBufferReceived(
                        buffer: ByteArray?
                    ) {
                    }

                    override fun onEndOfSpeech() {
                        if (!isCurrent(session)) return

                        publish(
                            "Procesando palabra escuchada..."
                        )
                    }

                    override fun onPartialResults(
                        bundle: Bundle?
                    ) {
                        if (!isCurrent(session)) return

                        val words = bundle
                            ?.getStringArrayList(
                                SpeechRecognizer.RESULTS_RECOGNITION
                            ).orEmpty()

                        val candidate = words.firstOrNull()
                            ?.trim()
                            .orEmpty()

                        if (candidate.isBlank()) return

                        val command =
                            JarvisCommands.parse(
                                listOf(candidate)
                            )

                        if (
                            command ==
                                JarvisCommands.Command.Open
                        ) {
                            val normalized =
                                candidate.lowercase(
                                    Locale.ROOT
                                ).trim()

                            if (
                                normalized == partialCandidate
                            ) {
                                partialConfirmations++
                            } else {
                                partialCandidate = normalized
                                partialConfirmations = 1
                            }

                            if (partialConfirmations >= 2) {
                                activateJarvis(session)
                            }
                        } else {
                            partialCandidate = ""
                            partialConfirmations = 0
                        }
                    }

                    override fun onResults(
                        bundle: Bundle?
                    ) {
                        if (!isCurrent(session)) return

                        val words = bundle
                            ?.getStringArrayList(
                                SpeechRecognizer.RESULTS_RECOGNITION
                            ).orEmpty()

                        val command =
                            JarvisCommands.parse(words)

                        when (command) {
                            is JarvisCommands.Command.Volume -> {
                                adjustVolume(command)
                                failures = 0
                                scheduleRestart(900)
                            }

                            JarvisCommands.Command.Exit -> {
                                speakAndStop()
                            }

                            JarvisCommands.Command.Open -> {
                                activateJarvis(session)
                            }

                            null -> {
                                publish(
                                    "Esperando comando Jarvis."
                                )
                                failures = 0
                                scheduleRestart(900)
                            }
                        }
                    }

                    override fun onError(error: Int) {
                        if (!isCurrent(session)) return

                        failures++

                        publish(
                            "Reconocedor: error $error"
                        )

                        if (
                            mode == MODE_LOCAL &&
                            (
                                error ==
                                    SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ||
                                error ==
                                    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ||
                                failures >= 3
                            )
                        ) {
                            mode = MODE_COMPATIBLE
                            failures = 0

                            publish(
                                "Cambiando al motor compatible."
                            )
                        }

                        if (failures >= 12) {
                            mode = MODE_COMPATIBLE
                            failures = 0

                            publish(
                                "Motor de voz temporalmente bloqueado."
                            )

                            scheduleRestart(20000)
                            return
                        }

                        val delay = when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH,
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                                900L

                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                                2500L

                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                                20000L

                            else -> when {
                                failures >= 8 -> 12000L
                                failures >= 4 -> 6000L
                                else -> 1800L
                            }
                        }

                        scheduleRestart(delay)
                    }

                    override fun onEvent(
                        eventType: Int,
                        params: Bundle?
                    ) {
                    }
                }
            )

            val request = Intent(
                RecognizerIntent.ACTION_RECOGNIZE_SPEECH
            ).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )

                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE,
                    "es-PE"
                )

                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,
                    "es-PE"
                )

                putExtra(
                    RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                    true
                )

                putExtra(
                    RecognizerIntent.EXTRA_MAX_RESULTS,
                    5
                )

                putExtra(
                    RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                    1200L
                )

                putExtra(
                    RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                    900L
                )
            }

            engine.startListening(request)

            handler.postDelayed(
                watchdog,
                25000
            )
        } catch (ex: Exception) {
            publish(
                "Error de reconocimiento: ${ex.message}"
            )

            failures++

            if (mode == MODE_LOCAL) {
                mode = MODE_COMPATIBLE
            }

            if (failures < 12) {
                scheduleRestart(2500)
            } else {
                failures = 0
                mode = MODE_COMPATIBLE
                scheduleRestart(20000)
            }
        }
    }

    private fun activateJarvis(session: Int) {
        if (!isCurrent(session)) return

        publish(
            "JARVIS detectado. Abriendo ChatGPT."
        )

        failures = 0
        pausedForChat = true
        partialCandidate = ""
        partialConfirmations = 0

        stopCurrentCapture()

        handler.postDelayed(
            {
                if (
                    active &&
                    pausedForChat
                ) {
                    openChatGPT()
                }
            },
            450
        )
    }

    private fun adjustVolume(
        command: JarvisCommands.Command.Volume
    ) {
        val maximum =
            audioManager.getStreamMaxVolume(
                AudioManager.STREAM_MUSIC
            )

        val currentLevel =
            audioManager.getStreamVolume(
                AudioManager.STREAM_MUSIC
            )

        val currentPercent =
            if (maximum > 0) {
                currentLevel * 100.0 / maximum
            } else {
                0.0
            }

        val percent = when (command.kind) {
            "up" ->
                currentPercent + command.percent

            "down" ->
                currentPercent - command.percent

            "set" ->
                command.percent.toDouble()

            "mute" ->
                0.0

            else ->
                currentPercent
        }.coerceIn(0.0, 100.0)

        val level =
            round(
                percent * maximum / 100.0
            ).toInt().coerceIn(0, maximum)

        try {
            audioManager.setStreamVolume(
                AudioManager.STREAM_MUSIC,
                level,
                0
            )

            publish(
                "Volumen multimedia: " +
                    "${round(level * 100.0 / maximum.coerceAtLeast(1)).toInt()}%"
            )
        } catch (ex: Exception) {
            publish(
                "No se pudo ajustar volumen: ${ex.message}"
            )
        }
    }

    private fun speakAndStop() {
        if (speakingExit) return

        speakingExit = true
        active = false

        stopCurrentCapture()

        getSharedPreferences(
            "atena",
            MODE_PRIVATE
        ).edit()
            .putBoolean(
                "wash_should_listen",
                false
            )
            .apply()

        exitTts = TextToSpeech(
            applicationContext
        ) { result ->
            if (result == TextToSpeech.SUCCESS) {
                exitTts?.language =
                    Locale("es", "PE")

                exitTts?.speak(
                    "Okey, señor",
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    "jarvis_exit"
                )
            }
        }

        publish(
            "JARVIS apagándose."
        )

        handler.postDelayed(
            {
                exitTts?.stop()
                exitTts?.shutdown()
                exitTts = null

                stopForeground(
                    STOP_FOREGROUND_REMOVE
                )

                stopSelf()
            },
            2600
        )
    }

    private fun isCurrent(session: Int): Boolean =
        active &&
        !pausedForChat &&
        !monitoring &&
        !speakingExit &&
        session == generation

    private fun scheduleRestart(
        waitMillis: Long
    ) {
        stopCurrentCapture()
        handler.removeCallbacks(restart)
        handler.postDelayed(
            restart,
            waitMillis
        )
    }

    private fun startMicrophoneTest() {
        val target = selectedDevice()

        if (target == null) {
            publish(
                "El micrófono seleccionado no está conectado."
            )

            notifyUser(
                "Micrófono no disponible"
            )
            return
        }

        try {
            setBluetoothRoute(target)

            val sampleRate = 16000

            val bufferSize =
                AudioRecord.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )

            if (bufferSize <= 0) {
                error("Formato PCM no disponible")
            }

            val source =
                if (
                    target.type in listOf(
                        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                        AudioDeviceInfo.TYPE_BLE_HEADSET
                    )
                ) {
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION
                } else {
                    MediaRecorder.AudioSource.VOICE_RECOGNITION
                }

            val record =
                AudioRecord.Builder()
                    .setAudioSource(source)
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(
                                AudioFormat.ENCODING_PCM_16BIT
                            )
                            .setSampleRate(sampleRate)
                            .setChannelMask(
                                AudioFormat.CHANNEL_IN_MONO
                            )
                            .build()
                    )
                    .setBufferSizeInBytes(
                        max(bufferSize * 2, 6400)
                    )
                    .build()

            if (
                record.state !=
                AudioRecord.STATE_INITIALIZED
            ) {
                record.release()
                error("No se pudo preparar AudioRecord")
            }

            audioRecord = record

            if (!record.setPreferredDevice(target)) {
                error(
                    "Android rechazó el micrófono elegido"
                )
            }

            record.startRecording()

            if (
                record.recordingState !=
                AudioRecord.RECORDSTATE_RECORDING
            ) {
                error(
                    "Android no inició la captura"
                )
            }

            monitoring = true

            val session = generation
            val buffer = ByteArray(3200)
            val started = SystemClock.elapsedRealtime()

            monitorThread = Thread(
                {
                    var verified = false

                    while (
                        active &&
                        monitoring &&
                        session == generation &&
                        !Thread.currentThread().isInterrupted
                    ) {
                        val count = try {
                            record.read(
                                buffer,
                                0,
                                buffer.size
                            )
                        } catch (_: Exception) {
                            break
                        }

                        if (count <= 0) break

                        val route = record.routedDevice

                        if (
                            route == null &&
                            SystemClock.elapsedRealtime() -
                                started > 3000
                        ) {
                            handler.post {
                                if (session == generation) {
                                    publish(
                                        "No se confirmó la ruta del micrófono."
                                    )
                                }
                            }
                            break
                        }

                        if (route == null) continue

                        if (route.id != target.id) {
                            handler.post {
                                if (session == generation) {
                                    publish(
                                        "Android usa ${route.productName} " +
                                            "en lugar de ${target.productName}."
                                    )
                                }
                            }
                            break
                        }

                        if (!verified) {
                            verified = true

                            handler.post {
                                if (session == generation) {
                                    publish(
                                        "CAPTURA CONFIRMADA: " +
                                            "${route.productName}"
                                    )

                                    notifyUser(
                                        "Probando micrófono: " +
                                            "${route.productName}"
                                    )
                                }
                            }
                        }

                        meter(
                            SoundMeter.fromPcm16(
                                buffer,
                                count
                            ),
                            "Micrófono verificado: ${route.productName}"
                        )
                    }

                    handler.post {
                        if (
                            session == generation &&
                            active &&
                            monitoring
                        ) {
                            stopCurrentCapture()

                            publish(
                                "La prueba de sonido terminó."
                            )
                        }
                    }
                },
                "jarvis-meter"
            ).apply {
                isDaemon = true
                start()
            }
        } catch (ex: Exception) {
            stopCurrentCapture()

            publish(
                "Error de prueba: ${ex.message}"
            )

            notifyUser(
                "Error en prueba de micrófono"
            )
        }
    }

    private fun openChatGPT() {
        if (!active) return

        pausedForChat = true
        monitoring = false

        stopCurrentCapture()

        if (ChatOpener.intent(this) == null) {
            publish(
                "ChatGPT no está instalado o no se encuentra."
            )

            notifyUser(
                "ChatGPT no disponible"
            )
            return
        }

        val canNavigate =
            WashAccessibilityService
                .requestOpenSdAndVoice()

        if (canNavigate) {
            publish(
                "JARVIS buscando SD y Voz."
            )
        } else {
            val opened = ChatOpener.open(this)

            publish(
                if (opened) {
                    "ChatGPT abierto. Activa Accesibilidad para SD y Voz."
                } else {
                    "Android no permitió abrir ChatGPT."
                }
            )
        }

        notifyUser(
            "JARVIS → SD → Voz"
        )
    }

    private fun stopCurrentCapture() {
        generation++

        handler.removeCallbacks(restart)
        handler.removeCallbacks(watchdog)

        recognizer?.let {
            try {
                it.cancel()
            } catch (_: Exception) {
            }

            try {
                it.destroy()
            } catch (_: Exception) {
            }
        }

        recognizer = null
        monitoring = false

        audioRecord?.let {
            try {
                it.stop()
            } catch (_: Exception) {
            }

            try {
                it.release()
            } catch (_: Exception) {
            }
        }

        audioRecord = null

        monitorThread?.interrupt()
        monitorThread = null

        clearBluetoothRoute()
    }

    override fun onBind(
        intent: Intent?
    ): IBinder? = null

    override fun onDestroy() {
        active = false

        stopCurrentCapture()

        if (current.get() === this) {
            current.clear()
        }

        exitTts?.shutdown()
        exitTts = null

        super.onDestroy()
    }
}
