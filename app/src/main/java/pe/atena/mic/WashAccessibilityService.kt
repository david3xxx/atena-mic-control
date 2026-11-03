package pe.atena.mic

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import java.lang.ref.WeakReference

/**
 * Servicio OPCIONAL. Solo consulta packageName de cambios de ventana: no lee contenido,
 * no captura gestos y no graba pantallas. Ofrece reactivación tentativa si ChatGPT sale.
 */
class WashAccessibilityService : AccessibilityService() {
    companion object {
        private var instance = WeakReference<WashAccessibilityService>(null)
        fun requestOpenChat(): Boolean = instance.get()?.let(ChatOpener::open) ?: false
    }
    private val handler = Handler(Looper.getMainLooper())
    private var seenChat = false
    private val rearm = Runnable {
        if (seenChat) {
            seenChat = false
            WashListenerService.rearmAfterChatLeft()
        }
    }
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = WeakReference(this)
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString().orEmpty()
        when {
            pkg == "com.openai.chatgpt" -> {
                seenChat = true
                handler.removeCallbacks(rearm)
            }
            pkg == packageName || pkg == "com.android.systemui" || pkg.isBlank() -> {
                // Ignorar diálogos propios, notificaciones y paneles del sistema.
            }
            seenChat -> {
                // Si ChatGPT sigue hablando en segundo plano, esta reactivación puede
                // competir por el micrófono. Puede desactivarse desde el menú.
                handler.removeCallbacks(rearm)
                handler.postDelayed(rearm, 6500)
            }
        }
    }
    override fun onInterrupt() { }
    override fun onDestroy() {
        handler.removeCallbacks(rearm)
        if (instance.get() === this) instance.clear()
        super.onDestroy()
    }
}
