# ATENA V0.8 – prueba de casilla 16 (Android)

## Novedades

- **Prueba por botones:** `CASILLA 16 · AMPLIAR` y `SUBCASILLA 16 · TOCAR` permiten verificar las pulsaciones sin depender de la voz.
- **Cuadrícula de precisión:** 4 × 4 en dos niveles; «dieciséis» abre la casilla 16, luego «dieciséis» pulsa el centro de la subcasilla 16.
- **Español (Perú):** reconoce textos finales del 1 al 16 escritos en palabras o cifras, incluso «dieciséis» / «diez y seis».
- **Diagnóstico visible:** banner sobre la cuadrícula (escuchando, resultado parcial/final, error, gesto aceptado/cancelado), historial en la aplicación y círculo amarillo en el punto solicitado.
- **Dos modos de reconocimiento:**
  1. **Compatible:** usa `SpeechRecognizer` normal de Android y evita el uso simultáneo de `AudioRecord`; es el mejor primer diagnóstico. Android decide el micrófono real. Solicitar la ruta Bluetooth no garantiza que el reconocedor la respete.
  2. **Micrófono elegido (experimental):** captura PCM con `AudioRecord`, comprueba `routedDevice` y envía el audio por `RecognizerIntent.EXTRA_AUDIO_SOURCE`. Algunos reconocedores ignoran esta fuente o no la soportan; un mensaje de ruta confirmada no garantiza la ruta del reconocimiento.

## Prueba de la casilla 16 en un Samsung

1. Instalar ATENA V0.8 desde el APK generado por GitHub Actions. No es necesario instalar ningún parche manualmente.
2. Abrir ATENA y activar voluntariamente **ATENA control por voz** en Accesibilidad. Esta capacidad puede controlar otras aplicaciones y es un permiso sensible.
3. Pulsar **MOSTRAR CUADRÍCULA**, **1. CASILLA 16 · AMPLIAR**, **2. SUBCASILLA 16 · TOCAR**. Comprobar el banner de confirmación/cancelación y el círculo amarillo. Una pulsación puede no producir cambios visibles si no hay controles en ese punto.
4. Conectar auriculares; desactivar Google Voice Access durante la prueba para evitar competencia por el micrófono.
5. Pulsar **ACTUALIZAR MICRÓFONOS**, seleccionar el dispositivo y luego **INICIAR ESCUCHA · MODO COMPATIBLE**. Decir «dieciséis», esperar la cuadrícula más pequeña, decir otra vez «dieciséis».
6. Si el modo compatible reconoce comandos, probar **INICIAR ESCUCHA · MICRÓFONO ELEGIDO (EXPERIMENTAL)**. Revisar el diagnóstico: la ruta `AudioRecord` comprobada y el resultado del reconocedor son hechos distintos.
7. **DETENER ESCUCHA** al terminar y desactivar el servicio de accesibilidad si no se necesita.

## Instalación / firma

La versión usa el mismo `applicationId` (`pe.atena.miccontrol`) y `versionCode = 8`. Los APK de depuración de diferentes ejecutores de GitHub Actions pueden estar firmados con claves distintas. En caso de conflicto de firma, desinstalar ATENA V0.7 antes de instalar V0.8 (no se almacenan cuentas ni grabaciones en el proyecto).

## Compilar en GitHub

Subir el contenido de esta carpeta directamente a la raíz de `david3xxx/atena-mic-control` (no subir la carpeta como un nivel extra). El flujo `.github/workflows/build-apk.yml` ejecutará `:app:testDebugUnitTest` y `:app:assembleDebug` automáticamente y publicará el artifact **ATENA-V0-8-APK**. GitHub Actions debe finalizar en verde antes de considerar el APK compilado.

## Limitaciones y seguridad

- Este proyecto **no modifica Google Voice Access**.
- Es una aplicación experimental de accesibilidad, no auditada ni publicada en Google Play.
- No es un reconocimiento plenamente continuo ni garantiza funcionar con pantalla apagada.
- La prueba de voz no demuestra por sí sola que la captura provenga de auriculares Bluetooth. No desactivar Play Protect para forzar la instalación ante una alerta de aplicación dañina.
- No se graban archivos de voz ni se transmite audio a servidores propios. El motor de reconocimiento de Android puede necesitar conectividad según su implementación.
