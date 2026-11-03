package pe.atena.mic

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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
            it.handler.post { it.beginNavigation() }; true
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
    private var voiceEndRearmPending = false

    private val scan = Runnable { scanWindow() }
    private val rearm = Runnable {
        // Solo reactivar si el usuario habilitó la opción experimental.
        val pref = getSharedPreferences("atena", MODE_PRIVATE)
        if (pref.getBoolean("auto_rearm", false)) WashListenerService.rearmAfterChatLeft()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        current = WeakReference(this)
        report("Accesibilidad lista: ATENA puede buscar SD y solicitar un toque de Voz.")
    }

    private fun report(message: String) {
        getSharedPreferences("atena", MODE_PRIVATE).edit()
            .putString("last_status", "• $message").apply()
        sendBroadcast(Intent(WashListenerService.ACTION_STATUS).apply {
            setPackage(packageName)
            putExtra(WashListenerService.EXTRA_MESSAGE, message)
        })
    }

    private fun beginNavigation() {
        handler.removeCallbacks(scan)
        navigating = true
        step = Step.FIND_SD
        started = SystemClock.elapsedRealtime()
        sidebarAttempted = false
        sidebarOpenedAt = 0L
        sawActiveVoice = false
        // La propia AccessibilityService es la que inicia la actividad cuando es posible.
        val root = rootInActiveWindow
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

    private fun finish(message: String) {
        navigating = false
        handler.removeCallbacks(scan)
        report(message)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val type = event?.eventType ?: return
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            type != AccessibilityEvent.TYPE_VIEW_SCROLLED) return
        val pkg = event.packageName?.toString().orEmpty()
        if (pkg == CHATGPT) {
            chatWasOnScreen = true
            handler.removeCallbacks(rearm)
            if (navigating) {
                // Debounce: las actualizaciones de pantalla suelen llegar en ráfagas.
                if (SystemClock.elapsedRealtime() - lastScanAt > 220) schedule(250)
            } else if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
                observeVoiceEnd()
            }
        } else if (pkg != packageName && pkg != "com.android.systemui" && pkg.isNotBlank()) {
            if (chatWasOnScreen && !navigating) {
                chatWasOnScreen = false
                handler.removeCallbacks(rearm)
                handler.postDelayed(rearm, 5000)
            }
        }
    }

    /** Detectar Voz activa depende de las etiquetas reales, puede no funcionar en toda versión. */
    private fun observeVoiceEnd() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastVoiceObservationAt < 1000) return
        lastVoiceObservationAt = now
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != CHATGPT) return
        val active = findNode(root) { SdUiRules.isActiveVoiceIndicator(it) } != null
        if (active) {
            sawActiveVoice = true
            voiceEndRearmPending = false
        } else if (sawActiveVoice && !voiceEndRearmPending) {
            sawActiveVoice = false
            voiceEndRearmPending = true
            handler.postDelayed({
                voiceEndRearmPending = false
                if (!navigating) {
                    report("La interfaz de Voz parece haberse cerrado. Intentando reactivar WASH.")
                    WashListenerService.rearmAfterChatLeft()
                }
            }, 1800)
        }
    }

    private fun scanWindow() {
        if (!navigating) return
        lastScanAt = SystemClock.elapsedRealtime()
        if (lastScanAt - started > 16500) {
            finish("Tiempo agotado: no pude confirmar SD y el botón de Voz. No toqué controles desconocidos.")
            return
        }
        val root = rootInActiveWindow
        if (root?.packageName?.toString() != CHATGPT) {
            schedule(600)
            return
        }
        // No se interrumpe una sesión de Voz si la interfaz informa que está activa.
        if (findNode(root) { SdUiRules.isActiveVoiceIndicator(it) } != null) {
            sawActiveVoice = true
            finish("ChatGPT parece tener una llamada de Voz activa. ATENA no la interrumpió.")
            return
        }
        when (step) {
            Step.FIND_SD -> findSd(root)
            Step.START_VOICE -> startVoice(root)
        }
    }

    private fun findSd(root: AccessibilityNodeInfo) {
        val sd = findNode(root) { SdUiRules.isSdTitle(it) }
        if (sd != null && clickNodeOrParent(sd)) {
            step = Step.START_VOICE
            report("Chat SD seleccionado. Buscando el botón para iniciar Voz…")
            schedule(900)
            return
        }
        if (!sidebarAttempted) {
            val menu = findNode(root) { SdUiRules.isNavigationButton(it) }
            if (menu != null && clickNodeOrParent(menu)) {
                sidebarAttempted = true
                sidebarOpenedAt = SystemClock.elapsedRealtime()
                report("Abriendo historial de ChatGPT para buscar el chat fijado SD…")
                schedule(850)
                return
            }
            // Algunas versiones no exponen el botón del menú a Accesibilidad.
            // No se usan toques ciegos en pantalla.
            sidebarAttempted = true
            sidebarOpenedAt = SystemClock.elapsedRealtime()
            report("No encontré un botón de historial identificable. Buscando SD en pantalla…")
        }
        if (SystemClock.elapsedRealtime() - sidebarOpenedAt > 6000) {
            finish("No pude localizar el chat fijado «SD». Abre el historial y revisa que su nombre sea exactamente SD.")
        } else schedule(650)
    }

    private fun startVoice(root: AccessibilityNodeInfo) {
        val voiceButton = findNode(root) { SdUiRules.isStartVoiceButton(it) }
        if (voiceButton != null && clickNodeOrParent(voiceButton)) {
            finish("ATENA pulsó el control de Voz del chat SD. Comprueba si ChatGPT empezó a escuchar.")
            return
        }
        if (SystemClock.elapsedRealtime() - started > 13500) {
            finish("Entré al chat SD, pero no encontré un botón de Voz identificable. No pulsé Dictado por error.")
        } else schedule(600)
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
        if (current.get() === this) current.clear()
        super.onDestroy()
    }
}
