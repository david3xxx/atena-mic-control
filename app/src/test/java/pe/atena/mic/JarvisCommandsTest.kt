package pe.atena.mic
import org.junit.Assert.*
import org.junit.Test
class JarvisCommandsTest {
 @Test fun open() { assertEquals(JarvisCommands.Command.Open, JarvisCommands.parse(listOf("Jarvis"))) }
 @Test fun exit() { assertEquals(JarvisCommands.Command.Exit, JarvisCommands.parse(listOf("Javis fuera"))) }
 @Test fun up() { assertEquals(JarvisCommands.Command.Volume("up", 5), JarvisCommands.parse(listOf("Jarvis sube el volumen 5%"))) }
 @Test fun down() { assertEquals(JarvisCommands.Command.Volume("down", 5), JarvisCommands.parse(listOf("Yarvis baja el volumen cinco por ciento"))) }
 @Test fun set() { assertEquals(JarvisCommands.Command.Volume("set", 50), JarvisCommands.parse(listOf("Jarvis volumen al 50%"))) }
 @Test fun noFalseTrigger() { assertNull(JarvisCommands.parse(listOf("Washington"))) }
}
