# ATENA V1.6 — WASH → chat SD → Voz (más rápido y con diagnóstico)

**Estado:** proyecto Android listo para compilar en GitHub Actions. Las simulaciones JVM prueban reglas, no el funcionamiento real en tu Samsung.

## Qué cambia

- El comando **Wash** y el botón **PROBAR AHORA** usan la misma navegación a ChatGPT → SD → Voz.
- Si ChatGPT ya muestra el chat SD, intenta omitir la barra lateral. Se reducen algunas esperas en las transiciones; la carga real depende de Android/ChatGPT.
- Ya no se considera iniciada una llamada simplemente porque Android aceptó un toque. Se comprueba si la interfaz de voz aparece o desaparece el editor de mensajes.
- Si el primer toque deja visible el editor, intenta una **posición calibrada previamente** (una sola vez). Si sigue fallando, informa el motivo y reanuda la escucha de Wash.
- Botón opcional **CALIBRAR BOTÓN VOZ**: abre SD, muestra una capa de instrucción, y permite señalar el control de Voz que ves en pantalla. La calibración se guarda en proporciones de pantalla y orientación. No se ejecuta el toque durante la calibración. Recalibra si cambia la interfaz.
- **ACTUALIZAR DIAGNÓSTICO** y **COPIAR DIAGNÓSTICO** permiten compartir el estado exacto de la navegación sin capturas de pantalla.
- Se mantienen las variantes fonéticas de Wash, selección de micrófonos, prueba con barra de sonido y recuperación experimental de la escucha. **No** se restauran las antiguas cuadrículas ni patrones.

## Cómo probar

1. Descomprime el ZIP y sube **el contenido completo** al repositorio `david3xxx/atena-mic-control` (rama `main`), conservando las carpetas. Asegúrate de que `app/build.gradle.kts` dice `versionCode = 16` y `versionName = "1.6-wash-voz-calibrable"`.
2. En GitHub Actions comprueba que la compilación y las pruebas terminen con éxito; descarga el APK del artefacto de **esa ejecución**, no el de una versión vieja.
3. Instala la actualización y comprueba que siga activo en Android el servicio **ATENA · Navegación a SD y Voz** en Accesibilidad.
4. En el menú de ATENA pulsa **PROBAR AHORA · ABRIR SD Y PULSAR VOZ**. Si no pulsa el botón, vuelve a ATENA, pulsa **CALIBRAR BOTÓN VOZ** y, cuando se abra SD, toca una vez el botón de Voz visible bajo la capa de calibración (solo se registra su posición).
5. Repite **PROBAR AHORA**. Luego prueba tres ciclos de «Wash» terminando la llamada entre cada ciclo. Si algo falla, copia el **DIAGNÓSTICO** de ATENA y compártelo; distingue fallos de SD, de toque de Voz y de reactivación de Wash.

### Limitaciones reales

No hay un mecanismo público garantizado para iniciar el modo de Voz de ChatGPT desde una aplicación externa. Android puede suspender el reconocedor o impedir arranques desde segundo plano. La navegación depende de la versión y las etiquetas accesibles de ChatGPT. **El APK debe compilarse y probarse en el Samsung; las simulaciones no garantizan funcionamiento al 100%.**

El micrófono se libera durante el modo de Voz para no competir con ChatGPT. Coffee puede mantener la pantalla encendida, pero no evitar que Android suspenda otro proceso.
