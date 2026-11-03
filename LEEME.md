# ATENA V1.4 — WASH → chat fijado SD → Voz

**Proyecto Android para Samsung Galaxy S23 FE.** Esta versión incorpora la navegación a la conversación fijada **«SD»**, a diferencia del anterior código V1.3 que solo abría ChatGPT. Se entregan **archivos fuente**; no es un APK ya compilado.

## Qué hace

1. La app ATENA mantiene un servicio de reconocimiento de voz en primer plano mientras Android lo permita. Reconoce **«wash»**, «guash» y variantes concretas en español.
2. Cuando detecta la palabra, **libera el micrófono** para evitar conflicto con Voz de ChatGPT.
3. Su servicio de **Accesibilidad** comprueba si ChatGPT ya está en pantalla. Si no está, intenta abrir `com.openai.chatgpt`.
4. Busca el título **«SD» exactamente**. Si no lo ve, intenta abrir el menú/historial mediante una etiqueta de accesibilidad y vuelve a buscarlo. Cuando lo encuentra, lo selecciona.
5. Intenta encontrar el control **Voz** por sus etiquetas accesibles (español/inglés) y pulsarlo. Primero usa `ACTION_CLICK`, con gesto en el **mismo elemento identificado** como respaldo.
6. Si no encuentra «SD» o el control Voz, **se detiene sin pulsar otras zonas** y muestra el motivo. No usa coordenadas inventadas.

**No puede garantizar 100 %** que el reconocimiento «wash» permanezca siempre activo, que el botón de voz tenga una etiqueta accesible o que ChatGPT recupere una llamada terminada. La UI de ChatGPT puede cambiar. La ejecución de un gesto tampoco demuestra por sí sola que ChatGPT haya iniciado Voz. Es necesario probarlo en el teléfono.

## Interfaz y consumo

- **Sin cuadrículas, sin grabación ni reproducción de patrones.** Coffee se encarga de la pantalla; ATENA no modifica esa configuración.
- Conserva **selección de micrófono**, prueba independiente por `AudioRecord`, **barra de nivel de sonido**, diagnósticos y modo de reconocimiento Android local/compatible.
- Permite **PROBAR AHORA · ABRIR SD Y PULSAR VOZ** sin depender del reconocimiento «wash».
- No graba ni almacena audio, capturas de pantalla ni contenido de ChatGPT. Para localizar botones, Accesibilidad examina brevemente los títulos y etiquetas expuestos por la interfaz; este es un permiso sensible y debe ser autorizado por el usuario.
- No funciona como hotword de bajo consumo por DSP: emplea `SpeechRecognizer` por sesiones sucesivas, por lo que podría consumir batería o ser detenido. El servicio de Accesibilidad solo inspecciona la pantalla en eventos y durante una navegación limitada a unos 16 s.
- No se reactiva tras un **forzar detención** o reinicio sin intervención del usuario.

## Instalación y pruebas

1. Sube el **contenido de este ZIP** a `https://github.com/david3xxx/atena-mic-control` en la rama `main`, manteniendo las carpetas y reemplazando archivos. La acción de GitHub compilará el APK `app-debug.apk` dentro del artefacto `ATENA-WASH-SD-v1-4-APK`.
2. Android `applicationId` sigue siendo `pe.atena.miccontrol` y `versionCode` pasa a **14**, para actualizar una ATENA anterior.
3. Instala el APK, abre ATENA, concede **Micrófono** y, cuando se solicite, **Notificaciones**.
4. Activa **ATENA · Navegación a SD y Voz** desde los ajustes de **Accesibilidad**. Este permiso es **obligatorio** para entrar automáticamente en «SD» y pulsar Voz; no lo necesitas para comprobar el nivel de sonido.
5. Usa **PROBAR MICRÓFONO · VER BARRA** y después **DETENER PRUEBA**. Selecciona teléfono y luego auriculares; comprueba el diagnóstico.
6. Con tu conversación **«SD» fijada y visible en el historial** de ChatGPT, pulsa **PROBAR AHORA · ABRIR SD Y PULSAR VOZ**. Comprueba que efectivamente entra en SD y se inicia Voz; si falla, anota el mensaje mostrado.
7. Cuando la prueba manual funcione, pulsa **INICIAR ATENA · ESCUCHAR WASH**, espera «ESCUCHANDO» y di **«wash»**.

### Limitaciones importantes

- Android puede impedir abrir ChatGPT desde segundo plano y/o pausar el reconocimiento. ATENA deja una notificación de respaldo.
- El modo compatible deja al motor de reconocimiento elegir el micrófono real; **la barra solo certifica la ruta durante la prueba de sonido**, no durante el reconocimiento de «wash».
- No hay API pública que permita iniciar/reconectar una llamada de voz de ChatGPT de forma garantizada. El toque accesible es un **intento basado en la UI existente**.
- La detección de Voz activa depende de etiquetas accesibles. ATENA evita tocar si identifica una llamada activa; la opción de reactivación tras salir de ChatGPT es experimental y puede interferir con una llamada en segundo plano.
- Si ChatGPT modifica la interfaz o el chat no se llama exactamente **SD**, ATENA mostrará error en lugar de tocar botones desconocidos.
- No se pueden garantizar resultados hasta compilar con GitHub Actions y probar en el Samsung real.

## Archivos

- `app/src/main/java/pe/atena/mic/` — escucha, micrófono, apertura, navegación accesible y reglas.
- `app/src/test/` — pruebas básicas de palabra «wash» y etiquetas de chat/voz.
- `.github/workflows/build-apk.yml` — pruebas y compilación Android en GitHub.

Nota: los archivos obsoletos de versiones antiguas que permanezcan en GitHub no son activados por el nuevo `AndroidManifest.xml` (pueden eliminarse posteriormente).
