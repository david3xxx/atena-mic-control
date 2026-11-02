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
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.io.IOException
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Demostración independiente: AudioRecord en micrófono elegido -> descriptor PCM -> SpeechRecognizer.
 * Reconocedores externos pueden ignorar EXTRA_AUDIO_SOURCE. No cambia el micrófono de Voice Access.
 */
class VoiceCaptureService : Service() {
    companion object {
        const val ACTION_START = "pe.atena.mic.START"
        const val ACTION_STOP = "pe.atena.mic.STOP"
        const val ACTION_STATUS = "pe.atena.mic.STATUS"
        const val EXTRA_INPUT_ID = "input_id"
        const val EXTRA_MESSAGE = "message"
        private const val CHANNEL = "atena_mic_capture_v06"
        private const val NOTIFICATION_ID = 71
        private const val SAMPLE_RATE = 16000
    }

    private lateinit var audioManager: AudioManager
    private val handler = Handler(Looper.getMainLooper())
    private var recorder: AudioRecord? = null
    private var recognizer: SpeechRecognizer? = null
    private var pipeRead: ParcelFileDescriptor? = null
    private var pipeWrite: ParcelFileDescriptor? = null
    private var captureThread: Thread? = null
    private var active = false
    private var selectedId = -1
    private var generation = 0
    private var errors = 0
    private var adjustedAudioMode = false
    private var savedAudioMode = AudioManager.MODE_NORMAL

