package pe.atena.mic

import java.text.Normalizer
import java.util.Locale

/** Reglas conservadoras para reconocer títulos y controles accesibles de ChatGPT. */
object SdUiRules {
    fun normalized(value: CharSequence?): String = Normalizer.normalize(
        value?.toString().orEmpty().lowercase(Locale.ROOT).trim(), Normalizer.Form.NFD
    ).replace(Regex("\\p{Mn}+"), "").replace(Regex("\\s+"), " ")

    // No basta con que el nombre 'SD' aparezca dentro de una pregunta o respuesta.
    fun isSdTitle(value: CharSequence?): Boolean = normalized(value) == "sd"

    fun isNavigationButton(value: CharSequence?): Boolean {
        val s = normalized(value)
        return listOf("abrir barra lateral", "abrir la barra lateral", "open sidebar", "mostrar barra lateral",
            "navigation drawer", "open drawer", "abrir menu", "open menu",
            "menu de navegacion", "menu de chats", "historial de chats",
            "show sidebar", "toggle sidebar", "show navigation", "abrir historial",
            "mostrar menu", "show menu", "menu lateral")
            .any { s.contains(it) } || s == "menu" || s == "menú" || s == "chats"
    }

    /** Evita tocar por error Dictado/teclado: no reconoce 'micrófono' a secas. */
    fun isStartVoiceButton(value: CharSequence?): Boolean {
        val s = normalized(value)
        if (s.isBlank()) return false
        if (listOf("dictad", "dictat", "transcrib", "record audio", "grabar audio").any { s.contains(it) }) return false
        return listOf("iniciar voz", "iniciar conversacion de voz", "empezar conversacion de voz",
            "abrir modo voz", "modo de voz", "voice mode", "start voice",
            "start a voice conversation", "start voice chat", "open voice",
            "chat de voz", "conversacion de voz", "hablar con chatgpt")
            .any { s.contains(it) } || s == "voz" || s == "voice"
    }

    /** Editor de texto o control de nueva llamada visibles al regresar del chat de voz. */
    fun isChatComposer(value: CharSequence?): Boolean {
        val s = normalized(value)
        return s == "preguntar a chatgpt" || s == "ask chatgpt" ||
            s == "mensaje a chatgpt" || s == "message chatgpt" ||
            s == "pregunta lo que quieras" || s == "ask anything" ||
            s == "escribe un mensaje" || s == "escribir un mensaje" ||
            s == "send a message"
    }

    fun isActiveVoiceIndicator(value: CharSequence?): Boolean {
        val s = normalized(value)
        return listOf("finalizar conversacion de voz", "salir del modo de voz", "terminar voz",
            "end voice", "exit voice", "stop voice conversation", "mute microphone",
            "silenciar microfono", "leave voice").any { s.contains(it) }
    }
}
