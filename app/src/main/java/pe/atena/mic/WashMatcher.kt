package pe.atena.mic

import java.text.Normalizer
import java.util.Locale

/**
 * Palabra de activación «WASH» y transcripciones fonéticas frecuentes en español.
 * No hace detección acústica: compara hipótesis del motor de voz Android.
 * Solo compara palabras COMPLETAS para evitar activar con WhatsApp o Washington.
 */
object WashMatcher {
    private val variants = setOf(
        "wash", "guash", "wosh", "guach", "guas", "huash", "uash",
        "wach", "watch", "cuash", "cuach", "guosh", "guoch", "wats",
        "whash", "wasch", "was", "waz", "guaz", "wachi", "guachi"
    )

    private fun tokens(text: String): List<String> {
        val normalized = Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return Regex("[a-z]+", RegexOption.IGNORE_CASE).findAll(normalized)
            .map { it.value }.toList()
    }

    fun contains(text: String, extraAliases: Set<String> = emptySet()): Boolean {
        val aliases = extraAliases.flatMap(::tokens).filter { it.length >= 3 }.toSet()
        return tokens(text).any { it in variants || it in aliases }
    }

    fun containsAny(options: List<String>, extraAliases: Set<String> = emptySet()): Boolean =
        options.any { contains(it, extraAliases) }

    /** Solo palabras individuales: no se aceptan frases como alias para prevenir activaciones erróneas. */
    fun parseAliases(csv: String): Set<String> = csv.split(',', ';', '\n')
        .mapNotNull { entry -> tokens(entry.trim()).singleOrNull()?.takeIf { it.length >= 3 } }
        .take(12).toSet()
}
