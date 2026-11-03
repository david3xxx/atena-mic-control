package pe.atena.mic

import org.junit.Assert.*
import org.junit.Test

class WashMatcherTest {
    @Test fun testDirect() {
        assertTrue(WashMatcher.contains("wash"))
        assertTrue(WashMatcher.contains("¡Wash, abre ChatGPT!"))
        assertTrue(WashMatcher.contains("Guash"))
        assertTrue(WashMatcher.contains("¿guás?"))
        assertTrue(WashMatcher.contains("watch"))
    }
    @Test fun avoidFalseHits() {
        assertFalse(WashMatcher.contains("WhatsApp"))
        assertFalse(WashMatcher.contains("washington"))
        assertFalse(WashMatcher.contains("vamos a descansar"))
        assertFalse(WashMatcher.contains(""))
    }
    @Test fun alternatives() {
        assertTrue(WashMatcher.containsAny(listOf("desconocido", "guach")))
        assertFalse(WashMatcher.containsAny(listOf("play", "whatsapp")))
    }
}
