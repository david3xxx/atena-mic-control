package pe.atena.mic

import java.text.Normalizer
import java.util.Locale

/** Palabra de activación. No se aceptan coincidencias dentro de otras palabras. */
object WashMatcher {
    private val accepted = setOf("wash", "guash", "wosh", "guach", "guas", "huash", "uash", "wach", "watch")
    fun contains(text: String): Boolean {
        val normalized = Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return Regex("[a-z]+", RegexOption.IGNORE_CASE).findAll(normalized)
            .any { it.value in accepted }
    }
    fun containsAny(options: List<String>): Boolean = options.any(::contains)
}
