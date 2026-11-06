AudioDeviceInfo.TYPE_BLE_HEADSET)) MediaRecorder.AudioSource.VOICE_COMMUNICATION
                else MediaRecorder.AudioSource.VOICE_RECOGNITION
            val record = AudioRecord.Builder()
                .setAudioSource(source)
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build())
                .setBufferSizeInBytes(max(bufferSize * 2, 6400))
                .build()
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                error("No se pudo preparar AudioRecord")
            }
            audioRecord = record
            if (!record.setPreferredDevice(target)) error("Android rechazó el micrófono elegido")
            record.startRecording()
            if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING)
                error("Android no inició la captura")
            monitoring = true
            val session = generation
            val buffer = ByteArray(3200)
            val started = SystemClock.elapsedRealtime()
            monitorThread = Thread({
                var verified = false
                while (active && monitoring && session == generation && !Thread.currentThread().isInterrupted) {
                    val count = try { record.read(buffer, 0, buffer.size) } catch (_: Exception) { break }
                    if (count <= 0) break
                    val route = record.routedDevice
                    if (route == null && SystemClock.elapsedRealtime() - started > 3000) {
                        handler.post { if (session == generation) publish("No se pudo confirmar la ruta del micrófono.") }
                        break
                    }
                    if (route == null) continue
                    if (route.id != target.id) {
                        handler.post { if (session == generation)
                            publish("RUTA INCORRECTA: Android usa ${route.productName} en lugar de ${target.productName}.") }
                        break
                    }
                    if (!verified) {
                        verified = true
                        handler.post { if (session == generation) {
                            publish("CAPTURA CONFIRMADA: ${route.productName}.")
                            notifyUser("Probando micrófono: ${route.productName}")
                        } }
                    }
                    meter(SoundMeter.fromPcm16(buffer, count), "Micrófono verificado: ${route.productName}")
                }
                handler.post {
                    if (session == generation && active && monitoring) {
                        stopCurrentCapture()
                        publish("La prueba de sonido terminó; comprueba el diagnóstico.")
                    }
                }
            }, "atena-v15-meter").apply { isDaemon = true; start() }
        } catch (ex: Exception) {
            stopCurrentCapture()
            publish("No se pudo comprobar el micrófono: ${ex.message}")
            notifyUser("Error en prueba de micrófono")
        }
    }

    private fun openChatGPT() {
        if (!active) return
        pausedForChat = true
        monitoring = false
        stopCurrentCapture()  // ATENA libera el micrófono para que ChatGPT pueda hablar.
        if (ChatOpener.intent(this) == null) {
            publish("ChatGPT no está instalado o no se puede localizar.")
            notifyUser("ChatGPT no disponible")
            return
        }
        val canNavigate = WashAccessibilityService.requestOpenSdAndVoice()
        if (canNavigate) {
            publish("JARVIS: buscando SD y Voz. Después ATENA volverá a escuchar cuando termine.")
        } else {
            val opened = ChatOpener.open(this)
            publish(if (opened)
                "ChatGPT abierto, pero falta activar Accesibilidad de ATENA para encontrar SD y pulsar Voz."
                else "Android impidió abrir ChatGPT. Usa la notificación como alternativa.")
        }
        notifyUser("JARVIS → SD → Voz · esperando regreso al chat")
    }

    private fun stopCurrentCapture() {
        generation++
        handler.removeCallbacks(restart)
        handler.removeCallbacks(watchdog)
        recognizer?.let {
            try { it.cancel() } catch (_: Exception) { }
            try { it.destroy() } catch (_: Exception) { }
        }
        recognizer = null
        monitoring = false
        audioRecord?.let {
            try { it.stop() } catch (_: Exception) { }
            try { it.release() } catch (_: Exception) { }
        }
        audioRecord = null
        monitorThread?.interrupt()
        monitorThread = null
        clearBluetoothRoute()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        active = false
        stopCurrentCapture()
        if (current.get() === this) current.clear()
        confirmationTts?.shutdown()
        exitTts?.shutdown()
        super.onDestroy()
    }
}
