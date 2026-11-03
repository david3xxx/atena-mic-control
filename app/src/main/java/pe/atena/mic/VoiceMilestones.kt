package pe.atena.mic

/** Máquina de estados mínima: un solo aviso por paso, en orden, por cada WASH. */
class VoiceMilestones {
    private var last = 0

    fun reset() { last = 0 }

    fun confirm(step: Int): Boolean {
        if (step !in 1..3 || step != last + 1) return false
        last = step
        return true
    }

    fun hasConfirmed(step: Int): Boolean = last >= step
    fun completed(): Boolean = last == 3
}
