package pe.atena.mic

import org.junit.Assert.*
import org.junit.Test

class PatternRulesTest {
    @Test fun recordingWaitIsFourSeconds() = assertEquals(4_000L, PatternRules.PREPARE_MILLIS)
    @Test fun atLeastTwoAndAtMostFourTaps() {
        assertFalse(PatternRules.canSave(0))
        assertFalse(PatternRules.canSave(1))
        assertTrue(PatternRules.canSave(2))
        assertTrue(PatternRules.canSave(3))
        assertTrue(PatternRules.canSave(4))
        assertFalse(PatternRules.canSave(5))
    }
    @Test fun cannotCollectMoreThanFour() {
        assertTrue(PatternRules.canAdd(0))
        assertTrue(PatternRules.canAdd(3))
        assertFalse(PatternRules.canAdd(4))
    }
}
