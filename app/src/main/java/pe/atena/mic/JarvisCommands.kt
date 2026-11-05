package pe.atena.mic

import java.text.Normalizer
import java.util.Locale

/** Parses final Android speech-recognition hypotheses. Does not claim acoustic wake-word detection. */
object JarvisCommands {
    sealed class Command {
        data object Open : Command()
        data object Exit : Command()
        data class Volume(val kind: String, val percent: Int = 5) : Command()
    }
    private val wake = setOf("jarvis", "jarvís", "yarvis", "yervis", "javis", "yarvís")
    private fun norm(s: String): String = Normalizer.normalize(s.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("[^a-z0-9% ]"), " ").replace(Regex("\\s+"), " ").trim()
    fun parse(options: List<String>): Command? {
        for (raw in options) {
            val text = norm(raw)
            val tokens = text.split(' ')
            val wakeAt = tokens.indexOfFirst { it in wake.map(::norm) }
            if (wakeAt < 0) continue
            val tail = tokens.drop(wakeAt + 1).joinToString(" ")
            if (Regex("^(fuera|apagate|apagar|cerrar|salir)( |$)").containsMatchIn(tail)) return Command.Exit
            if (Regex("^(silencio|silenciar|mute)( |$)").containsMatchIn(tail)) return Command.Volume("mute", 0)
            val percent = Regex("(\\d{1,3})\\s*%?").find(tail)?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(0, 100)
                ?: if (tail.contains("cinco")) 5 else if (tail.contains("diez")) 10 else 5
            if (tail.contains("sube") || tail.contains("subir") || tail.contains("aumenta")) return Command.Volume("up", percent)
            if (tail.contains("baja") || tail.contains("bajar") || tail.contains("reduce")) return Command.Volume("down", percent)
            if (tail.contains("volumen al") || tail.contains("volumen a ")) return Command.Volume("set", percent)
            if (tail.isBlank()) return Command.Open
        }
        return null
    }
}
