import pe.atena.mic.*

fun main() {
    val checks = linkedMapOf(
        "1 Reconoce Wash" to WashMatcher.contains("Wash"),
        "2 Reconoce variantes" to listOf("guash", "watch", "uash", "guás").all(WashMatcher::contains),
        "3 Evita coincidencias falsas" to listOf("WhatsApp", "watching").none(WashMatcher::contains),
        "4 Identifica SD exactamente" to (SdUiRules.isSdTitle("SD") && !SdUiRules.isSdTitle("SD otra cosa")),
        "5 Evita confundir Voz con Dictado" to (SdUiRules.isStartVoiceButton("Iniciar voz") && !SdUiRules.isStartVoiceButton("Dictado por micrófono")),
        "6 No rearma durante Voz activa" to !WashRearmRules.isSafeToRearm(60000, true, true, 3),
        "7 Rearma tras volver al chat" to WashRearmRules.isSafeToRearm(6000, false, true, 2),
        "8 Si la barra sigue visible, no confirma inicio de Voz" to !VoiceFlowRules.likelyStarted(2500,false,true),
        "9 Reintenta con calibración sin repetir dos veces" to (VoiceFlowRules.retryCalibratedTap(1900, true, false) && !VoiceFlowRules.retryCalibratedTap(2300,true,true)),
        "10 Usa coordenadas solo al estabilizar y detiene intentos agotados" to (!VoiceFlowRules.useSavedPosition(300,true,false) && VoiceFlowRules.useSavedPosition(800,true,false) && VoiceFlowRules.timedOut(3900))
    )
    checks.forEach { (label, pass) -> println("${if (pass) "APROBADA" else "FALLIDA"} | $label") }
    val passed = checks.values.count { it }
    println("TOTAL: $passed/${checks.size} simulaciones de reglas (JVM)")
    require(passed == checks.size) { "Al menos una comprobación falló" }
}
