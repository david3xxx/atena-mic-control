
package pe.atena.mic

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.io.File
import java.lang.ref.WeakReference
import java.util.Locale

class WashAccessibilityService : AccessibilityService() {

    companion object {
        private const val CHATGPT = "com.openai.chatgpt"
        private var current =
            WeakReference<WashAccessibilityService>(null)

        fun requestOpenSdAndVoice(): Boolean {
            val service = current.get() ?: return false
            service.handler.post { service.beginNavigation() }
            return true
        }
    }

    private enum class Step {
        FIND_SD, START_VOICE, WAIT_THREE
    }

    private val handler = Handler(Looper.getMainLooper())
    private val audioManager by lazy {
        getSystemService(AudioManager::class.java)
    }

    private var navigating = false
    private var step = Step.FIND_SD
    private var started = 0L
    private var navigationId = 0
    private var sidebarAttempted = false
    private var sidebarOpenedAt = 0L
    private var lastScanAt = 0L
    private var lastVoiceObservationAt = 0L
    private var chatWasOnScreen = false
    private var sawActiveVoice = false
    private var voiceButtonPressedAt = 0L
    private var chatSeenDuringPause = false
    private var screenAbsentChecks = 0
    private var readyToSpeakChecks = 0

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var ttsFailed = false
    private var saidOne = false
    private var saidTwo = false
    private var voiceAttemptInProgress = false
    private var threeUtteranceId: String? = null

    private var player: MediaPlayer? = null
    private val speechQueue =
        ArrayDeque<Pair<String, File>>()

    private val pauseMonitor = object : Runnable {
        override fun run() {
            if (!WashListenerService.isWaitingForChat()) return

            if (
                getSharedPreferences("atena", MODE_PRIVATE)
                    .getBoolean("auto_rearm", true)
            ) {
                inspectPausedSession()
            }

            if (WashListenerService.isWaitingForChat()) {
                handler.postDelayed(this, 2500)
            }
        }
    }

    private val scan = Runnable { scanWindow() }

    private val rearm = Runnable {
        if (
            getSharedPreferences("atena", MODE_PRIVATE)
                .getBoolean("auto_rearm", true)
        ) {
            WashListenerService.rearmAfterChatLeft()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        current = WeakReference(this)

        initializeSpeech()
        report("Accesibilidad de JARVIS preparada.")

        if (WashListenerService.isWaitingForChat()) {
            handler.removeCallbacks(pauseMonitor)
            handler.postDelayed(pauseMonitor, 1500)
        }
    }

    private fun initializeSpeech() {
        if (tts != null) return

        ttsFailed = false
        ttsReady = false

        tts = TextToSpeech(applicationContext) { result ->
            handler.post {
                val engine = tts ?: return@post

                if (result != TextToSpeech.SUCCESS) {
                    ttsFailed = true
                    report("No se pudo iniciar la voz de JARVIS.")
                    return@post
                }

                var language =
                    engine.setLanguage(Locale("es", "PE"))

                if (
                    language == TextToSpeech.LANG_MISSING_DATA ||
                    language == TextToSpeech.LANG_NOT_SUPPORTED
                ) {
                    language =
                        engine.setLanguage(Locale("es"))
                }

                if (
                    language == TextToSpeech.LANG_MISSING_DATA ||
                    language == TextToSpeech.LANG_NOT_SUPPORTED
                ) {
                    ttsFailed = true
                    report("No hay voz española instalada.")
                    return@post
                }

                engine.setSpeechRate(0.85f)

                engine.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(
                            AudioAttributes.CONTENT_TYPE_SPEECH
                        )
                        .build()
                )

                engine.setOnUtteranceProgressListener(
                    object : UtteranceProgressListener() {
                        override fun onStart(
                            utteranceId: String?
                        ) {}

                        override fun onDone(
                            utteranceId: String?
                        ) {
                            if (utteranceId != null) {
                                handler.post {
                                    onAudioPrepared(utteranceId)
                                }
                            }
                        }

                        @Deprecated("Legacy callback")
                        override fun onError(
                            utteranceId: String?
                        ) {
                            handler.post {
                                if (
                                    navigating &&
                                    utteranceId?.startsWith(
                                        "jarvis_${navigationId}_"
                                    ) == true
                                ) {
                                    finish(
                                        "Error TTS. No se pulsó Voz."
                                    )
                                }
                            }
                        }
                    }
                )

                ttsReady = true
                if (navigating) schedule(80)
            }
        }
    }

