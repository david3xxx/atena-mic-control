# ATENA Mic Control V0.6 — prueba Android

**Proyecto alternativo independiente; no se ha modificado Google Voice Access.**

## Qué hace

- Lista entradas de audio reales detectadas por Android (celular, Bluetooth, cable/USB).
- Permite escoger una entrada específica y solicita esa ruta mediante `AudioRecord.setPreferredDevice()`.
- Para auriculares Bluetooth solicita además la ruta de comunicación con `AudioManager.setCommunicationDevice()`.
- Comprueba `AudioRecord.getRoutedDevice()` con la captura en curso y **se detiene si Android no utiliza el dispositivo seleccionado**.
- Intenta transmitir PCM 16 kHz por `RecognizerIntent.EXTRA_AUDIO_SOURCE` al `SpeechRecognizer` de Android 13+.
- Reconoce órdenes: «inicio», «atrás», «notificaciones», «subir», «bajar», «abrir ChatGPT», «detener escucha».
- Navegación por accesibilidad solo tras activación manual del usuario. No hay API externa, escucha oculta, carga de archivos, ni almacenamiento de grabaciones.
- Notificaciones silenciosas (no garantiza ausencia de pitidos del reconocimiento o del sistema).

## Limitaciones cruciales

- **Este prototipo no reprograma ni cambia la entrada de Google Voice Access.** Esa aplicación no expone una interfaz de enrutamiento controlable por terceros.
- **La entrada externa de SpeechRecognizer es opcional**: Google advierte que un servicio de reconocimiento puede ignorar `EXTRA_AUDIO_SOURCE` y abrir por su cuenta el micrófono predeterminado. La comprobación de ruta AudioRecord NO prueba que el motor de Google acepte el audio suministrado. Para control garantizado necesitaríamos un motor de reconocimiento sobre el PCM que capturemos nosotros.
- El reconocimiento opera por turnos y puede interrumpirse/pitar; no promete escucha permanente con pantalla apagada ni funcionamiento continuo en segundo plano.
- No está integrado con ChatGPT ni con Voice Access; «abrir ChatGPT» solo intenta lanzar la aplicación oficial o la web.
- Bluetooth con A2DP de solo reproducción no es una fuente de micrófono: tiene que existir una entrada Bluetooth de llamadas (`SCO` o `BLE_HEADSET`).
- No lo hemos compilado en este entorno ni instalado en un dispositivo; los resultados dependen del fabricante, auriculares y versión de Android.

## Compilar en GitHub

Sube **el contenido** de este proyecto a un repositorio GitHub propio (o reemplaza los archivos del proyecto ATENA actual después de guardar una copia). `Actions` → `Compilar ATENA MIC v0.6` → `Run workflow`. Descarga el artefacto ZIP; dentro estará `app-debug.apk`.

**Recomendación:** usa un repositorio nuevo `atena-mic-control` para no perder tu V0.5 estable. No cambies tu APK de Google Voice Access.

## Prueba en Samsung

1. Conecta auriculares y, si corresponde, concede Bluetooth y micrófono.
2. Cierra temporalmente Voice Access para evitar que dos aplicaciones compitan por el micrófono.
3. Abre ATENA Mic Control, pulsa «ACTUALIZAR MICRÓFONOS» y selecciona la entrada Bluetooth/cable real (no solo el altavoz).
4. Para controlar Inicio/Atrás, activa manualmente ATENA en Ajustes de Accesibilidad.
5. Pulsa «INICIAR ESCUCHA». Debe mostrar «RUTA REAL CONFIRMADA: [auriculares]». Si no, detenerá la captura con explicación.
6. Di «atrás», «inicio» o «detener escucha». Observa el texto «Reconocido»; así sabremos si tu motor respeta la ruta elegida.
7. Tapar el micrófono del móvil y hablar al auricular ayuda como prueba práctica, pero no sustituye análisis de la ruta del reconocedor.

## Seguridad

Se solicitan micrófono, Bluetooth Connect (solo dispositivos vinculados), notificaciones y servicio de Accesibilidad. El servicio de accesibilidad requiere activación manual y permite operaciones globales únicamente tras reconocer comandos. No solicita permiso de Internet, ni recopila grabaciones. El motor de reconocimiento del dispositivo puede utilizar servicios propios/en la nube conforme a sus ajustes.
