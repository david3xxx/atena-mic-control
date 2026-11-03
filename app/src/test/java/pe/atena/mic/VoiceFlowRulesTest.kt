package pe.atena.mic

import org.junit.Assert.*
import org.junit.Test

class VoiceFlowRulesTest {
    @Test fun noConfirmaConBarraVisible() {
        assertFalse(VoiceFlowRules.likelyStarted(2500, false, true))
    }
    @Test fun confirmaCuandoVozEsVisible() {
        assertTrue(VoiceFlowRules.likelyStarted(300, true, true))
    }
    @Test fun reintentaDespuesDeFallo() {
        assertTrue(VoiceFlowRules.retryCalibratedTap(2300, true, false))
        assertFalse(VoiceFlowRules.retryCalibratedTap(2300, true, true))
    }
    @Test fun evitaToquesPrematuros() {
        assertFalse(VoiceFlowRules.useSavedPosition(300, true, false))
        assertTrue(VoiceFlowRules.useSavedPosition(800, true, false))
    }
}
