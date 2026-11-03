# ATENA V1.7 — WASH, chat SD y avisos hablados 1 / 2 / 3

## Función solicitada

Se conserva el comportamiento que ya funcionaba en la V1.6: reconocer Wash, abrir ChatGPT, ir al chat SD y pulsar Voz; también conserva la barra para medir sonido, los micrófonos, la calibración y el rearmado después de Voz.

Ahora, en **cada activación independiente de WASH**, Android intentará pronunciar en español:

- **«Uno»**: solo cuando Accesibilidad detecta que ChatGPT está en pantalla.
- **«Dos»**: solo cuando se ha seleccionado SD y se muestra su interfaz de conversación (editor o botón de Voz), sin la lista de fijados encima.
- **«Tres»**: cuando Accesibilidad detecta controles propios de una conversación de Voz activa, no simplemente al enviar un toque.

Los números se emiten **como voz**, mediante la función Texto a voz (TTS) del teléfono. Se pronuncian una sola vez por intento y en orden; una nueva activación de Wash reinicia 1 → 2 → 3. Si un paso no puede confirmarse, **no inventa ni pronuncia su número**: revisa DIAGNÓSTICO.

El TTS usa una voz española `es-PE` si el motor la tiene; como alternativa usa `es-ES`. Si el motor de voz del dispositivo no está instalado o configurado, ATENA informa del problema. El anuncio «tres» puede oírse al principio de una llamada, porque se produce después de detectar que comenzó la interfaz de Voz.

## Instalación

Esta descarga contiene **código fuente, no APK**. Descomprime el ZIP y sube todo el contenido a `https://github.com/david3xxx/atena-mic-control/upload/main` conservando las carpetas. La compilación de GitHub Actions debe completar `:app:testDebugUnitTest` y `:app:assembleDebug`; descarga el APK del artefacto de la misma ejecución. El proyecto se identifica como `versionCode=17` y `versionName=1.7-wash-avisos-hablados`.

Para oír los números, el teléfono debe tener activado el **motor Texto a voz** de Android con idioma español y el volumen multimedia/audífonos suficientemente alto. La prueba física de salida de voz y navegación requiere instalarlo en el Samsung.

## Pruebas

Las 8 simulaciones sobre la máquina de estados de pasos se ejecutaron en JVM y están en `SIMULACIONES_V17_RESULTADOS.txt`. No prueban la entrega de audio TTS, ni la UI real de ChatGPT, ni garantizan la llamada. V1.6 se conserva en el ZIP anterior; este archivo solo añade avisos de progreso y cambia el número de versión.
