package pe.atena.mic

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Guarda únicamente posiciones de toques normalizadas; NO captura imágenes ni contraseñas. */
object TapPatternStore {
    const val MAX_STEPS = 8
    data class Point(val x: Float, val y: Float)
    data class Pattern(val orientation: Int, val points: List<Point>)
    private const val PREFS = "atena_user_tap_patterns_v1"

    fun save(context: Context, slot: Int, pattern: Pattern) {
        require(slot in 1..3)
        require(pattern.points.size in 1..MAX_STEPS)
        val json = JSONObject()
            .put("orientation", pattern.orientation)
            .put("taps", JSONArray().apply {
                pattern.points.forEach { pt ->
                    put(JSONObject().put("x", pt.x.toDouble()).put("y", pt.y.toDouble()))
                }
            })
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("slot_$slot", json.toString()).apply()
    }

    fun load(context: Context, slot: Int): Pattern? {
        if (slot !in 1..3) return null
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("slot_$slot", null) ?: return null
        return try {
            val json = JSONObject(stored)
            val taps = json.getJSONArray("taps")
            if (taps.length() !in 1..MAX_STEPS) return null
            val points = (0 until taps.length()).map { i ->
                val p = taps.getJSONObject(i)
                Point(p.getDouble("x").toFloat(), p.getDouble("y").toFloat())
            }
            if (points.any { !it.x.isFinite() || !it.y.isFinite() || it.x !in 0f..1f || it.y !in 0f..1f }) {
                null
            } else Pattern(json.getInt("orientation"), points)
        } catch (_: Exception) { null }
    }

    fun delete(context: Context, slot: Int) {
        if (slot in 1..3) context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove("slot_$slot").apply()
    }
}
