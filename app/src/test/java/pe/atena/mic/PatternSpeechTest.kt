package pe.atena.mic

import org.junit.Assert.*
import org.junit.Test

class PatternSpeechTest {
    @Test fun recognizesNamesInSpanish() {
        assertEquals(1, PatternSpeech.slot("patrón uno"))
        assertEquals(2, PatternSpeech.slot("patron dos"))
        assertEquals(3, PatternSpeech.slot("Patrón TRES"))
        assertEquals(1, PatternSpeech.slot("ejecutar patrón 1"))
        assertEquals(2, PatternSpeech.slot("reproducir patron B"))
        assertEquals(3, PatternSpeech.slot("activar patrón c"))
    }
    @Test fun ignoresAmbiguousExpressions() {
        assertNull(PatternSpeech.slot("patrón X"))
        assertNull(PatternSpeech.slot("quiero mostrarte un patrón de tres toques"))
        assertNull(PatternSpeech.slot("patrón cuatro"))
        assertNull(PatternSpeech.slot("dieciséis"))
    }
}
