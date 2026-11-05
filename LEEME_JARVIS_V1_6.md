# JARVIS V1.6 — código fuente Android

## Incluye
- Icono J.A.R.V.I.S. proporcionado por el usuario (mipmap Android).
- Orb azul de tres tomoe que gira lentamente en la pantalla principal.
- Palabra de activación Jarvis / Javis / Yarvis mediante transcripción de Android.
- Comandos de volumen multimedia (incremento o decremento de puntos porcentuales, redondeado a pasos Android).
- «Jarvis fuera» reproduce «Okey, señor» con TTS y detiene el servicio.
- Reintentos más espaciados para reducir el bucle de conexión/desconexión.
- Opciones y diagnóstico ocultos hasta abrir el panel.
- Pantalla encendida mientras la actividad JARVIS esté visible.
- Conserva el servicio de Accesibilidad para intentar abrir el chat SD y Voz.

## Límites y pruebas necesarias
**No es un APK compilado ni está verificado en un Samsung.** Android SpeechRecognizer no garantiza escucha continua, especialmente en segundo plano. El reinicio del reconocimiento todavía puede producir interrupciones. La navegación a SD depende de la interfaz de ChatGPT y su accesibilidad. «Jarvis fuera» detiene el servicio, pero Android puede mantener el proceso en memoria. La síntesis de voz requiere un motor TTS instalado.

## Subir a GitHub
Descomprimir y subir el contenido conservando la estructura de carpetas al repositorio `david3xxx/atena-mic-control` en la rama `main`; comprobar la compilación en GitHub Actions. Antes de instalar, revisar el resultado del workflow y probar permisos de micrófono y Accesibilidad.
