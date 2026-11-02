package pe.atena.mic

import org.junit.Assert.*
import org.junit.Test

class GridLogicTest {
    @Test fun firstGridNumber12IsThirdRowFourthColumn() {
        val a = GridLogic.cell(GridLogic.Area(0f, 0f, 400f, 800f), 12)
        assertEquals(GridLogic.Area(300f, 400f, 400f, 600f), a)
    }
    @Test fun secondGridNumber5RefinesTheSelectedArea() {
        val a = GridLogic.target(GridLogic.Area(0f, 0f, 400f, 800f), 12, 5)
        assertEquals(GridLogic.Area(300f, 450f, 325f, 500f), a)
        assertEquals(312.5f, a.centerX, 0.001f)
        assertEquals(475f, a.centerY, 0.001f)
    }
    @Test fun spokenNumbersAreRecognized() {
        assertEquals(12, GridSpeech.number("doce"))
        assertEquals(12, GridSpeech.number("casilla 12"))
        assertEquals(5, GridSpeech.number("NÚMERO CINCO"))
        assertEquals(16, GridSpeech.number("dieciséis"))
    }
    @Test fun unrelatedNumbersAreNotTaps() {
        assertNull(GridSpeech.number("¿Cuál es el área 12 de Brodmann?"))
        assertNull(GridSpeech.number("2026"))
        assertNull(GridSpeech.number("17"))
    }
    @Test fun commandsEnableAndHideOverlay() {
        assertEquals(CommandRouter.Action.GRID_SHOW, CommandRouter.action("cuadrícula"))
        assertEquals(CommandRouter.Action.GRID_HIDE, CommandRouter.action("ocultar cuadrícula"))
        assertEquals(CommandRouter.Action.GRID_RESET, CommandRouter.action("reiniciar cuadrícula"))
    }
}
