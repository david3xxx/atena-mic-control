package pe.atena.mic

/** Geometría independiente de Android: 16 casillas en dos niveles. */
object GridLogic {
    const val COLS = 4
    const val MAX_CELL = COLS * COLS

    data class Area(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        val width: Float get() = right - left
        val height: Float get() = bottom - top
        val centerX: Float get() = (left + right) / 2f
        val centerY: Float get() = (top + bottom) / 2f
    }

    fun cell(parent: Area, number: Int): Area {
        require(number in 1..MAX_CELL) { "Casilla fuera de rango" }
        val row = (number - 1) / COLS
        val column = (number - 1) % COLS
        val cw = parent.width / COLS
        val ch = parent.height / COLS
        return Area(
            parent.left + column * cw,
            parent.top + row * ch,
            parent.left + (column + 1) * cw,
            parent.top + (row + 1) * ch
        )
    }

    fun target(screen: Area, first: Int, second: Int): Area = cell(cell(screen, first), second)
}

/** Reconoce únicamente números aislados o precedidos de 'casilla/cuadro/número'. */
object GridSpeech {
    fun number(raw: String): Int? {
        val clean = java.text.Normalizer.normalize(
            raw.lowercase(), java.text.Normalizer.Form.NFD
        ).replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .trim().replace(Regex("\\s+"), " ")
        val candidate = clean.replace(
            Regex("^(casilla|cuadro|cuadrado|numero|toca|selecciona|elige) "), ""
        ).trim()
        val words = mapOf(
            "uno" to 1, "dos" to 2, "tres" to 3, "cuatro" to 4,
            "cinco" to 5, "seis" to 6, "siete" to 7, "ocho" to 8,
            "nueve" to 9, "diez" to 10, "once" to 11, "doce" to 12,
            "trece" to 13, "catorce" to 14, "quince" to 15,
            "dieciseis" to 16
        )
        return (words[candidate] ?: candidate.toIntOrNull())?.takeIf { it in 1..16 }
    }
}
