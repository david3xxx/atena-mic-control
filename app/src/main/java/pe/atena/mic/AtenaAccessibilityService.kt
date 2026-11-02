package pe.atena.mic

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.PixelFormat
import android.graphics.RectF
import android.view.MotionEvent
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.os.Handler
import android.os.Looper
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

        fun recordPattern(slot: Int): String = current.get()?.prepareRecording(slot)
            ?: "Activa ATENA en Accesibilidad para grabar patrones."
        fun playPattern(slot: Int, delayed: Boolean = false): String = current.get()?.playSavedPattern(slot, delayed)
            ?: "Activa ATENA en Accesibilidad para ejecutar patrones."
        fun deletePattern(slot: Int): String = current.get()?.deleteSavedPattern(slot)
            ?: "Activa ATENA en Accesibilidad para borrar patrones."

        /** La cuadrícula muestra el estado también cuando ATENA está encima de otra app. */
        fun showVoiceState(message: String) {
            current.get()?.reportOnOverlay(message)
        }

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
    private val ui = Handler(Looper.getMainLooper())
    private var overlayMessage = "Inicia la escucha desde ATENA"
    private var lastTarget: PointF? = null
    private var patternOverlay: CaptureOverlay? = null
    private var pendingRecordSlot: Int? = null
    private var recordingSlot: Int? = null
    private var recorded = mutableListOf<TapPatternStore.Point>()
    private var previousGridVisible = false
    private var replaying = false
    private var replayGeneration = 0
    private var recordStart: Runnable? = null

    private fun reportOnOverlay(message: String) {
        ui.post {
            overlayMessage = message.take(80)
            overlay?.invalidate()
        }
    }

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
        lastTarget = null
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
        reportOnOverlay("Cuadrícula lista. Inicia la escucha en ATENA.")
        overlay?.invalidate()
        return "Cuadrícula visible. Para hablar, inicia la escucha en ATENA."
    }

    private fun hideGridInternal(): String {
        gridVisible = false
        firstCell = null
        lastTarget = null
        overlay?.let { try { windows.removeView(it) } catch (_: Exception) {} }
        overlay = null
        return "Cuadrícula oculta. Di 'mostrar cuadrícula' para abrirla."
    }

    private fun restartGridInternal(): String {
        if (!gridVisible) return showGridInternal()
        firstCell = null
        lastTarget = null
        reportOnOverlay("Cuadrícula reiniciada · di del uno al dieciséis")
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
            lastTarget = null
            firstCell = number
            reportOnOverlay("Casilla $number ampliada. Di otro número.")
            view.invalidate()
            return "Casilla $number ampliada. Di otro número del 1 al 16 para tocar."
        }

        val whole = GridLogic.Area(0f, 0f, view.width.toFloat(), view.height.toFloat())
        val target = GridLogic.target(whole, first, number)
        val x = target.centerX
        val y = target.centerY
        firstCell = null
        lastTarget = PointF(x, y)
        reportOnOverlay("Solicitando toque $first → $number")
        view.invalidate()

        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder().addStroke(
            GestureDescription.StrokeDescription(path, 0, 75)
        ).build()
        val accepted = try {
            dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    val message = "PULSACIÓN COMPLETADA: $first → $number"
                    broadcast(message)
                    reportOnOverlay(message)
                }
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    val message = "ANDROID CANCELÓ LA PULSACIÓN $first → $number"
                    broadcast(message)
                    reportOnOverlay(message)
                }
            }, null)
        } catch (ex: Exception) {
            return "Error al solicitar el toque: ${ex.message}"
        }
        return if (accepted) {
            "Toque solicitado en $first → $number. Cuadrícula principal restaurada."
        } else "Android rechazó el toque. Comprueba el permiso de accesibilidad."
    }

    /** Da seis segundos para salir de ATENA y abrir la pantalla que se desea grabar. */
    private fun prepareRecording(slot: Int): String {
        if (slot !in 1..3) return "Solo hay patrones 1, 2 y 3."
        if (recordingSlot != null || pendingRecordSlot != null || replaying)
            return "Termina la operación anterior antes de grabar otro patrón."
        previousGridVisible = gridVisible
        hideGridInternal()
        pendingRecordSlot = slot
        recorded.clear()
        val start = Runnable { startRecordingNow(slot) }
        recordStart = start
        ui.postDelayed(start, 6000)
        val message = "Patrón $slot: tienes 6 segundos para abrir la pantalla destino. Luego toca hasta 8 puntos. La franja superior tendrá GUARDAR y CANCELAR."
        broadcast(message)
        return message
    }

    private fun startRecordingNow(slot: Int) {
        if (pendingRecordSlot != slot) return
        pendingRecordSlot = null
        recordStart = null
        recordingSlot = slot
        val view = CaptureOverlay()
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }
        try {
            windows.addView(view, params)
            patternOverlay = view
            broadcast("GRABANDO PATRÓN $slot: toca hasta 8 posiciones; arriba GUARDAR/CANCELAR. Estos toques no ejecutan acciones mientras grabas.")
        } catch (e: Exception) {
            recordingSlot = null
            broadcast("No se pudo abrir la pantalla de grabación: ${e.message}")
            restoreGridAfterRecording()
        }
    }

    private fun finishRecording(save: Boolean): String {
        val slot = recordingSlot ?: return "No hay patrón en grabación."
        recordingSlot = null
        patternOverlay?.let { try { windows.removeView(it) } catch (_: Exception) {} }
        patternOverlay = null
        val result = if (save && recorded.isNotEmpty()) {
            try {
                TapPatternStore.save(this, slot,
                    TapPatternStore.Pattern(resources.configuration.orientation, recorded.toList()))
                "PATRÓN $slot GUARDADO: ${recorded.size} toque(s)."
            } catch (e: Exception) { "No se pudo guardar patrón: ${e.message}" }
        } else if (save) {
            "No grabaste ninguna posición. No se reemplazó el patrón anterior."
        } else "Grabación del patrón $slot cancelada."
        recorded.clear()
        restoreGridAfterRecording()
        broadcast(result)
        return result
    }

    private fun restoreGridAfterRecording() {
        if (previousGridVisible) showGridInternal()
        previousGridVisible = false
    }

    private fun deleteSavedPattern(slot: Int): String {
        if (slot !in 1..3) return "Patrón no válido."
        if (replaying || recordingSlot != null || pendingRecordSlot != null)
            return "Espera a terminar la operación actual."
        TapPatternStore.delete(this, slot)
        return "Patrón $slot borrado."
    }

    /** Al reproducir desde el menú hay seis segundos para volver a la pantalla destino. */
    private fun playSavedPattern(slot: Int, delayed: Boolean): String {
        if (slot !in 1..3) return "Patrón no válido."
        if (replaying || recordingSlot != null || pendingRecordSlot != null)
            return "ATENA está grabando o ejecutando otro patrón."
        val pattern = TapPatternStore.load(this, slot) ?: return "El patrón $slot todavía no está guardado."
        if (pattern.orientation != resources.configuration.orientation)
            return "La orientación cambió. Vuelve a la orientación en que grabaste el patrón $slot."
        replaying = true
        val run = ++replayGeneration
        val delay = if (delayed) 6000L else 300L
        ui.postDelayed({ if (replaying && replayGeneration == run) playNextPoint(slot, pattern.points, 0, run) }, delay)
        return if (delayed) "Patrón $slot: inicia en 6 segundos; abre la pantalla destino."
            else "Ejecutando patrón $slot con ${pattern.points.size} toque(s)."
    }

    private fun playNextPoint(slot: Int, points: List<TapPatternStore.Point>, index: Int, run: Int) {
        if (!replaying || replayGeneration != run) return
        if (index == points.size) {
            replaying = false
            broadcast("PATRÓN $slot TERMINADO: ${points.size} toque(s) completados.")
            return
        }
        val bounds = windows.currentWindowMetrics.bounds
        val point = points[index]
        val x = bounds.left + point.x * bounds.width()
        val y = bounds.top + point.y * bounds.height()
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder().addStroke(
            GestureDescription.StrokeDescription(path, 0, 95)
        ).build()
        val accepted = try {
            dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    ui.postDelayed({ playNextPoint(slot, points, index + 1, run) }, 600L)
                }
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    replaying = false
                    broadcast("Android canceló el toque ${index + 1} del patrón $slot.")
                }
            }, null)
        } catch (e: Exception) {
            replaying = false
            broadcast("Error ejecutando patrón $slot: ${e.message}")
            false
        }
        if (!accepted && replaying) {
            replaying = false
            broadcast("Android rechazó el toque ${index + 1} del patrón $slot.")
        }
    }

    /** Capa temporal táctil: graba coordenadas, no hace clic durante la captura. */
    private inner class CaptureOverlay : View(this@AtenaAccessibilityService) {
        private val background = Paint().apply { color = Color.argb(34, 0, 150, 240) }
        private val top = Paint().apply { color = Color.argb(245, 10, 29, 50) }
        private val font = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            textSize = 14f * resources.displayMetrics.scaledDensity
            textAlign = Paint.Align.CENTER
        }
        private val mark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.YELLOW
            style = Paint.Style.STROKE
            strokeWidth = 3f * resources.displayMetrics.density
        }
        private val barHeight get() = 82f * resources.displayMetrics.density
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), background)
            canvas.drawRect(0f, 0f, width.toFloat(), barHeight, top)
            val y = 29f * resources.displayMetrics.density
            canvas.drawText("CANCELAR", width * 0.25f, y, font)
            canvas.drawText("GUARDAR", width * 0.75f, y, font)
            canvas.drawText("Patrón ${recordingSlot ?: '-'}: ${recorded.size}/8 toques (debajo de esta barra)",
                width * 0.5f, 62f * resources.displayMetrics.density, font)
            val bounds = windows.currentWindowMetrics.bounds
            for (pt in recorded) {
                val xx = bounds.left + pt.x * bounds.width()
                val yy = bounds.top + pt.y * bounds.height()
                canvas.drawCircle(xx, yy, 13f * resources.displayMetrics.density, mark)
            }
        }
        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> return true
                MotionEvent.ACTION_UP -> {
                    if (event.y < barHeight) {
                        finishRecording(event.x >= width / 2f)
                    } else if (recorded.size < TapPatternStore.MAX_STEPS) {
                        val bounds = windows.currentWindowMetrics.bounds
                        val px = ((event.rawX - bounds.left) / bounds.width().toFloat()).coerceIn(0f, 1f)
                        val py = ((event.rawY - bounds.top) / bounds.height().toFloat()).coerceIn(0f, 1f)
                        recorded.add(TapPatternStore.Point(px, py))
                        broadcast("PATRÓN ${recordingSlot ?: '-'}: toque ${recorded.size} de 8 registrado.")
                        invalidate()
                    } else {
                        broadcast("Límite de 8 toques alcanzado; pulsa GUARDAR arriba.")
                    }
                    return true
                }
                else -> return true
            }
        }
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
        private val targetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 204, 0)
            style = Paint.Style.STROKE
            strokeWidth = 4f * resources.displayMetrics.density
        }
        private val bannerText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 13f * resources.displayMetrics.scaledDensity
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        private val bannerBackground = Paint().apply { color = Color.argb(230, 9, 32, 51) }

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
            // El último punto permanece marcado para distinguir un toque real de
            // una zona donde no existe ningún control pulsable debajo.
            lastTarget?.let {
                canvas.drawCircle(it.x, it.y, 13f * resources.displayMetrics.density, targetPaint)
            }
            val density = resources.displayMetrics.density
            val bannerHeight = 37f * density
            canvas.drawRect(0f, 0f, width.toFloat(), bannerHeight, bannerBackground)
            val maxLength = ((width / (8f * density)).toInt()).coerceAtLeast(15)
            val message = overlayMessage.replace('\n', ' ').take(maxLength)
            canvas.drawText(message, 9f * density, 23f * density, bannerText)
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
        recordStart?.let { ui.removeCallbacks(it) }
        recordStart = null
        pendingRecordSlot = null
        recordingSlot = null
        patternOverlay?.let { try { windows.removeView(it) } catch (_: Exception) {} }
        patternOverlay = null
        replaying = false
        ++replayGeneration
        hideGridInternal()
        if (current.get() === this) current.clear()
        super.onDestroy()
    }
}
