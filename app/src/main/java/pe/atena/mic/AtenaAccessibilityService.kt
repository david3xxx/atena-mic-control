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
import android.os.SystemClock
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
    private var countdownOverlay: CountdownOverlay? = null
    private var playbackOverlay: PlaybackOverlay? = null
    private var countdownTick: Runnable? = null
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

    /** 4 segundos para abrir la app; durante la espera la capa NO bloquea la pantalla. */
    private fun prepareRecording(slot: Int): String {
        if (slot !in 1..3) return "Solo hay patrones 1, 2 y 3."
        if (recordingSlot != null || pendingRecordSlot != null || replaying)
            return "Primero termina la grabación o reproducción anterior."
        previousGridVisible = gridVisible
        hideGridInternal()
        pendingRecordSlot = slot
        recorded.clear()
        val countdown = CountdownOverlay()
        try {
            windows.addView(countdown, floatingParams(touchable = false))
            countdownOverlay = countdown
        } catch (e: Exception) {
            pendingRecordSlot = null
            restoreGridAfterRecording()
            return "Android no permitió mostrar la cuenta atrás: ${e.message}"
        }
        val startedAt = SystemClock.uptimeMillis()
        val tick = object : Runnable {
            override fun run() {
                if (pendingRecordSlot != slot) return
                val elapsed = SystemClock.uptimeMillis() - startedAt
                val secondsLeft = (4 - (elapsed / 1000L).toInt()).coerceIn(0, 4)
                countdown.secondsLeft = secondsLeft
                countdown.invalidate()
                if (elapsed < PatternRules.PREPARE_MILLIS) {
                    ui.postDelayed(this, 100L)
                }
            }
        }
        countdownTick = tick
        ui.post(tick)
        val start = Runnable { startRecordingNow(slot) }
        recordStart = start
        ui.postDelayed(start, PatternRules.PREPARE_MILLIS)
        val message = "Patrón $slot: tienes 4 segundos para llegar a la pantalla. Luego marca entre 2 y 4 puntos amarillos y toca el círculo blanco para GUARDAR."
        broadcast(message)
        return message
    }

    private fun floatingParams(touchable: Boolean): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                (if (touchable) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE),
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }

    private fun removeCountdown() {
        countdownTick?.let { ui.removeCallbacks(it) }
        countdownTick = null
        countdownOverlay?.let { try { windows.removeView(it) } catch (_: Exception) {} }
        countdownOverlay = null
    }

    private fun startRecordingNow(slot: Int) {
        if (pendingRecordSlot != slot) return
        pendingRecordSlot = null
        recordStart = null
        removeCountdown()
        val view = CaptureOverlay()
        try {
            windows.addView(view, floatingParams(touchable = true))
            patternOverlay = view
            recordingSlot = slot
            broadcast("GRABACIÓN ACTIVA: toca 2 a 4 posiciones. Verás círculos AMARILLOS. Círculo BLANCO = GUARDAR.")
        } catch (e: Exception) {
            recordingSlot = null
            broadcast("No se pudo iniciar la captura de toques: ${e.message}")
            restoreGridAfterRecording()
        }
    }

    private fun finishRecording(save: Boolean): String {
        val slot = recordingSlot ?: return "No hay patrón en grabación."
        if (save && !PatternRules.canSave(recorded.size)) {
            val message = "Necesitas entre 2 y 4 puntos. Llevas ${recorded.size}; falta ${PatternRules.MIN_STEPS - recorded.size} como mínimo."
            broadcast(message)
            patternOverlay?.showMessage(message)
            return message
        }
        recordingSlot = null
        patternOverlay?.let { try { windows.removeView(it) } catch (_: Exception) {} }
        patternOverlay = null
        val result = if (save) {
            try {
                TapPatternStore.save(this, slot,
                    TapPatternStore.Pattern(resources.configuration.orientation, recorded.toList()))
                "PATRÓN $slot GUARDADO: ${recorded.size} círculos amarillos."
            } catch (e: Exception) { "No se pudo guardar el patrón $slot: ${e.message}" }
        } else "Grabación del patrón $slot cancelada. No se reemplazó lo anterior."
        recorded.clear()
        restoreGridAfterRecording()
        broadcast(result)
        reportOnOverlay(result)
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

    /** Al reproducir desde el menú hay seis segundos para regresar a la pantalla elegida. */
    private fun playSavedPattern(slot: Int, delayed: Boolean): String {
        if (slot !in 1..3) return "Patrón no válido."
        if (replaying || recordingSlot != null || pendingRecordSlot != null)
            return "ATENA está grabando o ejecutando otro patrón."
        val pattern = TapPatternStore.load(this, slot)
            ?: return "Patrón $slot no guardado o antiguo. Grábalo otra vez con 2 a 4 puntos."
        if (pattern.orientation != resources.configuration.orientation)
            return "La orientación cambió. Usa la misma orientación de la grabación."
        replaying = true
        val run = ++replayGeneration
        val delay = if (delayed) 6000L else 300L
        ui.postDelayed({ if (replaying && replayGeneration == run) playNextPoint(slot, pattern.points, 0, run) }, delay)
        return if (delayed) "Patrón $slot: comienza en 6 segundos. Vuelve a la pantalla de destino."
            else "Ejecutando patrón $slot: ${pattern.points.size} toques."
    }

    private fun showPlaybackTarget(slot: Int, index: Int, total: Int, x: Float, y: Float) {
        val view = playbackOverlay ?: PlaybackOverlay().also {
            try {
                windows.addView(it, floatingParams(touchable = false))
                playbackOverlay = it
            } catch (e: Exception) { broadcast("No se pudo mostrar el círculo amarillo: ${e.message}") }
        }
        view.displayTarget = PointF(x, y)
        view.stepLabel = "Patrón $slot · Toque ${index + 1}/$total"
        view.invalidate()
    }

    private fun removePlaybackOverlay() {
        playbackOverlay?.let { try { windows.removeView(it) } catch (_: Exception) {} }
        playbackOverlay = null
    }

    private fun finishPlayback(message: String) {
        replaying = false
        removePlaybackOverlay()
        broadcast(message)
        reportOnOverlay(message)
    }

    private fun playNextPoint(slot: Int, points: List<TapPatternStore.Point>, index: Int, run: Int) {
        if (!replaying || replayGeneration != run) return
        if (index == points.size) {
            finishPlayback("PATRÓN $slot FINALIZADO: ${points.size} pulsaciones enviadas a Android.")
            return
        }
        val bounds = windows.currentWindowMetrics.bounds
        val point = points[index]
        val x = bounds.left + point.x * bounds.width()
        val y = bounds.top + point.y * bounds.height()
        showPlaybackTarget(slot, index, points.size, x, y)
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder().addStroke(
            GestureDescription.StrokeDescription(path, 0, 95)
        ).build()
        val accepted = try {
            dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    ui.postDelayed({ playNextPoint(slot, points, index + 1, run) }, 650L)
                }
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    finishPlayback("Android canceló la pulsación ${index + 1} del patrón $slot.")
                }
            }, null)
        } catch (e: Exception) {
            finishPlayback("Error en patrón $slot: ${e.message}")
            false
        }
        if (!accepted && replaying) finishPlayback("Android rechazó el toque ${index + 1} del patrón $slot.")
    }

    /** Círculo amarillo de cuenta atrás, sin interceptar la navegación. */
    private inner class CountdownOverlay : View(this@AtenaAccessibilityService) {
        var secondsLeft = 4
        private val yellow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 216, 0) }
        private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 30f * resources.displayMetrics.scaledDensity
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 216, 0)
            textSize = 15f * resources.displayMetrics.scaledDensity
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
            setShadowLayer(3f, 1f, 1f, Color.BLACK)
        }
        override fun onDraw(canvas: Canvas) {
            val den = resources.displayMetrics.density
            val cx = width * .5f
            val cy = 85f * den
            canvas.drawCircle(cx, cy, 34f * den, yellow)
            canvas.drawText(secondsLeft.toString(), cx,
                cy - (numberPaint.ascent() + numberPaint.descent()) / 2f, numberPaint)
            canvas.drawText("ABRE LA PANTALLA DESTINO", cx, cy + 62f * den, textPaint)
        }
    }

    /** Capa transparente táctil: hasta 4 círculos AMARILLOS; el círculo BLANCO finaliza. */
    private inner class CaptureOverlay : View(this@AtenaAccessibilityService) {
        private val density = resources.displayMetrics.density
        private val yellow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 215, 0) }
        private val yellowOutline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 215, 0)
            style = Paint.Style.STROKE
            strokeWidth = 4f * density
        }
        private val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        private val black = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(21, 28, 40) }
        private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            textSize = 13f * resources.displayMetrics.scaledDensity
            textAlign = Paint.Align.CENTER
            setShadowLayer(4f, 1f, 1f, Color.BLACK)
        }
        private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            textSize = 13f * resources.displayMetrics.scaledDensity
            textAlign = Paint.Align.CENTER
        }
        private val bar = Paint().apply { color = Color.argb(155, 10, 26, 48) }
        private var customMessage = "Toca entre 2 y 4 puntos"
        private val finishX get() = width - 52f * density
        private val cancelX get() = 52f * density
        private val buttonY get() = 68f * density
        private val buttonRadius get() = 29f * density

        fun showMessage(msg: String) {
            customMessage = msg
            invalidate()
        }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val barHeight = 116f * density
            canvas.drawRect(0f, 0f, width.toFloat(), barHeight, bar)
            // CANCELAR = círculo amarillo; GUARDAR = único círculo blanco.
            canvas.drawCircle(cancelX, buttonY, buttonRadius, yellow)
            canvas.drawText("×", cancelX, buttonY - (label.ascent() + label.descent()) / 2f, label)
            canvas.drawCircle(finishX, buttonY, buttonRadius, white)
            canvas.drawText("✓", finishX, buttonY - (label.ascent() + label.descent()) / 2f, label)
            canvas.drawText("CANCELAR", cancelX, 111f * density, text)
            canvas.drawText("GUARDAR", finishX, 111f * density, text)
            canvas.drawText("PATRÓN ${recordingSlot ?: '-'} · ${recorded.size}/4", width/2f, 45f*density, text)
            canvas.drawText(customMessage.take(40), width/2f, 87f*density, text)
            // Coordenadas locales del mismo overlay táctil; cada marca es un círculo amarillo.
            for ((index, pt) in recorded.withIndex()) {
                val bounds = windows.currentWindowMetrics.bounds
                val screenX = bounds.left + pt.x * bounds.width()
                val screenY = bounds.top + pt.y * bounds.height()
                val loc = IntArray(2)
                getLocationOnScreen(loc)
                val localX = screenX - loc[0]
                val localY = screenY - loc[1]
                canvas.drawCircle(localX, localY, 20f*density, yellowOutline)
                canvas.drawCircle(localX, localY, 13f*density, yellow)
                canvas.drawText("${index+1}", localX,
                    localY - (label.ascent()+label.descent())/2f, label)
            }
        }
        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) return true
            if (event.actionMasked != MotionEvent.ACTION_UP) return true
            val dxFinish = event.x - finishX
            val dxCancel = event.x - cancelX
            val dy = event.y - buttonY
            val hitRadius = 41f * density
            if (dxFinish*dxFinish + dy*dy <= hitRadius*hitRadius) {
                finishRecording(save = true)
                return true
            }
            if (dxCancel*dxCancel + dy*dy <= hitRadius*hitRadius) {
                finishRecording(save = false)
                return true
            }
            // La cabecera jamás se memoriza como pulsación por error.
            if (event.y <= 116f*density) return true
            if (!PatternRules.canAdd(recorded.size)) {
                showMessage("Máximo 4 puntos. Toca círculo blanco")
                return true
            }
            val pos = IntArray(2)
            getLocationOnScreen(pos)
            val screenX = pos[0] + event.x
            val screenY = pos[1] + event.y
            val bounds = windows.currentWindowMetrics.bounds
            val px = ((screenX - bounds.left) / bounds.width().toFloat()).coerceIn(0f, 1f)
            val py = ((screenY - bounds.top) / bounds.height().toFloat()).coerceIn(0f, 1f)
            recorded.add(TapPatternStore.Point(px, py))
            customMessage = if (recorded.size >= PatternRules.MIN_STEPS)
                "Ya puedes pulsar el círculo blanco" else "Falta por lo menos un punto"
            invalidate()
            broadcast("PATRÓN ${recordingSlot ?: '-'}: ${recorded.size}/4 puntos marcados.")
            return true
        }
    }

    /** Indicador amarillo durante la reproducción; no bloquea las pulsaciones enviadas. */
    private inner class PlaybackOverlay : View(this@AtenaAccessibilityService) {
        var displayTarget: PointF? = null
        var stepLabel = ""
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 216, 0)
            style = Paint.Style.STROKE
            strokeWidth = 5f * resources.displayMetrics.density
        }
        private val message = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 216, 0)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            textSize = 15f * resources.displayMetrics.scaledDensity
            textAlign = Paint.Align.CENTER
            setShadowLayer(3f, 1f, 1f, Color.BLACK)
        }
        override fun onDraw(canvas: Canvas) {
            val den = resources.displayMetrics.density
            displayTarget?.let { target ->
                val loc = IntArray(2)
                getLocationOnScreen(loc)
                canvas.drawCircle(target.x - loc[0], target.y - loc[1], 23f*den, ring)
            }
            canvas.drawText(stepLabel, width * .5f, 90f * den, message)
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
        removeCountdown()
        removePlaybackOverlay()
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
