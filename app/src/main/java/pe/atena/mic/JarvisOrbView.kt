package pe.atena.mic

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import kotlin.math.min

/** Slow rotating three-tomoe-inspired blue orb, drawn natively without image/GIF overhead. */
class JarvisOrbView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cyan = Color.rgb(0, 218, 255)
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val r = min(width, height) * 0.39f
        val cx = width / 2f; val cy = height / 2f
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = r * 0.035f
        for (i in 0..3) {
            paint.color = Color.argb(210 - i * 35, 0, 220, 255)
            canvas.drawCircle(cx, cy, r * (0.62f + i * 0.13f), paint)
        }
        canvas.save()
        canvas.rotate((System.currentTimeMillis() % 24000L) * 360f / 24000f, cx, cy)
        paint.style = Paint.Style.FILL
        paint.color = cyan
        canvas.drawCircle(cx, cy, r * 0.14f, paint)
        for (i in 0..2) {
            canvas.save(); canvas.rotate(i * 120f, cx, cy)
            val path = android.graphics.Path().apply {
                moveTo(cx, cy - r * 0.55f)
                cubicTo(cx + r * 0.31f, cy - r * 0.76f, cx + r * 0.38f, cy - r * 0.34f, cx + r * 0.1f, cy - r * 0.3f)
                cubicTo(cx - r * 0.11f, cy - r * 0.23f, cx - r * 0.21f, cy - r * 0.49f, cx, cy - r * 0.55f)
                close()
            }
            canvas.drawPath(path, paint)
            canvas.restore()
        }
        canvas.restore()
        postInvalidateDelayed(60L)
    }
}
