# ATENA V1.1 — Grabar patrones mediante círculos amarillos

Actualización del proyecto V1.0. Conserva cuadrícula 1–16 en español, prueba de casilla 16, barra de audio, reconocimiento experimental y patrones 1, 2 y 3.

## Grabación corregida

1. Activa el servicio **ATENA control por voz** en Accesibilidad (solo si confías en la aplicación).
2. En el menú pulsa **GRABAR PATRÓN 1**, 2 o 3.
3. Tienes **4 segundos** para cambiar a la aplicación y pantalla deseada. Se muestra un círculo **amarillo** con cuenta regresiva, que **no bloquea** la navegación.
4. Al terminar el conteo aparece la capa de captura. Toca **mínimo 2 y máximo 4 posiciones**. Cada toque genera un **círculo amarillo** numerado; durante la captura NO toca los controles de la app subyacente.
5. Pulsa el **círculo BLANCO (✓)** arriba a la derecha para finalizar y guardar. Si aún no tienes dos puntos, no permite guardar. El **círculo amarillo (×)** arriba a la izquierda cancela la grabación y conserva el patrón anterior.
6. Para reproducir sin voz usa **PROBAR PATRÓN 1/2/3**; tendrás 6 segundos para regresar a la pantalla donde lo grabaste. Los toques reproducidos llevan un círculo **amarillo** visible, y se ejecutan en secuencia con `dispatchGesture`.
7. Para probar por voz, inicia escucha compatible o avanzada; di «patrón uno», «patrón dos» o «patrón tres». Android puede no reconocer el audio Bluetooth; la barra de sonido no significa que la voz se convierta en texto.

## Avisos importantes

- Las coordenadas se guardan normalizadas a la pantalla y dependen de orientación y diseño, **no reconocen botones**; al cambiar la app, tamaño o pantalla pueden tocar un lugar diferente.
- Los patrones antiguos con 1 o más de 4 puntos deben grabarse de nuevo bajo el límite nuevo.
- No uses patrones para operaciones sensibles, contraseñas, pagos, permisos ni acciones irreversibles.
- La grabación consume los toques en una capa de accesibilidad; no interactúa con la pantalla de fondo hasta que pulsas guardar/cancelar.
- Las marcas amarillas durante la reproducción son una indicación visual, NO una confirmación de que el botón subyacente reaccionó.
- No se almacena contenido de pantalla, solo posiciones y orientación dentro de almacenamiento privado de la app.

## Compilación e instalación

Sube el contenido extraído del ZIP a `david3xxx/atena-mic-control` en la rama `main`, reemplazando los archivos existentes. GitHub Actions ejecutará los tests y compilará `app-debug.apk`. La actualización usa el mismo `applicationId` y `versionCode = 11`.
Si Android rechaza la actualización por firma diferente puede ser necesario desinstalar la versión antigua (se perderán patrones guardados). No omitas protecciones de seguridad del celular.

**Alcance de validación:** pruebas de lógica local y comprobaciones de archivos; las capas táctiles y `dispatchGesture` requieren prueba real en Samsung.
