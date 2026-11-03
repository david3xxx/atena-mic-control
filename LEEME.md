# ATENA V1.5 — WASH siempre recuperable (actualización V1.4)

Esta versión corrige el problema concreto: tras detectar **WASH** una vez, ATENA liberaba el micrófono para la conversación de voz pero **no volvía a escuchar** al finalizar. Se conservan los botones, el detector de micrófono y su barra, la navegación al chat fijado **SD** y la pulsación de **Voz** de V1.4.

## Cambios

- Corregido el valor de `auto_rearm`: en V1.4 el menú mostraba la casilla activada por defecto, mientras Accesibilidad leía un valor por defecto **desactivado**. Ahora ambos son coherentes.
- Nuevo monitor ligero de accesibilidad **cada 2,5 s solo cuando ATENA ha liberado el micrófono**. Detecta el regreso al chat normal cuando el compositor o el botón de iniciar Voz reaparecen, o cuando ChatGPT desaparece de pantalla. Requiere **dos comprobaciones consecutivas** antes de rearmar para reducir falsas detecciones.
- Durante el chat de voz, ATENA no mantiene abierto su propio micrófono para evitar interferencias. La escucha de **WASH** vuelve cuando detecta el cierre de esa interfaz o la salida de ChatGPT. Este comportamiento no se puede garantizar si ChatGPT no expone su estado mediante Accesibilidad o si Android detiene el servicio.
- Si no se consigue abrir SD / pulsar Voz, se recupera WASH sin quedar en pausa indefinida.
- Tras 12 errores del motor Android, espera 20 segundos y vuelve a intentar en lugar de exigir una pulsación manual.
- Se conserva la notificación con **REANUDAR WASH** como respaldo.

## Instalación

1. Descomprime este ZIP y sube su contenido a la rama `main` del proyecto `david3xxx/atena-mic-control` en GitHub.
2. Comprueba que GitHub Actions ejecute las pruebas y compile **ATENA-WASH-SD-v1-5-APK**.
3. Instala el APK generado y mantén habilitado **ATENA · Navegación a SD y Voz** en Accesibilidad.
4. Abre ATENA, deja activada la opción **Reactivar WASH cuando salga de ChatGPT o parezca terminar Voz**, pulsa **INICIAR ATENA · ESCUCHAR WASH**.
5. Di WASH y verifica que entra a SD y abre Voz. Termina la voz o cierra ChatGPT y espera unos segundos. En los diagnósticos debe aparecer `REACTIVANDO WASH` seguido de `ESCUCHANDO «wash»`. Vuelve a decir WASH para repetir el ciclo.

El reconocimiento se basa en sesiones de Android SpeechRecognizer, no en un sistema dedicado de palabras clave de activación continua. Puede detenerse por límites del sistema, uso del micrófono por ChatGPT o falta de etiquetado accesible. No se promete disponibilidad del 100 %.

## Revisión adicional: WASH pronunciado de forma aproximada

- Se aceptan variantes transcritas como **guás, guach, watch, wosh, uash, cuash, guoch, was**; solo palabras completas. **WhatsApp** y **Washington** no activan el comando por coincidencia parcial.
- Se agregó un campo **GUARDAR PRONUNCIACIONES ALTERNATIVAS** para enseñar al comparador textual lo que Android está transcribiendo en ese Samsung. No entrena el reconocedor de audio ni garantiza que siempre devuelva un resultado.
- Se amplió la detección del regreso al chat normal usando el campo editable si el texto de accesibilidad cambió.
- El motor vuelve a intentar si Android informa que temporalmente no hay reconocimiento disponible.
- El servicio pide persistencia a Android, pero un sistema que lo cierre puede requerir una nueva activación manual. No es un detector de palabra clave nativo garantizado en todo momento.

**Seis simulaciones locales:** revisar `SIMULACIONES_6_RESULTADOS.txt`. Son pruebas de lógica sin el dispositivo Android real, no certifican funcionamiento del micrófono, UI ni recuperación de sesiones de ChatGPT.

- Si Android recrea el servicio y el usuario había activado WASH, ATENA **intentará** reanudarlo (sin garantía ante restricciones de Android). Al elegir DETENER no volverá a intentarlo.
