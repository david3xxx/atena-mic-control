package pe.atena.mic

import org.junit.Assert.assertEquals
import org.junit.Test

class CommandRouterTest {
    @Test fun recognizesAccents() = assertEquals(CommandRouter.Action.BACK, CommandRouter.action("ATRÁS"))
    @Test fun recognizesHome() = assertEquals(CommandRouter.Action.HOME, CommandRouter.action("Ir al inicio"))
    @Test fun recognizesApp() = assertEquals(CommandRouter.Action.OPEN_CHATGPT, CommandRouter.action("Abre ChatGPT"))
    @Test fun rejectsUnrelated() = assertEquals(CommandRouter.Action.NONE, CommandRouter.action("¿Qué hora es?"))
    @Test fun stopCommand() = assertEquals(CommandRouter.Action.STOP, CommandRouter.action("ATENA DETENER"))
}
