package pe.atena.mic

import java.text.Normalizer

/** Comandos exactos: evita ejecutar toques por menciones casuales de números. */
object PatternSpeech {
    fun slot(raw: String): Int? {
        val clean = Normalizer.normalize(raw.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .trim().replace(Regex("\\s+"), " ")
        val phrase = clean.replace(
            Regex("^(ejecuta|ejecutar|reproduce|reproducir|activa|activar|haz|hacer|inicia|iniciar) "), ""
        ).trim()
        val match = Regex("^patron (uno|dos|tres|1|2|3|a|b|c)$").matchEntire(phrase)
            ?: return null
        return when (match.groupValues[1]) {
            "uno", "1", "a" -> 1
            "dos", "2", "b" -> 2
            "tres", "3", "c" -> 3
            else -> null
        }
    }
}