    private fun speak(number: Int, id: String): Boolean {
        if (!ttsReady) return false

        val word = when (number) {
            1 -> "Uno"
            2 -> "Dos"
            else -> "Tres"
        }

        val file = File(cacheDir, "$id.wav")

        return tts?.synthesizeToFile(
            word,
            Bundle(),
            file,
            id
        ) == TextToSpeech.SUCCESS
    }

    private fun onAudioPrepared(id: String) {
        if (
            !navigating ||
            !id.startsWith("jarvis_${navigationId}_")
        ) return

        val file = File(cacheDir, "$id.wav")

        if (!file.exists() || file.length() == 0L) {
            finish("No se generó el aviso de voz.")
            return
        }

        speechQueue.addLast(id to file)
        playNextSpeech()
    }

    private fun bluetoothOutput(): AudioDeviceInfo? {
        return try {
            val types = setOf(
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                AudioDeviceInfo.TYPE_BLE_HEADSET
            )

            audioManager.getDevices(
                AudioManager.GET_DEVICES_OUTPUTS
            ).firstOrNull { it.type in types }
        } catch (_: Exception) {
            null
        }
    }

    private fun playNextSpeech() {
        if (
            player != null ||
            speechQueue.isEmpty() ||
            !navigating
        ) return

        val (id, file) = speechQueue.removeFirst()
        val output = bluetoothOutput()
        val mp = MediaPlayer()
        player = mp

        try {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(
                        AudioAttributes.CONTENT_TYPE_SPEECH
                    )
                    .build()
            )

            mp.setDataSource(file.absolutePath)

            if (
                output != null &&
                !mp.setPreferredDevice(output)
            ) {
                mp.release()
                player = null
                finish(
                    "Android rechazó Bluetooth. " +
                        "No se pulsó Voz."
                )
                return
            }

            mp.setOnErrorListener { _, _, _ ->
                releasePlayer()
                finish(
                    "No se pudo reproducir el aviso."
                )
                true
            }

            mp.setOnCompletionListener {
                val routed = mp.routedDevice

                val validRoute =
                    output == null ||
                    routed?.id == output.id

                releasePlayer()

                try {
                    file.delete()
                } catch (_: Exception) {}

                if (!validRoute) {
                    finish(
                        "No se confirmó la salida Bluetooth. " +
                            "Voz no se pulsó."
                    )
                } else {
                    if (id == threeUtteranceId) {
                        completeThree(id)
                    }
                    playNextSpeech()
                }
            }

            mp.setOnPreparedListener { it.start() }
            mp.prepareAsync()

        } catch (e: Exception) {
            releasePlayer()
            finish(
                "Error reproduciendo aviso: ${e.message}"
            )
        }
    }

    private fun releasePlayer() {
        val mp = player
        player = null

        try {
            mp?.release()
        } catch (_: Exception) {}
    }

    private fun completeThree(id: String?) {
        if (
            !navigating ||
            step != Step.WAIT_THREE ||
            id != threeUtteranceId
        ) return

        threeUtteranceId = null

        val root = chatRoot()

        if (root == null) {
            finish(
                "Terminó Tres, pero ChatGPT no está visible."
            )
            return
        }

        val voice = findNode(root) {
            SdUiRules.isStartVoiceButton(it)
        }

        if (voice == null) {
            finish(
                "Terminó Tres, pero no aparece Voz."
            )
            return
        }

        if (!clickNodeOrParent(voice)) {
            finish(
                "No se pudo pulsar Voz."
            )
            return
        }

        voiceButtonPressedAt =
            SystemClock.elapsedRealtime()

        chatSeenDuringPause = true

        finish(
            "JARVIS terminó Tres y pulsó Voz.",
            keepMicReleased = true
        )
    }

    private fun report(message: String) {
        getSharedPreferences("atena", MODE_PRIVATE)
            .edit()
            .putString(
                "last_status",
                "• $message"
            )
            .apply()

        sendBroadcast(
            Intent(WashListenerService.ACTION_STATUS).apply {
                setPackage(packageName)
                putExtra(
                    WashListenerService.EXTRA_MESSAGE,
                    message
                )
            }
        )
    }

    private fun beginNavigation() {
        handler.removeCallbacks(scan)
        handler.removeCallbacks(pauseMonitor)
        handler.postDelayed(pauseMonitor, 2500)

        navigationId++
        started = SystemClock.elapsedRealtime()

        navigating = true
        step = Step.FIND_SD

        sidebarAttempted = false
        sidebarOpenedAt = 0L
        sawActiveVoice = false
        voiceAttemptInProgress = false
        voiceButtonPressedAt = 0L
        chatSeenDuringPause = false
        screenAbsentChecks = 0
        readyToSpeakChecks = 0

        saidOne = false
        saidTwo = false
        threeUtteranceId = null

        tts?.stop()
        releasePlayer()
        speechQueue.clear()

        if (tts == null) initializeSpeech()

        val root = chatRoot()

        if (root == null) {
            report("Abriendo ChatGPT para buscar SD.")

            if (!ChatOpener.open(this)) {
                finish("No se pudo abrir ChatGPT.")
                return
            }
        } else {
            report("ChatGPT abierto. Buscando SD.")
        }

        schedule(550)
    }

    private fun schedule(delay: Long) {
        handler.removeCallbacks(scan)
        handler.postDelayed(scan, delay)
    }

    private fun finish(
        message: String,
        keepMicReleased: Boolean = false
    ) {
        navigating = false
        handler.removeCallbacks(scan)
        report(message)

        if (
            !keepMicReleased &&
            WashListenerService.isWaitingForChat()
        ) {
            handler.postDelayed(
                {
                    WashListenerService.rearmAfterChatLeft()
                },
                1800
            )
        }
    }

    private fun chatRoot(): AccessibilityNodeInfo? {
        val root = rootInActiveWindow

        if (
            root?.packageName?.toString() == CHATGPT
        ) return root

        return windows.firstOrNull {
            it.isActive
        }?.root?.takeIf {
            it.packageName?.toString() == CHATGPT
        }
    }

    private fun inspectPausedSession() {
        if (navigating) return

        val root = chatRoot()

        if (root == null) {
            readyToSpeakChecks = 0

            if (
                WashRearmRules.shouldRearmAfterExit(
                    chatSeenDuringPause,
                    ++screenAbsentChecks
                )
            ) {
                report(
                    "ChatGPT salió de pantalla. " +
                        "JARVIS vuelve a escuchar."
                )
                screenAbsentChecks = 0
                WashListenerService.rearmAfterChatLeft()
            }
            return
        }

        chatSeenDuringPause = true
        screenAbsentChecks = 0

        val inVoice = findNode(root) {
            SdUiRules.isActiveVoiceIndicator(it)
        } != null

        if (inVoice) {
            sawActiveVoice = true

            if (voiceButtonPressedAt == 0L) {
                voiceButtonPressedAt =
                    SystemClock.elapsedRealtime() - 5500L
            }

            readyToSpeakChecks = 0
            return
        }

        if (
            voiceButtonPressedAt == 0L ||
            SystemClock.elapsedRealtime() -
                voiceButtonPressedAt < 5500L
        ) return

        val composerVisible = findNode(root) {
            SdUiRules.isChatComposer(it) ||
                SdUiRules.isStartVoiceButton(it)
        } != null || findEditableNode(root)

        if (composerVisible) {
            readyToSpeakChecks++

            val elapsed =
                SystemClock.elapsedRealtime() -
                    voiceButtonPressedAt

            if (
                WashRearmRules.isSafeToRearm(
                    elapsed,
                    inVoice,
                    composerVisible,
                    readyToSpeakChecks
                )
            ) {
                readyToSpeakChecks = 0
                report(
                    "Volvió el chat. Reanudando JARVIS."
                )
                WashListenerService.rearmAfterChatLeft()
            }
        } else {
            readyToSpeakChecks = 0
        }
    }

    override fun onAccessibilityEvent(
        event: AccessibilityEvent?
    ) {
        val type = event?.eventType ?: return

        if (
            type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            type != AccessibilityEvent.TYPE_VIEW_SCROLLED
        ) return

        val pkg = event.packageName
            ?.toString().orEmpty()

        if (pkg == CHATGPT) {
            chatWasOnScreen = true

            if (WashListenerService.isWaitingForChat()) {
                chatSeenDuringPause = true
            }

            handler.removeCallbacks(rearm)

            if (navigating) {
                if (
                    SystemClock.elapsedRealtime() -
                        lastScanAt > 220
                ) {
                    schedule(250)
                }
            } else {
                observeVoiceEnd()
            }
        } else if (
            pkg != packageName &&
            pkg != "com.android.systemui" &&
            pkg.isNotBlank() &&
            !pkg.contains("inputmethod", true) &&
            !pkg.contains("honeyboard", true) &&
            !pkg.contains("keyboard", true)
        ) {
            if (
                chatWasOnScreen &&
                !navigating &&
                chatRoot() == null
            ) {
                chatWasOnScreen = false
                handler.removeCallbacks(rearm)
                handler.postDelayed(rearm, 5000)
            }
        }
    }

    private fun observeVoiceEnd() {
        val now = SystemClock.elapsedRealtime()

        if (
            now - lastVoiceObservationAt < 1000
        ) return

        lastVoiceObservationAt = now
        val root = chatRoot() ?: return

        val activeVoice = findNode(root) {
            SdUiRules.isActiveVoiceIndicator(it)
        } != null

        if (activeVoice) {
            sawActiveVoice = true

            if (voiceButtonPressedAt == 0L) {
                voiceButtonPressedAt = now - 5500L
            }

            readyToSpeakChecks = 0
        } else if (
            sawActiveVoice &&
            WashListenerService.isWaitingForChat()
        ) {
            handler.removeCallbacks(pauseMonitor)
            handler.postDelayed(pauseMonitor, 850)
        }
    }

    private fun scanWindow() {
        if (!navigating) return

        lastScanAt = SystemClock.elapsedRealtime()

        if (lastScanAt - started > 20000L) {
            finish(
                "Tiempo agotado buscando SD y Voz."
            )
            return
        }

        val root = chatRoot()

        if (root == null) {
            schedule(600)
            return
        }

        if (!ttsReady) {
            if (ttsFailed) {
                finish(
                    "No hay voz TTS. Voz no se pulsó."
                )
            } else {
                schedule(250)
            }
            return
        }

        if (!saidOne) {
            if (
                !speak(
                    1,
                    "jarvis_${navigationId}_1"
                )
            ) {
                finish("No se pudo generar Uno.")
                return
            }
            saidOne = true
        }

        if (
            findNode(root) {
                SdUiRules.isActiveVoiceIndicator(it)
            } != null
        ) {
            sawActiveVoice = true
            voiceButtonPressedAt =
                SystemClock.elapsedRealtime() - 5500L
            chatSeenDuringPause = true

            finish(
                "ChatGPT ya está en Voz.",
                keepMicReleased = true
            )
            return
        }

        when (step) {
            Step.FIND_SD -> findSd(root)
            Step.START_VOICE -> startVoice(root)
            Step.WAIT_THREE -> {}
        }
    }

    private fun findSd(root: AccessibilityNodeInfo) {
        val sd = findNode(root) {
            SdUiRules.isSdTitle(it)
        }

        if (
            sd != null &&
            clickNodeOrParent(sd)
        ) {
            step = Step.START_VOICE
            report("Chat SD seleccionado.")
            schedule(900)
            return
        }

        if (!sidebarAttempted) {
            val menu = findNode(root) {
                SdUiRules.isNavigationButton(it)
            }

            if (
                menu != null &&
                clickNodeOrParent(menu)
            ) {
                sidebarAttempted = true
                sidebarOpenedAt =
                    SystemClock.elapsedRealtime()

                report(
                    "Abriendo historial para buscar SD."
                )
                schedule(850)
                return
            }

            sidebarAttempted = true
            sidebarOpenedAt =
                SystemClock.elapsedRealtime()
        }

        if (
            SystemClock.elapsedRealtime() -
                sidebarOpenedAt > 6000
        ) {
            finish("No se encontró el chat SD.")
        } else {
            schedule(650)
        }
    }

    private fun startVoice(
        root: AccessibilityNodeInfo
    ) {
        val voiceButton = findNode(root) {
            SdUiRules.isStartVoiceButton(it)
        }

        if (voiceButton != null) {
            if (!saidTwo) {
                if (
                    !speak(
                        2,
                        "jarvis_${navigationId}_2"
                    )
                ) {
                    finish("No se pudo generar Dos.")
                    return
                }
                saidTwo = true
            }

            if (!voiceAttemptInProgress) {
                voiceAttemptInProgress = true
                step = Step.WAIT_THREE

                val id = "jarvis_${navigationId}_3"
                threeUtteranceId = id

                if (!speak(3, id)) {
                    finish(
                        "No se pudo generar Tres."
                    )
                    return
                }

                report(
                    "Esperando Tres antes de activar Voz."
                )
            }
            return
        }

        if (
            SystemClock.elapsedRealtime() -
                started > 15000L
        ) {
            finish(
                "SD abierto, pero no se encontró Voz."
            )
        } else {
            schedule(600)
        }
    }

    private fun findEditableNode(
        root: AccessibilityNodeInfo
    ): Boolean {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)

        var count = 0

        while (
            queue.isNotEmpty() &&
            count++ < 450
        ) {
            val node = queue.removeFirst()

            if (
                node.isVisibleToUser &&
                node.isEditable &&
                node.isEnabled
            ) return true

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let(queue::addLast)
            }
        }

        return false
    }

    private fun findNode(
        root: AccessibilityNodeInfo,
        rule: (CharSequence?) -> Boolean
    ): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)

        var count = 0

        while (
            queue.isNotEmpty() &&
            count++ < 450
        ) {
            val node = queue.removeFirst()

            if (
                node.isVisibleToUser &&
                (
                    rule(node.text) ||
                    rule(node.contentDescription)
                )
            ) return node

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let(queue::addLast)
            }
        }

        return null
    }

    private fun clickNodeOrParent(
        node: AccessibilityNodeInfo
    ): Boolean {
        var candidate: AccessibilityNodeInfo? = node

        repeat(5) {
            val item = candidate ?: return@repeat

            if (
                item.isClickable &&
                item.isEnabled &&
                item.performAction(
                    AccessibilityNodeInfo.ACTION_CLICK
                )
            ) return true

            candidate = item.parent
        }

        val rect = Rect()
        node.getBoundsInScreen(rect)

        if (
            !rect.isEmpty &&
            node.isVisibleToUser
        ) {
            val path = Path().apply {
                moveTo(
                    rect.exactCenterX(),
                    rect.exactCenterY()
                )
            }

            val gesture = GestureDescription.Builder()
                .addStroke(
                    GestureDescription.StrokeDescription(
                        path, 0, 90
                    )
                )
                .build()

            return dispatchGesture(
                gesture,
                null,
                null
            )
        }

        return false
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        handler.removeCallbacks(scan)
        handler.removeCallbacks(rearm)
        handler.removeCallbacks(pauseMonitor)

        tts?.stop()
        tts?.shutdown()
        releasePlayer()
        speechQueue.clear()
        tts = null

        if (current.get() === this) {
            current.clear()
        }

        super.onDestroy()
    }
}
