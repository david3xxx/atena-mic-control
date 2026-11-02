# ATENA V0.9 — barra de sonido y diagnóstico de entrada

## Qué cambia

- El **menú principal** ahora tiene una **barra horizontal de nivel de sonido** (0 a 100), con indicación de fuente y un botón **PROBAR MICRÓFONO · VER BARRA**.
- La prueba abre `AudioRecord` para la entrada seleccionada, compara la ruta real (`routedDevice`) y calcula el nivel RMS localmente. **No guarda archivos de audio** y no ejecuta acciones de pantalla en este modo.
- La barra se actualiza también durante el modo de voz compatible si el reconocedor de Android informa niveles RMS, y en modo avanzado desde el audio PCM capturado. **Una barra móvil no significa que se hayan reconocido palabras.**
- Conserva el menú y el diagnóstico de **casilla 16 → subcasilla 16**. Los comandos de voz son palabras en español (uno a dieciséis).

## Prueba recomendada

1. Abre ATENA; concede el permiso de micrófono. Desactiva temporalmente Voice Access de Google para que no compita por el micrófono.
2. Para descartar problemas con los audífonos, selecciona primero el **micrófono del teléfono** y pulsa **PROBAR MICRÓFONO · VER BARRA**. Habla: el nivel debe moverse. Pulsa **DETENER PRUEBA DE SONIDO**.
3. Selecciona el micrófono **Bluetooth/cable**; prueba de nuevo. Si Android usa otro micrófono, ATENA mostrará **RUTA INCORRECTA** y detendrá la captura.
4. Si la barra funciona pero los comandos no, pulsa **INICIAR ESCUCHA · MODO COMPATIBLE** y revisa los mensajes «ESCUCHANDO», «RECONOCIMIENTO PARCIAL», «RESULTADO FINAL» y errores. El reconocedor Android puede no informar RMS.
5. Una vez que el motor reconozca «dieciséis», di otra vez «dieciséis» para solicitar la pulsación. Para distinguir accesibilidad de voz, también hay botones manuales 16 → 16.

**Importante:** La barra de prueba confirma energía en el `AudioRecord` seleccionado, NO que el reconocimiento de palabras use ese mismo audio. En modo compatible la fuente real no puede garantizarse; en modo avanzado ciertos motores ignoran `EXTRA_AUDIO_SOURCE`.

## Compilación e instalación

- Subir **el contenido de esta carpeta** a la raíz de `david3xxx/atena-mic-control` en GitHub, sobreescribiendo archivos antiguos. Mantener `.github/workflows/build-apk.yml`.
- GitHub Actions compilará el APK. Artifact: **ATENA-V0-9-BARRA-AUDIO-APK**.
- `applicationId=pe.atena.miccontrol`, `versionCode=9`. Al usarse claves de depuración efímeras, es posible que debas desinstalar la versión anterior si Android reporta conflicto de firma.
- Es una aplicación experimental que solicita permisos sensibles; no desactives las protecciones del teléfono para forzar su instalación.
