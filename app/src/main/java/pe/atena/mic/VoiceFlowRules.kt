package pe.atena.mic

/** Reglas de reintento sin acceso a la pantalla: pruebas puras y deterministas. */
object VoiceFlowRules {
    fun likelyStarted(elapsedMs: Long, activeVoiceControl: Boolean, composerVisible: Boolean): Boolean =
        activeVoiceControl || (elapsedMs >= 1500L && !composerVisible)

    fun retryCalibratedTap(elapsedMs: Long, composerVisible: Boolean, alreadyRetried: Boolean): Boolean =
        elapsedMs >= 1800L && composerVisible && !alreadyRetried

    fun useSavedPosition(elapsedMs: Long, composerVisible: Boolean, alreadyAttempted: Boolean): Boolean =
        elapsedMs > 500L && composerVisible && !alreadyAttempted

    fun timedOut(elapsedMs: Long): Boolean = elapsedMs > 3600L
}
