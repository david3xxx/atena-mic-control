package pe.atena.mic

import org.junit.Assert.*
import org.junit.Test

class WashMatcherTest {
    @Test fun exactAndAccent() {
        assertTrue(WashMatcher.contains("Wash"))
        assertTrue(WashMatcher.contains("¡Guás!"))
    }
    @Test fun familiarSpanishPronunciations() {
        for (word in listOf("guach", "uash", "cuash", "wosh", "guoch", "watch", "was"))
            assertTrue("No detectó $word", WashMatcher.contains(word))
    }
    @Test fun alternativesFromSeveralHypotheses() {
        assertTrue(WashMatcher.containsAny(listOf("palabra desconocida", "guachi")))
    }
    @Test fun noAccidentalPartialWordMatch() {
        for (word in listOf("WhatsApp", "Washington", "watching", "whatsapp", "lavado"))
            assertFalse("Activación accidental por $word", WashMatcher.contains(word))
    }
    @Test fun userTrainedAliases() {
        val aliases = WashMatcher.parseAliases("uach, wachh; guachito")
        assertTrue(WashMatcher.contains("uach", aliases))
        assertTrue(WashMatcher.contains("wachh", aliases))
        assertFalse(WashMatcher.contains("abcdef", aliases))
    }
    @Test fun noOverlyShortAliases() {
        assertFalse(WashMatcher.contains("si", WashMatcher.parseAliases("si, a, ok")))
    }
}
