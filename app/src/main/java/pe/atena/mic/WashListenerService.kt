package pe.atena.mic

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.NotificationManager as AndroidNotificationManager
import android.media.AudioAttributes
import android.speech.tts.UtteranceProgressListener
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.speech.tts.TextToSpeech
import java.util.Locale
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlin.math.max
import java.lang.ref.WeakReference

/** Servicio en primer plano: reconoce «wash» O comprueba un micrófono, nunca ambos a la vez. */
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
        private var current = WeakReference<WashListenerService>(null)
        fun openFromMenuIfRunning(): Boolean {
            val service = current.get() ?: return false
            if (!service.active) return false
            service.handler.post { service.openChatGPT() }
            return true
        }
        /** Indica si cedimos temporalmente el micrófono para ChatGPT. */
        fun isWaitingForChat(): Boolean = current.get()?.let {
            it.active && it.pausedForChat && !it.monitoring
        } == true

        fun rearmAfterChatLeft() {
            current.get()?.let { service ->
                service.handler.post {
                    if (service.active && service.pausedForChat &&
                        service.getSharedPreferences("atena", android.content.Context.MODE_PRIVATE)
                            .getBoolean("auto_rearm", true)) {
                        service.publish("REACTIVANDO JARVIS: recuperando micrófono tras ChatGPT.")
                        service.stopCurrentCapture()
                        service.pausedForChat = false
                        service.failures = 0
                        service.mode = service.lastMode
                        service.scheduleRestart(2000)
                        service.notifyUser("JARVIS escuchando")
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
    @Volatile private var active = false
    @Volatile private var monitoring = false
    private var pausedForChat = false
    private var generation = 0
    private var failures = 0
    private var lastSpeechAt = 0L
    private var speakingExit = false
    private var mode = MODE_LOCAL
    private var inputId = -1
    private var lastMode = MODE_LOCAL
    private var savedAudioMode: Int? = null
    private var routeSelected = false
    private var lastLevelSent = 0L

    private val restart = Runnable {
        if (active && !pausedForChat && !monitoring) startRecognition()
    }
    private val watchdog = Runnable {
        if (active && !pausedForChat && !monitoring) {
            publish("El reconocedor no respondió; reiniciando escucha.")
            stopCurrentCapture()
            handler.postDelayed(restart, 3000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(AudioManager::class.java)
        current = WeakReference(this)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "JARVIS · Voz", NotificationManager.IMPORTANCE_DEFAULT).apply {
                setSound(null, null)
                enableVibration(false)
            }
        )
    }

    private fun pendingService(action: String, code: Int): PendingIntent = PendingIntent.getForegroundService(
        this, code,
        Intent(this, WashListenerService::class.java).apply { this.action = action },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun notification(message: String): Notification {
        val openMain = PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("JARVIS")
            .setContentText(message.take(100))
            .setContentIntent(openMain)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(null, "REANUDAR JARVIS", pendingService(ACTION_REARM, 2)).build())
            .addAction(Notification.Action.Builder(null, "DETENER", pendingService(ACTION_STOP, 4)).build())
        ChatOpener.intent(this)?.let { launch ->
            val openChat = PendingIntent.getActivity(this, 3, launch,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            builder.addAction(Notification.Action.Builder(null, "ABRIR CHATGPT", openChat).build())
        }
        return builder.build()
    }

    private fun notifyUser(message: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(message))
    }

    private fun publish(msg: String) {
        getSharedPreferences("atena", MODE_PRIVATE).edit().putString("last_status", "• $msg").apply()
        sendBroadcast(Intent(ACTION_STATUS).apply {
            setPackage(packageName)
            putExtra(EXTRA_MESSAGE, msg)
        })
    }

    private fun meter(level: Int, source: String) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastLevelSent < 125) return
        lastLevelSent = now
        sendBroadcast(Intent(ACTION_METER).apply {
            setPackage(packageName)
            putExtra(EXTRA_LEVEL, level.coerceIn(0, 100))
            putExtra(EXTRA_SOURCE, source)
        })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            when (intent?.action) {
                null -> {
                    // Android reinició el proceso: intentar recuperar escucha SOLO si el usuario
                    // la había dejado activa. Android puede denegar el micrófono desde fondo.
                    val saved = getSharedPreferences("atena", MODE_PRIVATE)
                    if (!saved.getBoolean("wash_should_listen", false)) {
                        stopSelf(); return START_NOT_STICKY
                    }
                    active = true
                    monitoring = false
                    pausedForChat = false
                    failures = 0
                    mode = saved.getString("wash_last_mode", MODE_COMPATIBLE) ?: MODE_COMPATIBLE
                    lastMode = mode
                    inputId = saved.getInt("wash_input_id", -1)
                    publish("Android reinició ATENA; intentando recuperar escucha WASH.")
                    startForeground(NOTIFICATION_ID, notification("Recuperando escucha WASH"),
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
                    startRecognition()
                }
                ACTION_START -> {
                    if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                        publish("Falta permiso de micrófono: abre ATENA y concédelo.")
                        stopSelf(); return START_NOT_STICKY
                    }
                    stopCurrentCapture()
                    pausedForChat = false
                    monitoring = false
                    active = true
                    enableFocusMode()
                    failures = 0
                    inputId = intent.getIntExtra(EXTRA_INPUT_ID, -1)
                    mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_LOCAL
                    if (mode != MODE_MONITOR) lastMode = mode
                    getSharedPreferences("atena", MODE_PRIVATE).edit()
                        .putBoolean("wash_should_listen", mode != MODE_MONITOR)
                        .putString("wash_last_mode", lastMode)
                        .putInt("wash_input_id", inputId).apply()
                    startForeground(NOTIFICATION_ID, notification("Inicializando $mode"),
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
                    if (mode == MODE_MONITOR) startMicrophoneTest() else startRecognition()
                }
                ACTION_REARM -> {
                    if (!active) {
                        publish("Abre ATENA para iniciar el servicio de micrófono.")
                        stopSelf(); return START_NOT_STICKY
                    }
                    stopCurrentCapture()
                    pausedForChat = false
                    monitoring = false
                    failures = 0
                    mode = lastMode
                    startRecognition()
                }
                ACTION_OPEN -> openChatGPT()
                ACTION_STOP -> {
                    getSharedPreferences("atena", MODE_PRIVATE).edit()
                        .putBoolean("wash_should_listen", false).apply()
                    active = false
                    pausedForChat = false
                    stopCurrentCapture()
                    restoreFocusMode()
                    publish("ATENA detenida por el usuario.")
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        } catch (ex: Exception) {
            active = false
            stopCurrentCapture()
            restoreFocusMode()
            publish("No se pudo iniciar ATENA: ${ex.message}")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        // Servicio persistente solicitado por el usuario. Android puede detenerlo de todos modos.
        return START_STICKY
    }

    private fun selectedDevice(): AudioDeviceInfo? = try {
        audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS).firstOrNull { it.id == inputId }
    } catch (_: SecurityException) { null }

    private fun setBluetoothRoute(target: AudioDeviceInfo?) {
        if (target == null || target.type !in listOf(AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                AudioDeviceInfo.TYPE_BLE_HEADSET)) return
        try {
            if (savedAudioMode == null) savedAudioMode = audioManager.mode
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            val device = audioManager.availableCommunicationDevices.firstOrNull {
                it.type == target.type && it.productName.toString() == target.productName.toString()
            } ?: audioManager.availableCommunicationDevices.firstOrNull { it.type == target.type }
            if (device != null) {
                routeSelected = audioManager.setCommunicationDevice(device)
                publish(if (routeSelected) "Se solicitó ruta Bluetooth: ${target.productName}." else
                    "Android rechazó la ruta Bluetooth elegida.")
            }
        } catch (ex: Exception) {
            publish("La selección Bluetooth no pudo aplicarse: ${ex.message}")
        }
    }

    private fun clearBluetoothRoute() {
        if (routeSelected) {
            try { audioManager.clearCommunicationDevice() } catch (_: Exception) { }
            routeSelected = false
        }
        savedAudioMode?.let { try { audioManager.mode = it } catch (_: Exception) { } }
        savedAudioMode = null
    }

    private fun startRecognition() {
        if (!active || pausedForChat || speakingExit) return
        stopCurrentCapture()
        monitoring = false
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            publish("Motor de voz no disponible. Nuevo intento automático en 30 s.")
            notifyUser("Motor de voz no disponible · reintento en 30 s")
            handler.postDelayed(restart, 30000)
            return
        }
        val localAvailable = SpeechRecognizer.isOnDeviceRecognitionAvailable(this)
        if (mode == MODE_LOCAL && !localAvailable) {
            mode = MODE_COMPATIBLE
            publish("No hay motor local. Usando modo compatible; puede requerir Internet.")
        }
        setBluetoothRoute(selectedDevice())
        val session = generation
        try {
            val engine = if (mode == MODE_LOCAL)
                SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
                else SpeechRecognizer.createSpeechRecognizer(this)
            recognizer = engine
            engine.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    if (!isCurrent(session)) return
                    publish("ESCUCHANDO «Jarvis» · modo $mode (Android elige micrófono)")
                    notifyUser("Escuchando «Jarvis» · modo $mode")
                }
                override fun onBeginningOfSpeech() {
                    if (isCurrent(session)) publish("El reconocedor detectó voz.")
                }
                override fun onRmsChanged(rmsdB: Float) {
                    if (isCurrent(session)) meter(SoundMeter.fromRecognizerRms(rmsdB),
                        "Nivel aproximado del reconocedor de Android")
                }
                override fun onBufferReceived(buffer: ByteArray?) { }
                override fun onEndOfSpeech() {
                    if (isCurrent(session)) publish("Procesando palabra escuchada…")
                }
                override fun onPartialResults(bundle: Bundle?) {
                    if (!isCurrent(session)) return
                    // Los parciales pueden equivocarse: mostramos el texto, pero solo activamos con resultado final.
                    val words = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                    words.firstOrNull()?.let { publish("Parcial: $it") }
                }
                override fun onResults(bundle: Bundle?) {
                    if (!isCurrent(session)) return
                    val words = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                    val aliases = WashMatcher.parseAliases(
                        getSharedPreferences("atena", MODE_PRIVATE).getString("wash_aliases", "").orEmpty()
                    )
                    val command = JarvisCommands.parse(words)
                    when (command) {
                        is JarvisCommands.Command.Volume -> {
                            val changed = adjustVolume(command)
                            failures = 0
                            if (changed) speakConfirmation()
                            scheduleRestart(if (changed) 2700 else 1800)
                        }
                        JarvisCommands.Command.Exit -> {
                            speakAndStop()
                        }
                        JarvisCommands.Command.Open -> {
                            publish("JARVIS detectado: abriendo ChatGPT")
                            pausedForChat = true
                            stopCurrentCapture()
                            handler.postDelayed({ if (active && pausedForChat) openChatGPT() }, 650)
                        }
                        null -> {
                            publish("Esperando Jarvis")
                            failures = 0
                            scheduleRestart(1800)
                        }
                    }
                }
                override fun onError(error: Int) {
                    if (!isCurrent(session)) return
                    failures++
                    publish("Reconocedor: error $error (intento $failures).")
                    if (mode == MODE_LOCAL && (error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ||
                                error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED || failures >= 3)) {
                        mode = MODE_COMPATIBLE
                        failures = 0
                        publish("Cambiando al modo compatible de Android.")
                    }
                    if (failures >= 12) {
                        // No dejar el servicio muerto: esperar antes de reintentar con el
                        // motor compatible. Ciclo con pausa para no agotar batería.
                        mode = MODE_COMPATIBLE
                        failures = 0
                        publish("Reconocimiento temporalmente bloqueado; nuevo intento automático en 20 s.")
                        notifyUser("ATENA esperando motor de voz · reintento automático")
                        scheduleRestart(20000)
                    } else scheduleRestart(when { failures >= 8 -> 12000L; failures >= 4 -> 6000L; else -> 3000L })
                }
                override fun onEvent(eventType: Int, params: Bundle?) { }
            })
            val request = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-PE")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            }
            engine.startListening(request)
            handler.postDelayed(watchdog, 25000)
        } catch (ex: Exception) {
            publish("Error iniciando reconocimiento: ${ex.message}")
            failures++
            if (mode == MODE_LOCAL) mode = MODE_COMPATIBLE
            if (failures < 12) scheduleRestart(3000)
            else {
                failures = 0
                mode = MODE_COMPATIBLE
                scheduleRestart(20000)
            }
        }
    }

    /** Samsung Bluetooth call output: this stream is verified on-device, not assumed universal. */
    private fun adjustVolume(command: JarvisCommands.Command.Volume): Boolean {
        val stream = AudioManager.STREAM_VOICE_CALL
        return try {
            val max = audioManager.getStreamMaxVolume(stream).coerceAtLeast(1)
            val current = audioManager.getStreamVolume(stream)
            val percent = when (command.kind) {
                "up" -> current * 100.0 / max + command.percent
                "down" -> current * 100.0 / max - command.percent
                "set" -> command.percent.toDouble()
                "mute" -> 0.0
                else -> current * 100.0 / max
            }.coerceIn(0.0, 100.0)
            val level = kotlin.math.round(percent * max / 100.0).toInt().coerceIn(0, max)
            audioManager.setStreamVolume(stream, level, 0)
            publish("Voz llamada/Bluetooth: ${kotlin.math.round(level * 100.0 / max).toInt()} %")
            true
        } catch (e: Exception) {
            publish("No se pudo ajustar voz Bluetooth: ${e.message}"); false
        }
    }

    private fun speakConfirmation() {
        confirmationTts?.stop(); confirmationTts?.shutdown(); confirmationTts = null
        confirmationTts = TextToSpeech(applicationContext) { result ->
            if (result == TextToSpeech.SUCCESS && active) {
                confirmationTts?.language = Locale("es", "PE")
                confirmationTts?.setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                confirmationTts?.speak("OK", TextToSpeech.QUEUE_FLUSH, null, "volume_ok")
            } else if (result != TextToSpeech.SUCCESS) publish("No hay motor de voz TTS disponible")
        }
    }

    /** Stores previous interruption filter. DND silences, does not reject calls. */
    private fun enableFocusMode() {
        val nm = getSystemService(AndroidNotificationManager::class.java)
        val prefs = getSharedPreferences("atena", MODE_PRIVATE)
        if (!nm.isNotificationPolicyAccessGranted) {
            publish("Autoriza acceso a No molestar desde Ajustes"); return
        }
        try {
            if (!prefs.getBoolean("dnd_owned", false)) {
                prefs.edit().putInt("dnd_previous", nm.currentInterruptionFilter)
                    .putBoolean("dnd_owned", true).apply()
            }
            nm.setInterruptionFilter(AndroidNotificationManager.INTERRUPTION_FILTER_NONE)
        } catch (e: Exception) { publish("No molestar no disponible: ${e.message}") }
    }

    private fun restoreFocusMode() {
        val prefs = getSharedPreferences("atena", MODE_PRIVATE)
        if (!prefs.getBoolean("dnd_owned", false)) return
        val nm = getSystemService(AndroidNotificationManager::class.java)
        if (!nm.isNotificationPolicyAccessGranted) return
        try {
            nm.setInterruptionFilter(prefs.getInt("dnd_previous", AndroidNotificationManager.INTERRUPTION_FILTER_ALL))
            prefs.edit().putBoolean("dnd_owned", false).apply()
        } catch (e: Exception) { publish("Restaura No molestar manualmente: ${e.message}") }
    }

    private fun speakAndStop() {
        if (speakingExit) return
        speakingExit = true
        restoreFocusMode()
        active = false
        stopCurrentCapture()
        getSharedPreferences("atena", MODE_PRIVATE).edit().putBoolean("wash_should_listen", false).apply()
        val tts = TextToSpeech(applicationContext) { result ->
            if (result == TextToSpeech.SUCCESS) {
                // Se libera el TTS tras un plazo suficiente para pronunciar la despedida.
                exitTts?.language = Locale("es", "PE")
                exitTts?.speak("Okey, señor", TextToSpeech.QUEUE_FLUSH, null, "jarvis_exit")
            }
        }
        exitTts = tts
        publish("JARVIS apagándose")
        handler.postDelayed({
            exitTts?.stop(); exitTts?.shutdown(); exitTts = null
            stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
        }, 2600)
    }
    private var exitTts: TextToSpeech? = null
    private var confirmationTts: TextToSpeech? = null
    private var restoring = false

  override fun onDestroy() {
    active = false
    stopCurrentCapture()
    if (current.get() === this) current.clear()
    confirmationTts?.shutdown()
    exitTts?.shutdown()
    super.onDestroy()
}

    private fun isCurrent(session: Int) = active && !pausedForChat && !monitoring && session == generation

    private fun scheduleRestart(waitMillis: Long) {
        stopCurrentCapture()
        handler.postDelayed(restart, waitMillis)
    }

    private fun startMicrophoneTest() {
        val target = selectedDevice()
        if (target == null) {
            publish("El micrófono seleccionado ya no está conectado.")
            notifyUser("Micrófono seleccionado no disponible")
            return
        }
        try {
            setBluetoothRoute(target)
            val sampleRate = 16000
            val bufferSize = AudioRecord.getMinBufferSize(sampleRate,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            if (bufferSize <= 0) error("Formato PCM no disponible")
            val source = if (target.type in listOf(AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                    AudioDeviceInfo.TYPE_BLE_HEADSET)) MediaRecorder.AudioSource.VOICE_COMMUNICATION
                else MediaRecorder.AudioSource.VOICE_RECOGNITION
            val record = AudioRecord.Builder()
                .setAudioSource(source)
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build())
                .setBufferSizeInBytes(max(bufferSize * 2, 6400))
                .build()
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                error("No se pudo preparar AudioRecord")
            }
            audioRecord = record
            if (!record.setPreferredDevice(target)) error("Android rechazó el micrófono elegido")
            record.startRecording()
            if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING)
                error("Android no inició la captura")
            monitoring = true
            val session = generation
            val buffer = ByteArray(3200)
            val started = SystemClock.elapsedRealtime()
            monitorThread = Thread({
                var verified = false
                while (active && monitoring && session == generation && !Thread.currentThread().isInterrupted) {
                    val count = try { record.read(buffer, 0, buffer.size) } catch (_: Exception) { break }
                    if (count <= 0) break
                    val route = record.routedDevice
                    if (route == null && SystemClock.elapsedRealtime() - started > 3000) {
                        handler.post { if (session == generation) publish("No se pudo confirmar la ruta del micrófono.") }
                        break
                    }
                    if (route == null) continue
                    if (route.id != target.id) {
                        handler.post { if (session == generation)
                            publish("RUTA INCORRECTA: Android usa ${route.productName} en lugar de ${target.productName}.") }
                        break
                    }
                    if (!verified) {
                        verified = true
                        handler.post { if (session == generation) {
                            publish("CAPTURA CONFIRMADA: ${route.productName}.")
                            notifyUser("Probando micrófono: ${route.productName}")
                        } }
                    }
                    meter(SoundMeter.fromPcm16(buffer, count), "Micrófono verificado: ${route.productName}")
                }
                handler.post {
                    if (session == generation && active && monitoring) {
                        stopCurrentCapture()
                        publish("La prueba de sonido terminó; comprueba el diagnóstico.")
                    }
                }
            }, "atena-v15-meter").apply { isDaemon = true; start() }
        } catch (ex: Exception) {
            stopCurrentCapture()
            publish("No se pudo comprobar el micrófono: ${ex.message}")
            notifyUser("Error en prueba de micrófono")
        }
    }

    private fun openChatGPT() {
        if (!active) return
        pausedForChat = true
        monitoring = false
        stopCurrentCapture()  // ATENA libera el micrófono para que ChatGPT pueda hablar.
        if (ChatOpener.intent(this) == null) {
            publish("ChatGPT no está instalado o no se puede localizar.")
            notifyUser("ChatGPT no disponible")
            return
        }
        val canNavigate = WashAccessibilityService.requestOpenSdAndVoice()
        if (canNavigate) {
            publish("JARVIS: buscando SD y Voz. Después ATENA volverá a escuchar cuando termine.")
        } else {
            val opened = ChatOpener.open(this)
            publish(if (opened)
                "ChatGPT abierto, pero falta activar Accesibilidad de ATENA para encontrar SD y pulsar Voz."
                else "Android impidió abrir ChatGPT. Usa la notificación como alternativa.")
        }
        notifyUser("JARVIS → SD → Voz · esperando regreso al chat")
    }

    private fun stopCurrentCapture() {
        generation++
        handler.removeCallbacks(restart)
        handler.removeCallbacks(watchdog)
        recognizer?.let {
            try { it.cancel() } catch (_: Exception) { }
            try { it.destroy() } catch (_: Exception) { }
        }
        recognizer = null
        monitoring = false
        audioRecord?.let {
            try { it.stop() } catch (_: Exception) { }
            try { it.release() } catch (_: Exception) { }
        }
        audioRecord = null
        monitorThread?.interrupt()
        monitorThread = null
        clearBluetoothRoute()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        active = false
        stopCurrentCapture()
        if (current.get() === this) current.clear()
        exitTts?.shutdown()
        super.onDestroy()
    }
}
