
package pe.atena.mic

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.lang.ref.WeakReference
import java.util.Locale

class WashAccessibilityService : AccessibilityService() {

    companion object {
        private const val CHATGPT = "com.openai.chatgpt"
        private var current =
            WeakReference<WashAccessibilityService>(null)

        fun requestOpenSdAndVoice(): Boolean =
            current.get()?.let {
                it.handler.post { it.beginNavigation() }
                true
            } ?: false
    }

    private enum class Step { FIND_SD, START_VOICE }

    private val handler = Handler(Looper.getMainLooper())
    private var navigating = false
    private var step = Step.FIND_SD
    private var started = 0L
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
    private var lastAnnounced = 0
    private val pending = ArrayDeque<Int>()
    private var awaitingVoice = false

    private fun initializeTts() {
        if (tts != null) return

        tts = TextToSpeech(applicationContext) { result ->
            handler.post {
                if (result == TextToSpeech.SUCCESS) {
                    val engine = tts
                    if (engine != null) {
                        val language =
                            engine.setLanguage(Locale("es", "PE"))

                        ttsReady =
                            language != TextToSpeech.LANG_MISSING_DATA &&
                            language != TextToSpeech.LANG_NOT_SUPPORTED

                        if (ttsReady) {
                            engine.setSpeechRate(0.9f)
                            flushSpeech()
                        }
                    }
                }
            }
        }
    }

    private fun say(number: Int) {
        if (number <= lastAnnounced) return

        for (n in (lastAnnounced + 1)..number) {
            pending.addLast(n)
        }

        lastAnnounced = number
        flushSpeech()
    }

    private fun flushSpeech() {
        if (!ttsReady) return

        val engine = tts ?: return

        while (pending.isNotEmpty()) {
            val number = pending.removeFirst()

            val word = when (number) {
                1 -> "Uno"
                2 -> "Dos"
                else -> "Tres"
            }

            engine.speak(
                word,
                TextToSpeech.QUEUE_ADD,
                null,
                "jarvis_${started}_$number"
            )
        }
    }

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
        val pref =
            getSharedPreferences("atena", MODE_PRIVATE)

        if (pref.getBoolean("auto_rearm", true)) {
            WashListenerService.rearmAfterChatLeft()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        current = WeakReference(this)
        initializeTts()

        report(
            "Accesibilidad lista: JARVIS puede buscar SD y Voz."
        )

        if (WashListenerService.isWaitingForChat()) {
            handler.removeCallbacks(pauseMonitor)
            handler.postDelayed(pauseMonitor, 1500)
        }
    }

    private fun report(message: String) {
        getSharedPreferences("atena", MODE_PRIVATE)
            .edit()
            .putString("last_status", "• $message")
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

        voiceButtonPressedAt = 0L
        chatSeenDuringPause = false
        screenAbsentChecks = 0
        readyToSpeakChecks = 0

        navigating = true
        lastAnnounced = 0
        pending.clear()
        awaitingVoice = false

        step = Step.FIND_SD
        started = SystemClock.elapsedRealtime()
        sidebarAttempted = false
        sidebarOpenedAt = 0L
        sawActiveVoice = false

        val root = chatRoot()

        if (root?.packageName?.toString() != CHATGPT) {
            report("JARVIS: abriendo ChatGPT para buscar SD.")

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
            handler.postDelayed({
                WashListenerService.rearmAfterChatLeft()
            }, 1800)
        }
    }

    private fun chatRoot(): AccessibilityNodeInfo? {
        val root = rootInActiveWindow

        if (root != null) {
            return root.takeIf {
                it.packageName?.toString() == CHATGPT
            }
        }

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
                awaitingVoice = false
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
            if (awaitingVoice) {
                say(3)
                awaitingVoice = false
            }

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
        ) {
            return
        }

        val composerVisible =
            findNode(root) {
                SdUiRules.isChatComposer(it) ||
                SdUiRules.isStartVoiceButton(it)
            } != null || findEditableNode(root)

