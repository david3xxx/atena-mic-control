package pe.atena.mic

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.lang.ref.WeakReference

/**
 * Navega únicamente dentro de la app oficial ChatGPT. Busca el chat fijado 'SD'
 * por su título exacto y luego el botón de Voz por etiqueta accesible.
 * Si ChatGPT cambia su interfaz, aborta sin pulsar coordenadas no verificadas.
 */
class WashAccessibilityService : AccessibilityService() {
    companion object {
        private const val CHATGPT = "com.openai.chatgpt"
        private var current = WeakReference<WashAccessibilityService>(null)
        fun requestOpenSdAndVoice(): Boolean = current.get()?.let {
            it.handler.post { it.beginNavigation(calibrate = false) }; true
        } ?: false

        /** La calibración captura exactamente un toque en una superposición temporal. */
        fun requestVoiceCalibration(): Boolean = current.get()?.let {
            it.handler.post { it.beginNavigation(calibrate = true) }; true
        } ?: false
    }

    private enum class Step { FIND_SD, START_VOICE, VERIFY_VOICE }
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
    private var calibrating = false
    private var calibrationOverlay: View? = null
    private var stepStartedAt = 0L
    private var attemptedSavedTap = false
    private var voiceAttemptOrigin = ""
    private val milestones = VoiceMilestones()
    private var speaker: StepSpeaker? = null
    private var sdChosenForThisRun = false

    /** Numeros de confirmacion por voz, nunca solo por una solicitud de abrir/tocar. */
    private fun confirmStage(stage: Int, description: String) {
        if (milestones.confirm(stage)) {
            report("PASO $stage CONFIRMADO: $description")
            speaker?.speak(stage)
        }
    }

    /** Comprobación ligera (cada 2.5 s), SOLO mientras ATENA ha cedido el micrófono a ChatGPT. */
    private val pauseMonitor = object : Runnable {
        override fun run() {
            if (!WashListenerService.isWaitingForChat()) return
            if (getSharedPreferences("atena", MODE_PRIVATE).getBoolean("auto_rearm", true)) {
                inspectPausedSession()
            }
            if (WashListenerService.isWaitingForChat()) handler.postDelayed(this, 2500)
        }
    }

    private val scan = Runnable { scanWindow() }
    private val rearm = Runnable {
        // Solo reactivar si el usuario habilitó la opción experimental.
        val pref = getSharedPreferences("atena", MODE_PRIVATE)
        if (pref.getBoolean("auto_rearm", true)) WashListenerService.rearmAfterChatLeft()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        current = WeakReference(this)
        speaker = StepSpeaker(this) { problem -> report(problem) }
        report("Accesibilidad lista: ATENA puede buscar SD y solicitar un toque de Voz.")
        if (WashListenerService.isWaitingForChat()) {
            handler.removeCallbacks(pauseMonitor)
            handler.postDelayed(pauseMonitor, 1500)
        }
    }

    private fun report(message: String) {
        AtenaDiagnostics.append(this, message)
        sendBroadcast(Intent(WashListenerService.ACTION_STATUS).apply {
            setPackage(packageName)
            putExtra(WashListenerService.EXTRA_MESSAGE, message)
        })
    }

    private fun beginNavigation(calibrate: Boolean) {
        clearCalibrationOverlay()
        calibrating = calibrate
        attemptedSavedTap = false
        voiceAttemptOrigin = ""
        sdChosenForThisRun = false
        milestones.reset()
        // Los números se pronuncian solo al confirmar cada paso de ESTE intento.
        speaker?.cancelPending()
        handler.removeCallbacks(scan)
        handler.removeCallbacks(pauseMonitor)
        handler.postDelayed(pauseMonitor, 2500)
        voiceButtonPressedAt = 0L
        chatSeenDuringPause = false
        screenAbsentChecks = 0
        readyToSpeakChecks = 0
        navigating = true
        step = Step.FIND_SD
        started = SystemClock.elapsedRealtime()
        stepStartedAt = started
        sidebarAttempted = false
        sidebarOpenedAt = 0L
        sawActiveVoice = false
        // La propia AccessibilityService es la que inicia la actividad cuando es posible.
        val root = chatRoot()
        if (root?.packageName?.toString() == CHATGPT) {
            confirmStage(1, "ChatGPT ya está visible")
        }
        if (root?.packageName?.toString() != CHATGPT) {
            report("WASH: abriendo ChatGPT para buscar el chat SD…")
            if (!ChatOpener.open(this)) {
                finish("No se pudo abrir ChatGPT. Comprueba que esté instalado.")
                return
            }
        } else report("WASH: ChatGPT ya está abierto; buscando SD.")
        schedule(550)
    }

