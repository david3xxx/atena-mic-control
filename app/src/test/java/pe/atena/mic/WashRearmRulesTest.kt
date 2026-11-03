package pe.atena.mic

import org.junit.Assert.*
import org.junit.Test

class WashRearmRulesTest {
    @Test fun neverTakeMicWhenVoiceStillActive() {
        assertFalse(WashRearmRules.isSafeToRearm(120000, true, true, 4))
    }
    @Test fun waitForVoiceToStartAndTwoStableChecks() {
        assertFalse(WashRearmRules.isSafeToRearm(1000, false, true, 2))
        assertFalse(WashRearmRules.isSafeToRearm(6500, false, true, 1))
        assertTrue(WashRearmRules.isSafeToRearm(6500, false, true, 2))
        assertFalse(WashRearmRules.isSafeToRearm(6500, false, false, 2))
    }
    @Test fun chatExitRequiresTwoChecks() {
        assertFalse(WashRearmRules.shouldRearmAfterExit(false, 5))
        assertFalse(WashRearmRules.shouldRearmAfterExit(true, 1))
        assertTrue(WashRearmRules.shouldRearmAfterExit(true, 2))
    }
}
