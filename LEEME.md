# ATENA Cuadrícula V0.7 — Selector de micrófono y pulsación por voz

**Proyecto Android nuevo e independiente de Google Voice Access.** No modifica su APK, su firma ni el micrófono utilizado por Voice Access.

## Funcionamiento solicitado

- Al activar manualmente el servicio **ATENA control por voz** en Accesibilidad, aparece de inmediato una cuadrícula de **4 × 4 (16 casillas)** sobre la pantalla, incluso al cambiar de aplicaciones.
- Di **«12»**: la casilla 12 se divide en **otras 16 subcasillas**, sin tocar todavía la pantalla.
- Di **«5»**: ATENA solicita una pulsación en el centro de la subcasilla 5 dentro de la casilla 12, y regresa a la cuadrícula principal.
- También acepta «casilla doce», «número cinco», «mostrar cuadrícula», «ocultar cuadrícula», «reiniciar cuadrícula», «volver cuadrícula».
- No lee, copia ni transmite el contenido de la pantalla. El overlay es no interactivo al tacto; las pulsaciones dependen del servicio de accesibilidad activado voluntariamente.
- La pantalla debe estar encendida y desbloqueada para operar la cuadrícula.

## Comprobar sin depender del micrófono

1. Instala el APK que se genere desde este proyecto.
2. En **Ajustes → Accesibilidad**, activa voluntariamente **ATENA control por voz** si confías en la aplicación y entiendes sus permisos; Android puede mostrar «configuración restringida» con APK instalados fuera de Play Store. No es necesario desactivar Play Protect para desarrollar o compilar el proyecto.
3. Aparecerá la cuadrícula principal. Si no, abre la app y pulsa **MOSTRAR CUADRÍCULA**.
4. Pulsa **PROBAR CASILLA 12 · AMPLIAR**. Observa cómo la casilla 12 se subdivide.
5. Pulsa **PROBAR SUBCASILLA 5 · TOCAR**. Android intentará pulsar esa región.
6. Pulsa **REINICIAR CUADRÍCULA** o **OCULTAR CUADRÍCULA** cuando lo necesites.

## Probar con auriculares

1. Desactiva temporalmente Google Voice Access para evitar competencia por el micrófono.
2. Conecta los auriculares. Abre ATENA, concede el permiso de micrófono y dispositivos cercanos si corresponde.
3. Pulsa **ACTUALIZAR MICRÓFONOS**, selecciona la entrada deseada y **INICIAR ESCUCHA CON MICRÓFONO ELEGIDO**.
4. Di «12» y luego «5» en **dos turnos distintos**. Los comandos «inicio», «atrás», «subir», «bajar» y «abrir ChatGPT» de V0.6 siguen presentes.

**Limitación importante:** `AudioRecord.setPreferredDevice()` comprueba por qué entrada captura ATENA, pero el motor de `SpeechRecognizer` de Android **puede ignorar** el audio externo suministrado. Por ello la ruta de voz completa por auriculares no está garantizada. Las escuchas pueden reiniciarse y producir sonidos del sistema. Las pruebas con botones permiten validar la cuadrícula independientemente de ese problema.

## Compilar en GitHub

Usa un repositorio nuevo `atena-cuadricula`, o reemplaza los archivos del repositorio `atena-mic-control` (incluidos el código fuente y `.github/workflows/build-apk.yml`, manteniendo copia de la V0.6). En **Actions → Compilar ATENA CUADRICULA v0.7**, ejecuta el flujo. Si pasa pruebas, descarga **ATENA-CUADRICULA-v0-7-APK** y extrae `app-debug.apk`.

**Estado:** código fuente y pruebas preparados; todavía debe compilarse con Android SDK y verificarse en un Samsung. No es una aplicación oficial de Google ni está conectada a ChatGPT para formular respuestas.
