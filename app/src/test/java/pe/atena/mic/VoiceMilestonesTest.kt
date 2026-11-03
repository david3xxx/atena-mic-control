package pe.atena.mic

import org.junit.Assert.*
import org.junit.Test

class VoiceMilestonesTest {
    @Test fun secuenciaCorrecta() {
        val flow = VoiceMilestones()
        assertTrue(flow.confirm(1))
        assertTrue(flow.confirm(2))
        assertTrue(flow.confirm(3))
        assertTrue(flow.completed())
    }
    @Test fun noRepiteAvisos() {
        val flow = VoiceMilestones()
        assertTrue(flow.confirm(1))
        assertFalse(flow.confirm(1))
        assertTrue(flow.confirm(2))
        assertFalse(flow.confirm(2))
        assertTrue(flow.confirm(3))
        assertFalse(flow.confirm(3))
    }
    @Test fun noSaltaPasos() {
        val flow = VoiceMilestones()
        assertFalse(flow.confirm(2))
        assertFalse(flow.confirm(3))
        assertTrue(flow.confirm(1))
        assertFalse(flow.confirm(3))
        assertTrue(flow.confirm(2))
    }
    @Test fun nuevaActivacionReinicia() {
        val flow = VoiceMilestones()
        assertTrue(flow.confirm(1))
        assertTrue(flow.confirm(2))
        assertTrue(flow.confirm(3))
        flow.reset()
        assertTrue(flow.confirm(1))
        assertTrue(flow.confirm(2))
        assertTrue(flow.confirm(3))
    }
}
