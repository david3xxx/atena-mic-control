package pe.atena.mic

/** Rutas restringidas: sin pulsaciones ocultas ni escritura autónoma. */
object CommandRouter {
    fun action(raw: String): Action {
        val normalized = java.text.Normalizer.normalize(raw.lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "").trim().replace(Regex("\\s+"), " ")
        return when (normalized) {
            "atena inicio", "inicio", "ir al inicio", "pantalla de inicio" -> Action.HOME
            "atena atras", "atras", "volver", "regresar" -> Action.BACK
            "notificaciones", "mostrar notificaciones" -> Action.NOTIFICATIONS
            "desliza arriba", "subir", "desplazar arriba" -> Action.SCROLL_UP
            "desliza abajo", "bajar", "desplazar abajo" -> Action.SCROLL_DOWN
            "abrir chatgpt", "abre chatgpt" -> Action.OPEN_CHATGPT
            "detener escucha", "atena detener", "dejar de escuchar" -> Action.STOP
            else -> Action.NONE
        }
    }
    enum class Action { HOME, BACK, NOTIFICATIONS, SCROLL_UP, SCROLL_DOWN, OPEN_CHATGPT, STOP, NONE }
}
