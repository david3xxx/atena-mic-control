package pe.atena.mic

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import java.lang.ref.WeakReference

/** El usuario debe habilitar expresamente este servicio en Accesibilidad. */
class AtenaAccessibilityService : AccessibilityService() {
    companion object {
        private var current = WeakReference<AtenaAccessibilityService>(null)
        fun isEnabled(): Boolean = current.get() != null
        fun perform(action: CommandRouter.Action): Boolean {
            val service = current.get() ?: return false
            return when (action) {
                CommandRouter.Action.HOME -> service.performGlobalAction(GLOBAL_ACTION_HOME)
                CommandRouter.Action.BACK -> service.performGlobalAction(GLOBAL_ACTION_BACK)
                CommandRouter.Action.NOTIFICATIONS -> service.performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
                CommandRouter.Action.SCROLL_UP -> service.swipe(up = true)
                CommandRouter.Action.SCROLL_DOWN -> service.swipe(up = false)
                else -> false
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        current = WeakReference(this)
    }

    private fun swipe(up: Boolean): Boolean {
        val dm = resources.displayMetrics
        val x = dm.widthPixels * 0.5f
        val upper = dm.heightPixels * 0.28f
        val lower = dm.heightPixels * 0.76f
        val path = Path().apply {
            moveTo(x, if (up) lower else upper)
            lineTo(x, if (up) upper else lower)
        }
        val gesture = GestureDescription.Builder().addStroke(
            GestureDescription.StrokeDescription(path, 0, 430)
        ).build()
        return dispatchGesture(gesture, null, null)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onDestroy() {
        if (current.get() === this) current.clear()
        super.onDestroy()
    }
}
