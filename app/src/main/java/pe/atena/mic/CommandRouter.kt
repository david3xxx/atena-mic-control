package pe.atena.mic

/** Rutas restringidas: sin pulsaciones ocultas ni escritura autónoma. */
object CommandRouter {
    fun action(raw: String): Action {
        val normalized = java.text.Normalizer.normalize(raw.lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "").trim().replace(Regex("\\s+"), " ")
        return when (normalized) {
            "mostrar cuadricula", "cuadricula", "activar cuadricula", "muestra cuadricula", "abrir cuadricula" -> Action.GRID_SHOW
            "ocultar cuadricula", "cerrar cuadricula", "quitar cuadricula" -> Action.GRID_HIDE
            "reiniciar cuadricula", "desde cero cuadricula", "cuadricula desde cero" -> Action.GRID_RESET
            "volver cuadricula", "atras cuadricula", "volver a la cuadricula" -> Action.GRID_BACK
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
    enum class Action { HOME, BACK, NOTIFICATIONS, SCROLL_UP, SCROLL_DOWN, OPEN_CHATGPT, STOP, GRID_SHOW, GRID_HIDE, GRID_RESET, GRID_BACK, NONE }
}
