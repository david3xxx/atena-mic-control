from pathlib import Path
base=Path('/mnt/data/atena_v16_work/app/src/main/java/pe/atena/mic')
p=base/'WashAccessibilityService.kt'
s=p.read_text()
s=s.replace('private enum class Step { FIND_SD, START_VOICE }','private enum class Step { FIND_SD, START_VOICE, CONFIRM_VOICE }')
s=s.replace('''    private var attemptedSavedTap = false''','''    private var attemptedSavedTap = false
    private var voiceTapFromCalibration = false''')
s=s.replace('''        attemptedSavedTap = false
        handler.removeCallbacks(scan)''','''        attemptedSavedTap = false
        voiceTapFromCalibration = false
        handler.removeCallbacks(scan)''')
s=s.replace('''            Step.FIND_SD -> findSd(root)
            Step.START_VOICE -> startVoice(root)''','''            Step.FIND_SD -> findSd(root)
            Step.START_VOICE -> startVoice(root)
            Step.CONFIRM_VOICE -> confirmVoice(root)''')
s=s.replace('''        val sd = findNode(root) { SdUiRules.isSdTitle(it) }
        if (sd != null) {''','''        val sd = findNode(root) { SdUiRules.isSdTitle(it) }
        val pinnedSectionOpen = findNode(root) { SdUiRules.isPinnedHeading(it) } != null
        if (sd != null) {''')
s=s.replace('''            if (!sidebarAttempted && composerVisible(root)) {''','''            if (!pinnedSectionOpen && !sidebarAttempted && composerVisible(root)) {''')
s=s.replace('''        if (voiceButton != null && clickNodeOrParent(voiceButton)) {
            voiceButtonPressedAt = SystemClock.elapsedRealtime()
            chatSeenDuringPause = true
            finish("ETAPA VOZ: toque solicitado en el botón accesible. Comprobando si inicia Voz…",
                keepMicReleased = true)
            return
        }''','''        if (voiceButton != null && clickNodeOrParent(voiceButton)) {
            beginVoiceConfirmation("botón accesible")
            return
        }''')
anchor='''    /** El usuario elige la posición. No pulsar coordenadas predeterminadas o adivinadas. */'''
assert anchor in s
new='''    /** Una pulsación solicitada no prueba que Voz haya iniciado: verificar el nuevo estado. */
    private fun beginVoiceConfirmation(source: String) {
        step = Step.CONFIRM_VOICE
        stepStartedAt = SystemClock.elapsedRealtime()
        voiceButtonPressedAt = stepStartedAt
        chatSeenDuringPause = true
        report("ETAPA VOZ: pulsación solicitada ($source); verificando respuesta de ChatGPT…")
        schedule(550)
    }

    private fun confirmVoice(root: AccessibilityNodeInfo) {
        val elapsed = SystemClock.elapsedRealtime() - stepStartedAt
        val active = findNode(root) { SdUiRules.isActiveVoiceIndicator(it) } != null
        val composer = composerVisible(root)
        when (VoiceNavigationPolicy.evaluate(active, composer, elapsed)) {
            VoiceNavigationPolicy.Observation.ACTIVE -> {
                sawActiveVoice = true
                finish("Voz activa detectada en ChatGPT. ATENA espera a que termine.",
                    keepMicReleased = true)
            }
            VoiceNavigationPolicy.Observation.WAIT -> schedule(350)
            VoiceNavigationPolicy.Observation.READY -> {
                if (!voiceTapFromCalibration &&
                    getSharedPreferences("atena", MODE_PRIVATE).getBoolean("voice_calibrated", false)) {
                    voiceTapFromCalibration = true
                    report("El chat sigue mostrando el editor. Intentando el botón calibrado una vez…")
                    if (!tapCalibratedVoice()) {
                        finish("Voz no confirmada: falló el toque calibrado; ATENA recuperará WASH.")
                    }
                } else {
                    finish("No se abrió Voz: sigue visible el cuadro para escribir. Reactivando WASH.")
                }
            }
            VoiceNavigationPolicy.Observation.UNKNOWN -> {
                // Sin editor ni etiquetas no se puede distinguir un cambio de interfaz
                // de Voz que ya comenzó. No repetir toques para no colgar la llamada.
                finish("El editor desapareció; posible inicio de Voz (no verificable).",
                    keepMicReleased = true)
            }
        }
    }

'''
s=s.replace(anchor,new+anchor)
s=s.replace('''                voiceButtonPressedAt = SystemClock.elapsedRealtime()
                chatSeenDuringPause = true
                finish("ETAPA VOZ: pulsación calibrada completada; comprueba si Voz comenzó.",
                    keepMicReleased = true)''','''                if (navigating) beginVoiceConfirmation("posición calibrada")''')
s=s.replace('''                finish("ETAPA VOZ: Android canceló la pulsación calibrada.")''','''                if (navigating) finish("ETAPA VOZ: Android canceló la pulsación calibrada.")''')
p.write_text(s)
p=base/'SdUiRules.kt';s=p.read_text();anchor='''    fun isNavigationButton(value: CharSequence?): Boolean {''';assert anchor in s
s=s.replace(anchor,'''    fun isPinnedHeading(value: CharSequence?): Boolean =
        normalized(value) in setOf("fijados", "pinned", "pinned chats", "pinned conversations")

'''+anchor);p.write_text(s)
(base/'VoiceNavigationPolicy.kt').write_text('''package pe.atena.mic

/** Estado posterior al toque, no confundir gesto solicitado con llamada iniciada. */
object VoiceNavigationPolicy {
    enum class Observation { ACTIVE, WAIT, READY, UNKNOWN }

    fun evaluate(voiceActive: Boolean, editorVisible: Boolean, millisecondsSinceTap: Long): Observation {
        if (voiceActive) return Observation.ACTIVE
        if (millisecondsSinceTap < 3800L) return Observation.WAIT
        return if (editorVisible) Observation.READY else Observation.UNKNOWN
    }
}
''')
tests=Path('/mnt/data/atena_v16_work/app/src/test/java/pe/atena/mic')
(tests/'VoiceNavigationPolicyTest.kt').write_text('''package pe.atena.mic

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceNavigationPolicyTest {
    @Test fun requiresTimeBeforeJudgingFailedTouch() {
        assertEquals(VoiceNavigationPolicy.Observation.WAIT,
            VoiceNavigationPolicy.evaluate(false, true, 1600))
        assertEquals(VoiceNavigationPolicy.Observation.READY,
            VoiceNavigationPolicy.evaluate(false, true, 4000))
    }
    @Test fun activeCallTakesPriority() {
        assertEquals(VoiceNavigationPolicy.Observation.ACTIVE,
            VoiceNavigationPolicy.evaluate(true, true, 5000))
    }
    @Test fun noEditorAndNoIndicatorIsUnknown() {
        assertEquals(VoiceNavigationPolicy.Observation.UNKNOWN,
            VoiceNavigationPolicy.evaluate(false, false, 4500))
    }
}
''')
p=tests/'SdUiRulesTest.kt';s=p.read_text().replace('''    @Test fun sidebarSpanishAndEnglish() {''','''    @Test fun pinnedSectionIsNotChatTitle() {
        assertTrue(SdUiRules.isPinnedHeading("Fijados"))
        assertTrue(SdUiRules.isPinnedHeading("Pinned"))
        assertFalse(SdUiRules.isPinnedHeading("SD"))
    }
    @Test fun sidebarSpanishAndEnglish() {''');p.write_text(s)
print('V16 adjusted and policy test added')
