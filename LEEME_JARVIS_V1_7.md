# JARVIS V1.7 — código fuente (no APK)

Cambios: control de volumen de voz de llamada/Bluetooth con confirmación TTS «OK», permiso No molestar y restauración del filtro anterior al detener el servicio, botón de accesibilidad principal, panel de diagnóstico ocultable y lista de micrófonos con nombre y tipo.

**Limitaciones:** no se ha compilado ni probado en Samsung. El reconocimiento de Android puede ignorar el micrófono seleccionado. La voz TTS puede salir por otro canal. No molestar silencia, pero no bloquea llamadas ni WhatsApp. Android puede restringir cambios de volumen de llamada fuera de una comunicación activa. La recuperación tras cierre forzado no está garantizada. La cuenta audible 1-2-3 y la interfaz idéntica al mockup requieren implementación adicional. No se fuerza volumen de alarmas a cero para evitar perder alarmas importantes; No molestar intenta silenciarlas durante uso.

Subir los archivos descomprimidos a la raíz del repositorio. Revisar GitHub Actions y probar en Samsung antes de instalar como versión estable.
