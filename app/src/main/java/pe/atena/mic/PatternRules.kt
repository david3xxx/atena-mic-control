package pe.atena.mic

/** Límites exactos de la interfaz de grabación V1.1. */
object PatternRules {
    const val MIN_STEPS = 2
    const val MAX_STEPS = 4
    const val PREPARE_MILLIS = 4_000L

    fun canAdd(currentCount: Int) = currentCount in 0 until MAX_STEPS
    fun canSave(count: Int) = count in MIN_STEPS..MAX_STEPS
}