    private fun schedule(delay: Long) {
        handler.removeCallbacks(scan)
        handler.postDelayed(scan, delay)
    }

    private fun finish(message: String, keepMicReleased: Boolean = false) {
        navigating = false
        handler.removeCallbacks(scan)
        clearCalibrationOverlay()
        calibrating = false
        report(message)
        if (!keepMicReleased && WashListenerService.isWaitingForChat()) {
            // Si no se logró abrir SD/Voz, NUNCA dejar el disparador WASH pausado.
            handler.postDelayed({ WashListenerService.rearmAfterChatLeft() }, 1800)
        }
    }

    private fun chatRoot(): AccessibilityNodeInfo? {
        // En algunos Samsung el foco pasa temporalmente al teclado o un diálogo;
        // comprobar ventanas activas/enfocadas sin escoger una ventana antigua de fondo.
        val root = rootInActiveWindow
        if (root?.packageName?.toString() == CHATGPT) return root
        return windows.firstOrNull { it.isActive && it.root?.packageName?.toString() == CHATGPT }
            ?.root ?: windows.firstOrNull { it.isFocused &&
                it.root?.packageName?.toString() == CHATGPT }?.root
    }

    /**
     * No usa audio ni analiza conversaciones. Se limita a comprobar si se ve el botón
     * que inicia Voz o el cuadro de escribir, y si la interfaz de llamada sigue activa.
     * Requiere dos observaciones consecutivas para evitar transiciones momentáneas.
     */
    private fun inspectPausedSession() {
        if (navigating) return
        val root = chatRoot()
        if (root == null) {
            readyToSpeakChecks = 0
            if (WashRearmRules.shouldRearmAfterExit(chatSeenDuringPause, ++screenAbsentChecks)) {
                report("ChatGPT salió de pantalla; ATENA vuelve a escuchar WASH.")
                screenAbsentChecks = 0
                WashListenerService.rearmAfterChatLeft()
            }
            return
        }
        chatSeenDuringPause = true
        screenAbsentChecks = 0
        val inVoice = findNode(root) { SdUiRules.isActiveVoiceIndicator(it) } != null
        if (inVoice) {
            sawActiveVoice = true
            if (voiceButtonPressedAt == 0L) voiceButtonPressedAt =
                SystemClock.elapsedRealtime() - 5500L // Una llamada ya estaba activa.
            readyToSpeakChecks = 0
            return
        }
        // Esperar a que haya tiempo de abrir la conversación y mostrar la interfaz de Voz.
        if (voiceButtonPressedAt == 0L ||
            SystemClock.elapsedRealtime() - voiceButtonPressedAt < 5500L) return
        val composerVisible = findNode(root) { SdUiRules.isChatComposer(it) ||
            SdUiRules.isStartVoiceButton(it) } != null ||
            findEditableNode(root)
        if (composerVisible) {
            readyToSpeakChecks++
            if (WashRearmRules.isSafeToRearm(
                    SystemClock.elapsedRealtime() - voiceButtonPressedAt,
                    inVoice, composerVisible, readyToSpeakChecks)) {
                readyToSpeakChecks = 0
                report("Volvió el cuadro de chat; reanudando WASH sin pulsar ChatGPT.")
                WashListenerService.rearmAfterChatLeft()
            }
        } else readyToSpeakChecks = 0
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val type = event?.eventType ?: return
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            type != AccessibilityEvent.TYPE_VIEW_SCROLLED) return
        val pkg = event.packageName?.toString().orEmpty()
        if (pkg == CHATGPT) {
            chatWasOnScreen = true
            if (WashListenerService.isWaitingForChat()) chatSeenDuringPause = true
            handler.removeCallbacks(rearm)
            if (navigating) {
                // Debounce: las actualizaciones de pantalla suelen llegar en ráfagas.
                if (SystemClock.elapsedRealtime() - lastScanAt > 220) schedule(250)
            } else if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
                observeVoiceEnd()
            }
        } else if (pkg != packageName && pkg != "com.android.systemui" && pkg.isNotBlank() &&
            !pkg.contains("inputmethod", ignoreCase = true) &&
            !pkg.contains("honeyboard", ignoreCase = true) &&
            !pkg.contains("keyboard", ignoreCase = true)) {
            if (chatWasOnScreen && !navigating && chatRoot() == null) {
                chatWasOnScreen = false
                handler.removeCallbacks(rearm)
                handler.postDelayed(rearm, 5000)
            }
        }
    }

    /** La transición a chat normal se confirma mediante el monitor, nunca con un solo evento. */
    private fun observeVoiceEnd() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastVoiceObservationAt < 1000) return
        lastVoiceObservationAt = now
        val root = chatRoot() ?: return
        val active = findNode(root) { SdUiRules.isActiveVoiceIndicator(it) } != null
        if (active) {
            sawActiveVoice = true
            if (voiceButtonPressedAt == 0L) voiceButtonPressedAt =
                SystemClock.elapsedRealtime() - 5500L
            readyToSpeakChecks = 0
        } else if (sawActiveVoice && WashListenerService.isWaitingForChat()) {
            // Una ausencia momentánea de etiqueta puede producirse durante una respuesta.
            // Por seguridad, exigir dos sondeos de compositor antes de liberar micrófono.
            handler.removeCallbacks(pauseMonitor)
            handler.postDelayed(pauseMonitor, 850)
        }
    }

    private fun scanWindow() {
        if (!navigating) return
        lastScanAt = SystemClock.elapsedRealtime()
        if (lastScanAt - started > 12500) {
            finish("Tiempo agotado: no pude confirmar SD y el botón de Voz. No toqué controles desconocidos.")
            return
        }
        val root = chatRoot()
        if (root?.packageName?.toString() != CHATGPT) {
            schedule(600)
            return
        }
        confirmStage(1, "ChatGPT abierto y visible")
        // No se interrumpe una sesión de Voz si la interfaz informa que está activa.
        if (findNode(root) { SdUiRules.isActiveVoiceIndicator(it) } != null) {
            sawActiveVoice = true
            voiceButtonPressedAt = SystemClock.elapsedRealtime() - 5500L
            chatSeenDuringPause = true
            // No decir DOS sin confirmar SD: la llamada podría pertenecer a otro chat.
            if (milestones.hasConfirmed(2)) confirmStage(3, "Modo de voz activo")
            finish("ChatGPT parece tener una llamada de Voz activa. ATENA no la interrumpió.", keepMicReleased = true)
            return
        }
        when (step) {
            Step.FIND_SD -> findSd(root)
            Step.START_VOICE -> startVoice(root)
            Step.VERIFY_VOICE -> verifyVoiceStarted(root)
        }
    }

    private fun composerVisible(root: AccessibilityNodeInfo): Boolean =
        findNode(root) { SdUiRules.isChatComposer(it) } != null || findEditableNode(root)

    private fun findSd(root: AccessibilityNodeInfo) {
        val sd = findNode(root) { SdUiRules.isSdTitle(it) }
        val pinnedSectionOpen = findNode(root) { SdUiRules.isPinnedHeading(it) } != null
        if (sd != null) {
            // En la cabecera del chat SD, el compositor está disponible; no abrir el menú otra vez.
            // Si la barra lateral está abierta, el elemento SD suele estar en una lista sin editor.
            if (!pinnedSectionOpen && !sidebarAttempted && composerVisible(root)) {
                sdChosenForThisRun = true
                step = Step.START_VOICE
                stepStartedAt = SystemClock.elapsedRealtime()
                report("Chat SD ya visible: omitiendo búsqueda repetida. Buscando Voz…")
                schedule(180)
                return
            }
            if (clickNodeOrParent(sd)) {
                sdChosenForThisRun = true
                step = Step.START_VOICE
                stepStartedAt = SystemClock.elapsedRealtime()
                report("Toque solicitado en SD. Comprobando botón de Voz…")
                schedule(320)
                return
            }
        }
        if (!sidebarAttempted) {
            val menu = findNode(root) { SdUiRules.isNavigationButton(it) }
            if (menu != null && clickNodeOrParent(menu)) {
                sidebarAttempted = true
                sidebarOpenedAt = SystemClock.elapsedRealtime()
                report("Abriendo barra lateral para buscar SD…")
                schedule(380)
                return
            }
            sidebarAttempted = true
            sidebarOpenedAt = SystemClock.elapsedRealtime()
            report("No se identificó el menú de chats; buscando SD en elementos accesibles…")
        }
        if (SystemClock.elapsedRealtime() - sidebarOpenedAt > 4700) {
            finish("ETAPA SD: no localicé SD ni el botón del menú. Verifica Accesibilidad y captura el diagnóstico.")
        } else schedule(420)
    }

    /** Solo concluir que se inició Voz cuando cambia la interfaz, no por pulsar un botón. */
    private fun verifyVoiceStarted(root: AccessibilityNodeInfo) {
        val elapsed = SystemClock.elapsedRealtime() - voiceButtonPressedAt
        val voiceActive = findNode(root) { SdUiRules.isActiveVoiceIndicator(it) } != null
        if (voiceActive) {
            sawActiveVoice = true
            confirmStage(3, "Modo de voz activo")
            finish("ETAPA VOZ: interfaz de llamada detectada ($voiceAttemptOrigin).", keepMicReleased = true)
            return
        }
        // Si desapareció el cuadro para escribir y pasó el cambio de pantalla, probablemente
        // comenzó la conversación; no interferimos mientras esperamos reconfirmación.
        if (VoiceFlowRules.likelyStarted(elapsed, false, composerVisible(root))) {
            finish("ETAPA VOZ: desapareció el cuadro de escribir; posiblemente abierta ($voiceAttemptOrigin). Paso 3 sin confirmar.", keepMicReleased = true)
            return
        }
        // El primer toque puede no abrir Voz si ChatGPT cambió las etiquetas accesibles.
        // Segundo método: posición que el usuario calibró previamente, no una estimación.
        if (VoiceFlowRules.retryCalibratedTap(elapsed, composerVisible(root), attemptedSavedTap)) {
            attemptedSavedTap = true
            if (tapCalibratedVoice()) return
        }
        if (VoiceFlowRules.timedOut(elapsed)) {
            finish("ETAPA VOZ: el chat sigue mostrando la barra de escritura tras el toque. Calibra el botón Voz.")
        } else schedule(300)
    }

    private fun startVoice(root: AccessibilityNodeInfo) {
        // El toque en la lista SD no basta: confirmar que ya se ve el editor del chat.
        val pinnedPanelVisible = findNode(root) { SdUiRules.isPinnedHeading(it) } != null
        if (sdChosenForThisRun && !pinnedPanelVisible &&
            (composerVisible(root) ||
                findNode(root) { SdUiRules.isStartVoiceButton(it) } != null)) {
            confirmStage(2, "Chat SD abierto")
        }
        if (calibrating) {
            if (composerVisible(root)) showCalibrationOverlay()
            else if (SystemClock.elapsedRealtime() - stepStartedAt > 3500L)
                finish("CALIBRACIÓN: no apareció el cuadro del chat SD. Abre SD y vuelve a intentar.")
            else schedule(250)
            return
        }
        val voiceButton = findNode(root) { SdUiRules.isStartVoiceButton(it) }
        if (voiceButton != null && clickNodeOrParent(voiceButton)) {
            voiceButtonPressedAt = SystemClock.elapsedRealtime()
            chatSeenDuringPause = true
            voiceAttemptOrigin = "botón accesible"
            step = Step.VERIFY_VOICE
            report("ETAPA VOZ: toque enviado; confirmando que se abra la llamada…")
            schedule(450)
            return
        }
        val elapsed = SystemClock.elapsedRealtime() - stepStartedAt
        // Reducimos de 1.2s a 0.5s la espera si el botón ya fue calibrado.
        if (VoiceFlowRules.useSavedPosition(elapsed, composerVisible(root), attemptedSavedTap)) {
            attemptedSavedTap = true
            if (tapCalibratedVoice()) return
        }
        if (VoiceFlowRules.timedOut(elapsed)) {
            finish("ETAPA VOZ: no se identifica el botón. Usa CALIBRAR BOTÓN VOZ una vez.")
        } else schedule(250)
    }

    /** El usuario elige la posición. No pulsar coordenadas predeterminadas o adivinadas. */
    private fun tapCalibratedVoice(): Boolean {
        val preferences = getSharedPreferences("atena", MODE_PRIVATE)
        if (!preferences.getBoolean("voice_calibrated", false)) return false
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val bounds = wm.currentWindowMetrics.bounds
        if (preferences.getInt("voice_orientation", -1) != resources.configuration.orientation) {
            report("Calibración guardada para otra orientación: no se utilizará.")
            return false
        }
        val xFraction = preferences.getFloat("voice_fraction_x", -1f)
        val yFraction = preferences.getFloat("voice_fraction_y", -1f)
        if (xFraction !in 0.03f..0.97f || yFraction !in 0.12f..0.97f) {
            report("Coordenadas calibradas inválidas; se ignoran por seguridad.")
            return false
        }
        val x = bounds.left + bounds.width() * xFraction
        val y = bounds.top + bounds.height() * yFraction
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder().addStroke(
            GestureDescription.StrokeDescription(path, 0, 75)).build()
        val submitted = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                voiceButtonPressedAt = SystemClock.elapsedRealtime()
                chatSeenDuringPause = true
                voiceAttemptOrigin = "posición calibrada"
                step = Step.VERIFY_VOICE
                report("ETAPA VOZ: toque calibrado completado. Verificando inicio…")
                schedule(500)
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                if (navigating) finish("ETAPA VOZ: Android canceló la pulsación calibrada.")
            }
        }, null)
        if (!submitted) {
            report("ETAPA VOZ: Android rechazó la pulsación calibrada.")
        } else {
            report("ETAPA VOZ: solicitando pulsación calibrada…")
            // Esperar callback de Android, evitando una segunda pulsación sobre el mismo control.
            handler.removeCallbacks(scan)
        }
        return submitted
    }

    private fun showCalibrationOverlay() {
        if (calibrationOverlay != null) return
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val density = resources.displayMetrics.density
        val v = object : View(this) {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            override fun onDraw(canvas: Canvas) {
                canvas.drawColor(Color.argb(24, 0, 0, 0))
                paint.color = Color.argb(240, 18, 32, 49)
                canvas.drawRect(0f, 0f, width.toFloat(), 100f * density, paint)
                paint.color = Color.WHITE
                paint.textSize = 16f * density
                paint.isFakeBoldText = true
                canvas.drawText("TOCA EL BOTÓN VOZ DE CHATGPT", 12f*density, 37f*density, paint)
                paint.textSize = 12f*density
                paint.isFakeBoldText = false
                canvas.drawText("Arriba: cancelar. El toque se guarda, no se ejecuta.",
                    12f*density, 65f*density, paint)
            }
            override fun onTouchEvent(event: MotionEvent): Boolean {
                if (event.actionMasked == MotionEvent.ACTION_DOWN) return true
                if (event.actionMasked != MotionEvent.ACTION_UP) return true
                if (event.y < 100f * density) {
                    finish("Calibración cancelada.")
                    return true
                }
                val bounds = wm.currentWindowMetrics.bounds
                if (bounds.width() <= 0 || bounds.height() <= 0) {
                    finish("Calibración falló: pantalla sin tamaño válido.")
                    return true
                }
                val fx = (event.rawX - bounds.left) / bounds.width()
                val fy = (event.rawY - bounds.top) / bounds.height()
                if (fx !in 0.03f..0.97f || fy !in 0.12f..0.97f) {
                    report("Toque fuera del área permitida. Vuelve a tocar el botón Voz.")
                    return true
                }
                getSharedPreferences("atena", MODE_PRIVATE).edit()
                    .putBoolean("voice_calibrated", true)
                    .putFloat("voice_fraction_x", fx)
                    .putFloat("voice_fraction_y", fy)
                    .putInt("voice_orientation", resources.configuration.orientation).apply()
                finish("Botón de Voz calibrado. Ahora usa PROBAR AHORA para comprobarlo.")
                return true
            }
        }
        val params = WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.START }
        try {
            wm.addView(v, params)
            calibrationOverlay = v
            report("CALIBRACIÓN: toca exactamente el botón Voz que ves en el chat SD.")
        } catch (ex: Exception) {
            finish("No se pudo mostrar calibración: ${ex.message}")
        }
    }

    private fun clearCalibrationOverlay() {
        calibrationOverlay?.let {
            try { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(it) }
            catch (_: Exception) { }
        }
        calibrationOverlay = null
    }

    /** Detecta el editor de mensajes aunque cambie su texto de accesibilidad. */
    private fun findEditableNode(root: AccessibilityNodeInfo): Boolean {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var count = 0
        while (queue.isNotEmpty() && count++ < 450) {
            val node = queue.removeFirst()
            if (node.isVisibleToUser && node.isEditable && node.isEnabled) return true
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::addLast)
        }
        return false
    }

    /** Búsqueda acotada para no recorrer indefinidamente árboles de UI extensos. */
    private fun findNode(root: AccessibilityNodeInfo, rule: (CharSequence?) -> Boolean): AccessibilityNodeInfo? {
        val pending = ArrayDeque<AccessibilityNodeInfo>()
        pending.add(root)
        var inspected = 0
        while (pending.isNotEmpty() && inspected++ < 450) {
            val node = pending.removeFirst()
            if (node.isVisibleToUser && (rule(node.text) || rule(node.contentDescription))) return node
            for (i in 0 until node.childCount) node.getChild(i)?.let(pending::addLast)
        }
        return null
    }

    /** Acción accesible primero; gesto en el centro del MISMO nodo validado si hace falta. */
    private fun clickNodeOrParent(node: AccessibilityNodeInfo): Boolean {
        var candidate: AccessibilityNodeInfo? = node
        repeat(5) {
            if (candidate == null) return@repeat
            val item = candidate!!
            if (item.isClickable && item.isEnabled) {
                if (item.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            }
            candidate = item.parent
        }
        val rect = Rect()
        node.getBoundsInScreen(rect)
        if (!rect.isEmpty && node.isVisibleToUser) {
            val path = Path().apply { moveTo(rect.exactCenterX(), rect.exactCenterY()) }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 90)).build()
            return dispatchGesture(gesture, null, null)
        }
        return false
    }

    override fun onInterrupt() { }
    override fun onDestroy() {
        handler.removeCallbacks(scan)
        handler.removeCallbacks(rearm)
        handler.removeCallbacks(pauseMonitor)
        clearCalibrationOverlay()
        speaker?.shutdown()
        speaker = null
        if (current.get() === this) current.clear()
        super.onDestroy()
    }
}