        if (composerVisible) {
            readyToSpeakChecks++

            if (
                WashRearmRules.isSafeToRearm(
                    SystemClock.elapsedRealtime() -
                        voiceButtonPressedAt,
                    inVoice,
                    composerVisible,
                    readyToSpeakChecks
                )
            ) {
                readyToSpeakChecks = 0

                report(
                    "Volvió el cuadro de chat. " +
                    "Reanudando JARVIS."
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
        ) {
            return
        }

        val pkg = event.packageName?.toString().orEmpty()

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
            } else if (
                type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            ) {
                observeVoiceEnd()
            }
        } else if (
            pkg != packageName &&
            pkg != "com.android.systemui" &&
            pkg.isNotBlank() &&
            !pkg.contains("inputmethod", ignoreCase = true) &&
            !pkg.contains("honeyboard", ignoreCase = true) &&
            !pkg.contains("keyboard", ignoreCase = true)
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

        if (now - lastVoiceObservationAt < 1000) return

        lastVoiceObservationAt = now
        val root = chatRoot() ?: return

        val active = findNode(root) {
            SdUiRules.isActiveVoiceIndicator(it)
        } != null

        if (active) {
            if (awaitingVoice) {
                say(3)
                awaitingVoice = false
            }

            sawActiveVoice = true

            if (voiceButtonPressedAt == 0L) {
                voiceButtonPressedAt =
                    SystemClock.elapsedRealtime() - 5500L
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

        if (lastScanAt - started > 16500) {
            finish(
                "Tiempo agotado: no pude confirmar SD y Voz."
            )
            return
        }

        val root = chatRoot()

        if (root?.packageName?.toString() != CHATGPT) {
            schedule(600)
            return
        }

        say(1)

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
                "ChatGPT ya tiene una sesión de Voz activa.",
                keepMicReleased = true
            )
            return
        }

        when (step) {
            Step.FIND_SD -> findSd(root)
            Step.START_VOICE -> startVoice(root)
        }
    }

    private fun findSd(root: AccessibilityNodeInfo) {
        val sd = findNode(root) {
            SdUiRules.isSdTitle(it)
        }

        if (sd != null && clickNodeOrParent(sd)) {
            step = Step.START_VOICE

            report(
                "Chat SD seleccionado. Buscando botón de Voz."
            )

            schedule(900)
            return
        }

        if (!sidebarAttempted) {
            val menu = findNode(root) {
                SdUiRules.isNavigationButton(it)
            }

            if (menu != null && clickNodeOrParent(menu)) {
                sidebarAttempted = true
                sidebarOpenedAt = SystemClock.elapsedRealtime()

                report(
                    "Abriendo historial para buscar SD."
                )

                schedule(850)
                return
            }

            sidebarAttempted = true
            sidebarOpenedAt = SystemClock.elapsedRealtime()

            report("Buscando SD en pantalla.")
        }

        if (
            SystemClock.elapsedRealtime() -
                sidebarOpenedAt > 6000
        ) {
            finish("No pude localizar el chat SD.")
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

        if (
            voiceButton != null &&
            clickNodeOrParent(voiceButton)
        ) {
            say(2)
            awaitingVoice = true

            voiceButtonPressedAt =
                SystemClock.elapsedRealtime()

            chatSeenDuringPause = true

            finish(
                "JARVIS pulsó Voz en SD.",
                keepMicReleased = true
            )
            return
        }

        if (
            SystemClock.elapsedRealtime() -
                started > 13500
        ) {
            finish(
                "Entré a SD, pero no encontré el botón de Voz."
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

        while (queue.isNotEmpty() && count++ < 450) {
            val node = queue.removeFirst()

            if (
                node.isVisibleToUser &&
                node.isEditable &&
                node.isEnabled
            ) {
                return true
            }

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
        val pending = ArrayDeque<AccessibilityNodeInfo>()
        pending.add(root)
        var inspected = 0

        while (
            pending.isNotEmpty() &&
            inspected++ < 450
        ) {
            val node = pending.removeFirst()

            if (
                node.isVisibleToUser &&
                (
                    rule(node.text) ||
                    rule(node.contentDescription)
                )
            ) {
                return node
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let(pending::addLast)
            }
        }

        return null
    }

    private fun clickNodeOrParent(
        node: AccessibilityNodeInfo
    ): Boolean {
        var candidate: AccessibilityNodeInfo? = node

        repeat(5) {
            if (candidate == null) return@repeat

            val item = candidate!!

            if (item.isClickable && item.isEnabled) {
                if (
                    item.performAction(
                        AccessibilityNodeInfo.ACTION_CLICK
                    )
                ) {
                    return true
                }
            }

            candidate = item.parent
        }

        val rect = Rect()
        node.getBoundsInScreen(rect)

        if (!rect.isEmpty && node.isVisibleToUser) {
            val path = Path().apply {
                moveTo(
                    rect.exactCenterX(),
                    rect.exactCenterY()
                )
            }

            val gesture =
                GestureDescription.Builder()
                    .addStroke(
                        GestureDescription.StrokeDescription(
                            path,
                            0,
                            90
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
        tts = null

        if (current.get() === this) {
            current.clear()
        }

        super.onDestroy()
    }
}