    private val nextAttempt = Runnable { if (active) startSession() }
    private val watchdog = Runnable {
        if (active) {
            publish("El reconocimiento no terminó. Reiniciando sesión de audio...")
            handler.removeCallbacks(nextAttempt)
            finishSession()
            handler.postDelayed(nextAttempt, 1500)
        }
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(AudioManager::class.java)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "ATENA Mic: captura", NotificationManager.IMPORTANCE_LOW).apply {
                setSound(null, null)
                enableVibration(false)
            }
        )
    }

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("ATENA: comprobando micrófono")
            .setContentText("Escucha por turnos con micrófono seleccionado")
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    publish("Falta permiso RECORD_AUDIO; no se abre ningún micrófono.")
                    stopSelf()
                    return START_NOT_STICKY
                }
                stopCaptureOnly()
                selectedId = intent.getIntExtra(EXTRA_INPUT_ID, -1)
                active = true
                errors = 0
                try {
                    startForeground(
                        NOTIFICATION_ID, notification(),
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                    )
                    startSession()
                } catch (ex: Exception) {
                    stopWithMessage("No se pudo iniciar micrófono: ${ex.message}")
                }
            }
            ACTION_STOP -> {
                stopWithMessage("Escucha detenida por el usuario.")
            }
        }
        return START_NOT_STICKY
    }

    private fun publish(message: String) {
        sendBroadcast(Intent(ACTION_STATUS).apply {
            setPackage(packageName)
            putExtra(EXTRA_MESSAGE, message)
        })
    }

    private fun chosenInput(): AudioDeviceInfo? = try {
        audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            .firstOrNull { it.id == selectedId }
    } catch (ex: SecurityException) {
        publish("Android denegó la lista de dispositivos: ${ex.message}")
        null
    }

    private fun configureBluetooth(target: AudioDeviceInfo) {
        if (target.type != AudioDeviceInfo.TYPE_BLUETOOTH_SCO &&
            target.type != AudioDeviceInfo.TYPE_BLE_HEADSET) return

        if (!adjustedAudioMode) {
            savedAudioMode = audioManager.mode
            adjustedAudioMode = true
        }
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        val device = audioManager.availableCommunicationDevices.firstOrNull {
            it.type == target.type && it.productName.toString() == target.productName.toString()
        } ?: audioManager.availableCommunicationDevices.firstOrNull { it.type == target.type }
        if (device != null) {
            val ok = audioManager.setCommunicationDevice(device)
            publish(if (ok) "Solicitada ruta Bluetooth de llamadas: ${target.productName}." else
                "Android rechazó ruta Bluetooth de llamadas. Se comprobará AudioRecord.")
        } else {
            publish("No aparece salida Bluetooth de comunicación. Se comprobará la entrada seleccionada.")
        }
    }

    private fun startSession() {
        if (!active) return
        handler.removeCallbacks(nextAttempt)
        handler.removeCallbacks(watchdog)
        finishSession()
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            stopWithMessage("No hay motor SpeechRecognizer disponible en Android.")
            return
        }
        val target = chosenInput()
        if (target == null) {
            stopWithMessage("El micrófono elegido ya no está conectado. Vuelve a seleccionarlo.")
            return
        }
        try {
            configureBluetooth(target)
            val minSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            if (minSize <= 0) throw IllegalStateException("Formato PCM de 16 kHz no disponible")
            val source = if (target.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                target.type == AudioDeviceInfo.TYPE_BLE_HEADSET)
                MediaRecorder.AudioSource.VOICE_COMMUNICATION else MediaRecorder.AudioSource.VOICE_RECOGNITION
            val audio = AudioRecord.Builder()
                .setAudioSource(source)
                .setAudioFormat(AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build())
                .setBufferSizeInBytes(max(minSize * 2, 6400))
                .build()
            if (audio.state != AudioRecord.STATE_INITIALIZED) {
                audio.release()
                throw IllegalStateException("AudioRecord no se pudo inicializar")
            }
            recorder = audio
            if (!audio.setPreferredDevice(target)) {
                throw IllegalStateException("Android rechazó el micrófono ${target.productName}")
            }
            audio.startRecording()
            if (audio.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                throw IllegalStateException("La captura del micrófono no se inició")
            }

            val descriptors = ParcelFileDescriptor.createPipe()
            pipeRead = descriptors[0]
            pipeWrite = descriptors[1]
            val session = ++generation
            val recognition = SpeechRecognizer.createSpeechRecognizer(this)
            recognizer = recognition
            recognition.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
                override fun onError(error: Int) {
                    if (session != generation || !active) return
                    errors++
                    if (errors >= 5) {
                        stopWithMessage("Reconocedor no acepta el audio elegido o está ocupado (código $error). Revisa Bluetooth y permisos.")
                    } else {
                        publish("Reconocimiento: código $error. Reintentando (${errors}/5)...")
                        scheduleNext(2200)
                    }
                }
                override fun onResults(results: Bundle?) {
                    if (session != generation || !active) return
                    errors = 0
                    val heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull().orEmpty()
                    handleCommand(heard)
                }
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-PE")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, pipeRead)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, SAMPLE_RATE)
            }
            recognition.startListening(intent)
            startFeedingAudio(audio, target, session)
            handler.postDelayed(watchdog, 15500)
        } catch (ex: Exception) {
            stopWithMessage("No se logró utilizar ese micrófono: ${ex.message}")
        }
    }

    private fun startFeedingAudio(audio: AudioRecord, target: AudioDeviceInfo, session: Int) {
        val writePipe = pipeWrite ?: return
        captureThread = Thread({
            var receivedVoice = false
            var lastVoiceTime = 0L
            val started = SystemClock.elapsedRealtime()
            var routeVerified = false
            val buffer = ByteArray(3200)
            try {
                ParcelFileDescriptor.AutoCloseOutputStream(writePipe).use { output ->
                    while (active && generation == session && !Thread.currentThread().isInterrupted) {
                        val count = audio.read(buffer, 0, buffer.size)
                        if (count <= 0) break
                        if (!routeVerified) {
                            val route = audio.routedDevice
                            if (route != null) {
                                if (route.id != target.id) {
                                    handler.post {
                                        if (active && generation == session) {
                                            stopWithMessage("RUTA INCORRECTA: pediste '${target.productName}' (ID ${target.id}) y Android usa '${route.productName}' (ID ${route.id}). Se detuvo para evitar usar otro micrófono.")
                                        }
                                    }
                                    break
                                }
                                routeVerified = true
                                handler.post {
                                    if (active && generation == session) {
                                        publish("RUTA REAL CONFIRMADA: ${route.productName} (ID ${route.id}). Escuchando órdenes... El motor de reconocimiento podría ignorar el audio inyectado.")
                                    }
                                }
                            } else if (SystemClock.elapsedRealtime() - started > 1700) {
                                handler.post {
                                    if (active && generation == session) {
                                        stopWithMessage("Android no confirmó la ruta física del micrófono; captura detenida.")
                                    }
                                }
                                break
                            }
                        }
                        if (!routeVerified) continue
                        output.write(buffer, 0, count)
                        val now = SystemClock.elapsedRealtime()
                        val level = rms(buffer, count)
                        if (level > 550) {
                            receivedVoice = true
                            lastVoiceTime = now
                        }
                        if (receivedVoice && now - lastVoiceTime > 1250) break
                        if (now - started > 10500) break
                    }
                }
            } catch (_: IOException) {
                // El reconocedor o el usuario puede cerrar la tubería mientras se captura.
            } catch (_: IllegalStateException) {
                // La grabación puede detenerse al cambiar de dispositivo.
            }
        }, "atena-audio-route").apply { isDaemon = true; start() }
    }

    private fun rms(bytes: ByteArray, length: Int): Double {
        if (length < 2) return 0.0
        var sum = 0.0
        var index = 0
        while (index + 1 < length) {
            val sample = ((bytes[index].toInt() and 0xff) or (bytes[index + 1].toInt() shl 8)).toShort().toInt()
            sum += sample.toDouble() * sample
            index += 2
        }
        return sqrt(sum / (length / 2))
    }

    private fun handleCommand(heard: String) {
        val number = GridSpeech.number(heard)
        if (number != null && AtenaAccessibilityService.isGridVisible()) {
            publish(AtenaAccessibilityService.selectCell(number))
            scheduleNext(650)
            return
        }
        val action = CommandRouter.action(heard)
        publish("Reconocido: '$heard'. Acción: ${action.name}. (No garantiza la fuente externa.)")
        when (action) {
            CommandRouter.Action.GRID_SHOW -> publish(AtenaAccessibilityService.showGrid())
            CommandRouter.Action.GRID_HIDE -> publish(AtenaAccessibilityService.hideGrid())
            CommandRouter.Action.GRID_RESET -> publish(AtenaAccessibilityService.restartGrid())
            CommandRouter.Action.GRID_BACK -> publish(AtenaAccessibilityService.backGrid())
            CommandRouter.Action.STOP -> {
                stopWithMessage("Escucha detenida por comando de voz.")
                return
            }
            CommandRouter.Action.OPEN_CHATGPT -> {
                try {
                    val target = packageManager.getLaunchIntentForPackage("com.openai.chatgpt")
                        ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://chatgpt.com/"))
                    startActivity(target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (ex: Exception) { publish("No pude abrir ChatGPT: ${ex.message}") }
            }
            CommandRouter.Action.NONE -> Unit
            else -> {
                if (!AtenaAccessibilityService.perform(action)) {
                    publish("Orden '$heard' reconocida, pero activa 'ATENA control por voz' en Accesibilidad para ejecutar esa acción.")
                }
            }
        }
        scheduleNext(1400)
    }

    private fun scheduleNext(delay: Long) {
        handler.removeCallbacks(watchdog)
        finishSession()
        if (active) {
            handler.removeCallbacks(nextAttempt)
            handler.postDelayed(nextAttempt, delay)
        }
    }

    private fun finishSession() {
        generation++ // invalida resultados de sesiones anteriores
        captureThread?.interrupt()
        captureThread = null
        try { recorder?.stop() } catch (_: Exception) {}
        try { pipeWrite?.close() } catch (_: Exception) {}
        try { pipeRead?.close() } catch (_: Exception) {}
        try { recorder?.release() } catch (_: Exception) {}
        recorder = null
        pipeRead = null
        pipeWrite = null
        try { recognizer?.cancel() } catch (_: Exception) {}
        try { recognizer?.destroy() } catch (_: Exception) {}
        recognizer = null
    }

    private fun stopCaptureOnly() {
        active = false
        handler.removeCallbacksAndMessages(null)
        finishSession()
        try { audioManager.clearCommunicationDevice() } catch (_: Exception) {}
        if (adjustedAudioMode) {
            try { audioManager.mode = savedAudioMode } catch (_: Exception) {}
            adjustedAudioMode = false
        }
    }

    private fun stopWithMessage(message: String) {
        publish(message)
        stopCaptureOnly()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopCaptureOnly()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
