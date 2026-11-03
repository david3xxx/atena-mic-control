package pe.atena.mic

import android.content.Context
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import java.util.ArrayDeque
import java.util.Locale

/** Lector liviano de UNO/DOS/TRES: no abre el micrófono ni interfiere con el reconocedor. */
class StepSpeaker(context: Context, private val onFailure: (String) -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private val pending = ArrayDeque<Int>()
    private var ready = false
    private var closed = false
    private var failed = false
    private var utterance = 0
    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        main.post { initialize(status) }
    }

    private fun initialize(status: Int) {
        if (closed) return
        if (status != TextToSpeech.SUCCESS) {
            failed = true
            pending.clear()
            onFailure("No se pudieron activar los avisos hablados. Comprueba Texto a voz de Android.")
            return
        }
        val localeResult = tts.setLanguage(Locale.forLanguageTag("es-PE"))
        if (localeResult == TextToSpeech.LANG_MISSING_DATA ||
            localeResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            val fallback = tts.setLanguage(Locale.forLanguageTag("es-ES"))
            if (fallback == TextToSpeech.LANG_MISSING_DATA ||
                fallback == TextToSpeech.LANG_NOT_SUPPORTED) {
                failed = true
                pending.clear()
                onFailure("Instala o habilita una voz española para escuchar UNO/DOS/TRES.")
                return
            }
        }
        tts.setSpeechRate(1.12f)
        tts.setAudioAttributes(AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
        ready = true
        flush()
    }

    fun speak(step: Int) {
        if (closed || failed || step !in 1..3) return
        pending.addLast(step)
        if (ready) flush()
    }

    private fun flush() {
        if (closed || !ready) return
        while (pending.isNotEmpty()) {
            val number = pending.removeFirst()
            val word = when (number) { 1 -> "uno"; 2 -> "dos"; else -> "tres" }
            val result = tts.speak(word, TextToSpeech.QUEUE_ADD, null, "atena-step-${++utterance}")
            if (result == TextToSpeech.ERROR) onFailure("No se pudo pronunciar el paso $number.")
        }
    }

    fun cancelPending() {
        pending.clear()
        if (ready && !closed) tts.stop()
    }

    fun shutdown() {
        closed = true
        pending.clear()
        tts.stop()
        tts.shutdown()
    }
}
