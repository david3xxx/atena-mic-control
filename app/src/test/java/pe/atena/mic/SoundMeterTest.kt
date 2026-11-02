package pe.atena.mic

import org.junit.Assert.*
import org.junit.Test

class SoundMeterTest {
    @Test fun noSamplesProduceZero() {
        assertEquals(0, SoundMeter.fromPcm16(byteArrayOf(), 0))
        assertEquals(0, SoundMeter.fromPcm16(byteArrayOf(0, 0, 0, 0), 4))
    }
    @Test fun louderPcmProducesBiggerLevel() {
        val quiet = byteArrayOf(1, 0, 1, 0)
        val loud = byteArrayOf(0, 64, 0, 64)
        assertTrue(SoundMeter.fromPcm16(loud, 4) > SoundMeter.fromPcm16(quiet, 4))
    }
    @Test fun levelsAreBounded() {
        assertEquals(100, SoundMeter.fromPcm16(byteArrayOf(0, -128, 0, -128), 4))
        assertEquals(0, SoundMeter.fromRecognizerRms(Float.NaN))
        assertEquals(100, SoundMeter.fromRecognizerRms(100f))
    }
}
