import pe.atena.mic.WashMatcher
import pe.atena.mic.WashRearmRules
import pe.atena.mic.SdUiRules

fun main() {
    val checks = linkedMapOf<String, Boolean>()
    checks["1 · Activación con WASH en español"] = WashMatcher.contains("¡Wash, abre ChatGPT!")
    checks["2 · Transcripciones fonéticas aproximadas"] = listOf("guás", "guach", "watch", "uash", "cuash", "was").all(WashMatcher::contains)
    checks["3 · Rechazo de falsas coincidencias largas"] = listOf("WhatsApp", "Washington", "watching", "Lávate las manos").none(WashMatcher::contains)
    checks["4 · Navegación lógica SD y botón de Voz"] = SdUiRules.isSdTitle("SD") && !SdUiRules.isSdTitle("SD otro chat") && SdUiRules.isStartVoiceButton("Iniciar voz") && !SdUiRules.isStartVoiceButton("Dictado por micrófono")
    checks["5 · No reactivar micrófono si Voz sigue activa"] = !WashRearmRules.isSafeToRearm(60_000L, true, true, 3)
    checks["6 · Rearme y segundo ciclo WASH tras cierre"] = !WashRearmRules.isSafeToRearm(6_000L, false, true, 1) && WashRearmRules.isSafeToRearm(6_000L, false, true, 2) && WashRearmRules.shouldRearmAfterExit(true, 2) && WashMatcher.containsAny(listOf("guash", "prueba"))
    checks.forEach { (name, ok) -> println("${if(ok) "APROBADA" else "FALLIDA"} | $name") }
    val total = checks.size
    val passed = checks.values.count { it }
    println("TOTAL: $passed/$total simulaciones de lógica aprobadas")
    check(passed == total) { "Fallaron ${total-passed} simulaciones" }
}
