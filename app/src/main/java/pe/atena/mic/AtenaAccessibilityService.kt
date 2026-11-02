package pe.atena.mic

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import java.lang.ref.WeakReference

/** Cuadrícula de dos niveles; no lee ni almacena el contenido de la pantalla. */
class AtenaAccessibilityService : AccessibilityService() {
    companion object {
        private var current = WeakReference<AtenaAccessibilityService>(null)

        fun isEnabled(): Boolean = current.get() != null
        fun isGridVisible(): Boolean = current.get()?.gridVisible == true
        fun showGrid(): String = current.get()?.showGridInternal()
            ?: "Activa ATENA en Accesibilidad para mostrar la cuadrícula."
        fun hideGrid(): String = current.get()?.hideGridInternal()
            ?: "El servicio de accesibilidad no está activado."
        fun restartGrid(): String = current.get()?.restartGridInternal()
            ?: "El servicio de accesibilidad no está activado."
        fun backGrid(): String = current.get()?.backGridInternal()
            ?: "El servicio de accesibilidad no está activado."
        fun selectCell(number: Int): String = current.get()?.selectCellInternal(number)
            ?: "Activa ATENA en Accesibilidad para seleccionar casillas."

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

    private lateinit var windows: WindowManager
    private var overlay: GridOverlay? = null
    private var gridVisible = false
    private var firstCell: Int? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        current = WeakReference(this)
        windows = getSystemService(WINDOW_SERVICE) as WindowManager
        showGridInternal() // aparece directamente, sin comando adicional
    }

    private fun broadcast(message: String) {
        sendBroadcast(android.content.Intent(VoiceCaptureService.ACTION_STATUS).apply {
            setPackage(packageName)
            putExtra(VoiceCaptureService.EXTRA_MESSAGE, message)
        })
    }

    private fun showGridInternal(): String {
        gridVisible = true
        firstCell = null
        if (overlay == null) {
            val view = GridOverlay()
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.TOP or Gravity.START }
            try {
                windows.addView(view, params)
                overlay = view
            } catch (ex: Exception) {
                gridVisible = false
                return "No se pudo mostrar cuadrícula: ${ex.message}"
            }
        }
        overlay?.invalidate()
        return "Cuadrícula visible: di un número del 1 al 16."
    }

    private fun hideGridInternal(): String {
        gridVisible = false
        firstCell = null
        overlay?.let { try { windows.removeView(it) } catch (_: Exception) {} }
        overlay = null
        return "Cuadrícula oculta. Di 'mostrar cuadrícula' para abrirla."
    }

    private fun restartGridInternal(): String {
        if (!gridVisible) return showGridInternal()
        firstCell = null
        overlay?.invalidate()
        return "Cuadrícula principal: elige del 1 al 16."
    }

    private fun backGridInternal(): String {
        if (!gridVisible) return "La cuadrícula está oculta."
        firstCell = null
        overlay?.invalidate()
        return "Regresaste a la cuadrícula principal."
    }

    private fun selectCellInternal(number: Int): String {
        if (!gridVisible) return "Primero di 'mostrar cuadrícula'."
        if (number !in 1..16) return "Elige una casilla entre 1 y 16."
        val view = overlay ?: return "Cuadrícula no disponible."
        if (view.width <= 0 || view.height <= 0) return "Espera a que aparezca la cuadrícula."

        val first = firstCell
        if (first == null) {
            firstCell = number
            view.invalidate()
            return "Casilla $number ampliada. Di otro número del 1 al 16 para tocar."
        }

        val whole = GridLogic.Area(0f, 0f, view.width.toFloat(), view.height.toFloat())
        val target = GridLogic.target(whole, first, number)
        val x = target.centerX
        val y = target.centerY
        firstCell = null
        view.invalidate()

        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder().addStroke(
            GestureDescription.StrokeDescription(path, 0, 75)
        ).build()
        val accepted = try {
            dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    broadcast("Toque confirmado: casilla $first, subcasilla $number.")
                }
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    broadcast("Android canceló el toque. Intenta nuevamente.")
                }
            }, null)
        } catch (ex: Exception) {
            return "Error al solicitar el toque: ${ex.message}"
        }
        return if (accepted) {
            "Toque solicitado en $first → $number. Cuadrícula principal restaurada."
        } else "Android rechazó el toque. Comprueba el permiso de accesibilidad."
    }

    private inner class GridOverlay : View(this@AtenaAccessibilityService) {
        private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(44, 175, 245)
            style = Paint.Style.STROKE
            strokeWidth = 2.3f * resources.displayMetrics.density
        }
        private val bigStroke = Paint(stroke).apply { strokeWidth *= 2f }
        private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
            textSize = 19f * resources.displayMetrics.scaledDensity
        }
        private val labelBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(210, 14, 38, 61)
            style = Paint.Style.FILL
        }
        private val dim = Paint().apply { color = Color.argb(40, 0, 0, 0) }
        private val highlighted = Paint().apply { color = Color.argb(42, 0, 160, 255) }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (!gridVisible || width <= 0 || height <= 0) return
            val area = GridLogic.Area(0f, 0f, width.toFloat(), height.toFloat())
            val selected = firstCell
            if (selected == null) {
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dim)
                drawCells(canvas, area, false)
            } else {
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dim)
                val zoom = GridLogic.cell(area, selected)
                canvas.drawRect(zoom.left, zoom.top, zoom.right, zoom.bottom, highlighted)
                canvas.drawRect(zoom.left, zoom.top, zoom.right, zoom.bottom, bigStroke)
                drawCells(canvas, zoom, true)
            }
        }

        private fun drawCells(canvas: Canvas, area: GridLogic.Area, compact: Boolean) {
            val density = resources.displayMetrics.density
            for (number in 1..16) {
                val cell = GridLogic.cell(area, number)
                canvas.drawRect(cell.left, cell.top, cell.right, cell.bottom, stroke)
                val radius = if (compact) 13f * density else 17f * density
                val tx = cell.centerX
                val ty = cell.centerY
                canvas.drawRoundRect(
                    RectF(tx - radius * 1.25f, ty - radius,
                        tx + radius * 1.25f, ty + radius),
                    7f * density, 7f * density, labelBg
                )
                val textSize = if (compact) 15f else 19f
                label.textSize = textSize * resources.displayMetrics.scaledDensity
                canvas.drawText(number.toString(), tx,
                    ty - (label.ascent() + label.descent()) / 2f, label)
            }
        }
    }

    private fun swipe(up: Boolean): Boolean {
        val dm = resources.displayMetrics
        val x = dm.widthPixels * 0.5f
        val top = dm.heightPixels * 0.28f
        val bottom = dm.heightPixels * 0.76f
        val path = Path().apply {
            moveTo(x, if (up) bottom else top)
            lineTo(x, if (up) top else bottom)
        }
        val gesture = GestureDescription.Builder().addStroke(
            GestureDescription.StrokeDescription(path, 0, 430)
        ).build()
        return dispatchGesture(gesture, null, null)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onDestroy() {
        hideGridInternal()
        if (current.get() === this) current.clear()
        super.onDestroy()
    }
}
