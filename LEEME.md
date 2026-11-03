# ATENA V1.3 WASH — activación manos libres (prueba experimental)

La V1.3 reemplaza cuadrículas y patrones por una aplicación simple que escucha la palabra **wash** y solicita abrir la aplicación oficial de **ChatGPT**. Mantiene la lista de micrófonos, una barra de sonido independiente para verificar la ruta física y un diagnóstico visible.

## Comportamiento y límites

- Primero, en ChatGPT > Configuración > Voz, activar **Iniciar con Voz** (si está disponible) y **Conversaciones en segundo plano**. La opción Iniciar con Voz solo inicia Voz automáticamente cuando se abre un chat nuevo o vacío, no garantiza una sesión que había terminado.
- Al decir «wash», Android debe reconocer la palabra (acepta variantes «guash», «guás» y similares). ATENA libera su micrófono **antes de intentar abrir ChatGPT**. La conversación activa podrá continuar si ChatGPT todavía la conserva.
- Android puede bloquear el lanzamiento automático de ChatGPT desde segundo plano. ATENA pone una acción **ABRIR CHATGPT** en la notificación como alternativa. No existe una API pública oficial que permita a ATENA iniciar o reconectar una sesión de Voz de ChatGPT de manera garantizada.
- El **servicio opcional de Accesibilidad** puede mejorar la posibilidad de abrir ChatGPT desde segundo plano y detectar que ChatGPT dejó el primer plano. **No lee textos de pantalla, no toca botones, no activa Voz ni crea cuadrículas.** Si «Intentar reactivar WASH cuando salga de ChatGPT» está marcado, ATENA intentará recuperar la escucha tras 6.5 s. **Puede interferir con Voz que todavía esté activa en segundo plano**: desactivar esa opción si ocurre.
- Los motores SpeechRecognizer no están diseñados para permanecer escuchando indefinidamente. El servicio reinicia las sesiones cuando terminan, pero Android, el ahorro de batería, la política de micrófono y otros procesos pueden detener la escucha. **No se ofrece garantía 100 % de disponibilidad**.
- La prueba de micrófono con barra verifica la **ruta de AudioRecord seleccionada** y no demuestra que SpeechRecognizer reconozca comandos por esa entrada. Android puede utilizar otra fuente para el reconocimiento.
- La prueba prioriza motor local cuando existe; el modo compatible podría enviar audio al proveedor del reconocimiento. ATENA no contiene servidores, no envía audio a un servidor propio ni almacena grabaciones.
- No hace autoarranque de micrófono tras reiniciar el teléfono ni tras un cierre forzoso, por las restricciones de Android.

## Instalación y prueba

1. En GitHub `david3xxx/atena-mic-control`, subir el **contenido interior** del ZIP (preservando carpetas y reemplazando los archivos del mismo nombre), rama `main`.
2. GitHub Actions compilará `ATENA-WASH-v1-3-APK` y generará `app-debug.apk` dentro del artefacto ZIP. Se instala como actualización porque mantiene `applicationId = pe.atena.miccontrol`, con `versionCode = 13`.
3. Abrir ATENA y conceder micrófono y notificaciones. Probar «PROBAR MICRÓFONO» primero con el teléfono y después con auriculares. Detener la prueba.
4. Activar **ATENA · Apertura manos libres** en Accesibilidad solo si confías en el proyecto y quieres la mejora opcional en segundo plano.
5. Pulsar «INICIAR ATENA · ESCUCHAR WASH». Cuando aparezca «ESCUCHANDO», decir «wash» o «guash» y comprobar si se abre ChatGPT. Si el motor local falla, usar compatible.
6. La aplicación ChatGPT debe estar instalada y configurada. Para recuperar la escucha, usar la notificación «REANUDAR WASH» o la opción experimental de autorreactivación si se sale de ChatGPT.

## Archivos y pruebas

Los archivos antiguos de cuadrículas y patrones se han quitado de este paquete fuente. Si ya existen en el repositorio GitHub, subir este proyecto no los eliminará automáticamente de GitHub, pero **no están registrados en AndroidManifest**, por lo que no se ejecutarán ni se mostrarán. Se pueden eliminar después del repositorio para limpieza. `WashMatcherTest` verifica comandos reconocibles y evita coincidencias dentro de palabras.

**Pendiente antes de considerarlo estable:** compilar con Gradle Android y probar en el Samsung Galaxy S23 FE real, especialmente segundo plano, auriculares Bluetooth y modo Voz. La compilación no demuestra disponibilidad 100 % ni apertura automática de Voz.
