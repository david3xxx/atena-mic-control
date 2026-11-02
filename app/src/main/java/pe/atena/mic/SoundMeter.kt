package pe.atena.mic

import kotlin.math.log10
import kotlin.math.sqrt

/** Convierte energía de sonido en una escala visual orientativa de 0..100. */
object SoundMeter {
    fun fromPcm16(bytes: ByteArray, length: Int): Int {
        val samples = length.coerceAtMost(bytes.size) / 2
        if (samples == 0) return 0
        var energy = 0.0
        var index = 0
        repeat(samples) {
            val lo = bytes[index].toInt() and 0xff
            val hi = bytes[index + 1].toInt() shl 8
            val sample = (lo or hi).toShort().toInt().toDouble()
            energy += sample * sample
            index += 2
        }
        val rms = sqrt(energy / samples)
        if (rms == 0.0) return 0
        val dbfs = 20.0 * log10(rms / 32768.0)
        return ((dbfs + 60.0) * 2.0).toInt().coerceIn(0, 100)
    }

    /** El motor Android no calibra su callback RMS: es solo nivel visual aproximado. */
    fun fromRecognizerRms(rmsDb: Float): Int {
        if (rmsDb.isNaN()) return 0
        return (rmsDb * 7.0f).toInt().coerceIn(0, 100)
    }
}
