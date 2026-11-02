# ATENA V1.0 — 3 patrones de pulsación y comandos de voz

Actualización del proyecto V0.9; conserva cuadrícula 4×4 de dos niveles, prueba de casilla 16, barra de sonido y modos de micrófono.

## ¿Qué añade V1.0?

- Tres ranuras independientes: **patrón uno**, **patrón dos**, **patrón tres**.
- Cada patrón almacena de 1 a 8 puntos en el orden tocado; solamente coordenadas normalizadas y orientación, no fotografías, texto ni contraseñas.
- En ATENA, pulsa **GRABAR PATRÓN 1**. Tienes **6 segundos** para abrir la pantalla de la otra aplicación. Cuando aparezca la capa azul, toca las posiciones que quieres memorizar, en orden. Esos toques se **registran, pero NO se ejecutan** durante la grabación. En la franja superior elige **GUARDAR** o **CANCELAR**.
- Para probar sin voz, toca **PROBAR PATRÓN 1 – EJECUTAR EN 6 s**, navega a la pantalla destino y espera.
- Para voz, inicia **ESCUCHA – MODO COMPATIBLE** y di exactamente **«patrón uno»**, **«patrón dos»** o **«patrón tres»**. También acepta «ejecutar patrón uno», «patrón 1» etc. El reconocimiento Android de voz sigue siendo una limitación de esta versión.
- Cada pulsación se envía secuencialmente mediante AccessibilityService.dispatchGesture, con ~600ms entre pulsaciones.
- Comprueba que el servicio ATENA esté activado en Ajustes > Accesibilidad. Android puede restringir la activación en APK instaladas fuera de Play Store; no desactives Play Protect para ello.

## Precauciones

- Las coordenadas son específicas de pantalla/posición: cuando una app cambia de diseño, el mismo patrón puede tocar otro lugar.
- Se rechaza reproducir si cambió la orientación, pero **no** es posible garantizar que la pantalla o el contenido sea el mismo.
- No grabes ni reproduzcas sobre contraseñas, compras, transferencias, permisos u operaciones irreversibles.
- Un comando de voz solo funciona si el reconocimiento realmente identifica esa frase. Para reconocer comandos por el micrófono de auriculares, Android puede requerir un motor de reconocimiento compatible con PCM externo; **no está garantizado**.
- La capa de grabación bloquea los toques reales mientras está activa. Los controles CANCELAR y GUARDAR están en la parte superior. Una vez cerrada puedes volver a utilizar la pantalla.

## Instalación

Subir **el contenido de esta carpeta** a `david3xxx/atena-mic-control` en la rama `main`, aceptando reemplazar archivos. GitHub Actions ejecuta tests y construye `app-debug.apk`. La descarga del artefacto tiene el nombre histórico `ATENA-MIC-v0-6-APK`, pero `versionName` debe ser `1.0-patrones`. No hay APK compilado dentro de este ZIP. Para instalar como actualización debes mantener el mismo `applicationId` y firma de depuración; si el firmante difiere, Android solicitará desinstalar la anterior.
