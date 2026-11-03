package pe.atena.mic

/** Política sin dependencias Android, comprobable por tests unitarios. */
object WashRearmRules {
    fun isSafeToRearm(
        millisSinceVoiceRequest: Long,
        voiceActive: Boolean,
        composerVisible: Boolean,
        consecutiveObservations: Int
    ): Boolean = millisSinceVoiceRequest >= 5500L && !voiceActive &&
        composerVisible && consecutiveObservations >= 2

    fun shouldRearmAfterExit(chatObserved: Boolean, consecutiveAbsent: Int): Boolean =
        chatObserved && consecutiveAbsent >= 2
}
