package pe.atena.mic

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Bundle
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ProgressBar
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView

/** V0.8: primero prueba de casilla 16, después reconocimiento y ruta de audio. */
class MainActivity : Activity() {
    private lateinit var inputSpinner: Spinner
    private lateinit var status: TextView
    private lateinit var soundBar: ProgressBar
    private lateinit var soundCaption: TextView
    private var inputs = emptyList<AudioDeviceInfo>()
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == VoiceCaptureService.ACTION_METER) {
                val level = intent.getIntExtra(VoiceCaptureService.EXTRA_LEVEL, 0).coerceIn(0, 100)
                soundBar.progress = level
                val source = intent.getStringExtra(VoiceCaptureService.EXTRA_SOURCE) ?: "Sin fuente"
                soundCaption.text = "Nivel: $level/100 · $source"
            } else {
                intent?.getStringExtra(VoiceCaptureService.EXTRA_MESSAGE)
                    ?.let { this@MainActivity.report(it) }
            }
        }
    }

    private fun report(message: String) {
        // Conservar los últimos diagnósticos permite leerlos al volver de otra aplicación.
        status.text = ("• $message\n" + status.text.toString()).take(3500)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 28, 28, 28)
        }
        fun addText(value: String, large: Boolean = false) {
            column.addView(TextView(this).apply {
                text = value
                textSize = if (large) 23f else 16f
                setPadding(0, 8, 0, 8)
            })
        }
        fun addButton(value: String, onTap: () -> Unit) {
            column.addView(Button(this).apply { text = value; setOnClickListener { onTap() } })
        }
        addText("ATENA V1.1 · PATRONES CON CÍRCULOS", true)
        addText("PRIMERO · Comprueba el micrófono. Habla y mira si se mueve la barra. No necesitas activar la cuadrícula para esta prueba.")
        inputSpinner = Spinner(this)
        column.addView(inputSpinner)
        addButton("ACTUALIZAR MICRÓFONOS") { refreshInputs() }
        soundBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            isIndeterminate = false
            minimumHeight = (14 * resources.displayMetrics.density).toInt()
        }
        column.addView(soundBar, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, (18 * resources.displayMetrics.density).toInt()
        ))
        soundCaption = TextView(this).apply {
            text = "Nivel: 0/100 · Prueba sin iniciar"
            textSize = 14f
            setPadding(0, 6, 0, 10)
        }
        column.addView(soundCaption)
        addButton("PROBAR MICRÓFONO · VER BARRA") { start(VoiceCaptureService.MODE_MONITOR) }
        addButton("DETENER PRUEBA DE SONIDO") { stopListening() }
        addText("Esta barra muestra energía del sonido: no asegura que Android entienda palabras. Prueba primero el micrófono del teléfono y después tus auriculares.")
        addText("SEGUNDO · Prueba la casilla 16. Los números se dicen en español, del uno al dieciséis.")
        addButton("ABRIR ACCESIBILIDAD") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        addButton("MOSTRAR CUADRÍCULA") { report(AtenaAccessibilityService.showGrid()) }
        addButton("1. CASILLA 16 · AMPLIAR") { report(AtenaAccessibilityService.selectCell(16)) }
        addButton("2. SUBCASILLA 16 · TOCAR") { report(AtenaAccessibilityService.selectCell(16)) }
        addButton("REINICIAR CUADRÍCULA") { report(AtenaAccessibilityService.restartGrid()) }
        addButton("OCULTAR CUADRÍCULA") { report(AtenaAccessibilityService.hideGrid()) }
        addText("PATRONES · Pulsa GRABAR y tendrás exactamente 4 segundos para ir a la pantalla elegida. Aparecerá una capa táctil: marca de 2 a 4 posiciones en orden; cada una se marca con un CÍRCULO AMARILLO. Al terminar toca el CÍRCULO BLANCO (✓) para guardar. El círculo amarillo (×) cancela. Los toques solo se graban: no activan botones durante la captura.")
        for (slot in 1..3) {
            addText("PATRÓN $slot · di 'patrón ${listOf("uno", "dos", "tres")[slot - 1]}' para reproducir.")
            addButton("GRABAR PATRÓN $slot") { report(AtenaAccessibilityService.recordPattern(slot)) }
            addButton("PROBAR PATRÓN $slot · EJECUTAR EN 6 s") {
                report(AtenaAccessibilityService.playPattern(slot, delayed = true))
            }
            addButton("BORRAR PATRÓN $slot") { report(AtenaAccessibilityService.deletePattern(slot)) }
        }
        addText("ATENCIÓN: los patrones son coordenadas, no nombres de botones. Úsalos solo en la misma aplicación, orientación y pantalla. No guardes ni ejecutes toques sobre contraseñas, pagos o acciones irreversibles. Para comandos de voz, primero inicia ESCUCHA en MODO COMPATIBLE. Si el motor Android no reconoce voz, la orden verbal tampoco funcionará.")
        addText("TERCERO · Desactiva temporalmente Google Voice Access al probar ATENA. Para probar reconocimiento, pulsa iniciar escucha y di 'dieciséis', después 'dieciséis'.")
        addText("PASO 4 · Iniciar la escucha. Primero usa el modo compatible. Ese modo utiliza el reconocimiento habitual de Android y NO garantiza que el audio proceda de los auriculares.")
        addButton("INICIAR ESCUCHA · MODO COMPATIBLE") { start(VoiceCaptureService.MODE_COMPATIBLE) }
        addText("Si el modo compatible reconoce 'dieciséis', prueba el modo avanzado. Este comprueba la ruta física del micrófono elegido, pero el motor de voz podría no aceptar el audio externo.")
        addButton("INICIAR ESCUCHA · MICRÓFONO ELEGIDO (EXPERIMENTAL)") { start(VoiceCaptureService.MODE_ROUTED) }
        addButton("DETENER ESCUCHA") { stopListening() }
        addText("DIAGNÓSTICO · Los mensajes más recientes aparecen primero. La cuadrícula también muestra el último estado de ATENA.")
        status = TextView(this).apply {
            textSize = 15f
            text = "• ATENA lista. Para patrones, graba entre 2 y 4 puntos con círculos amarillos.\n"
            setPadding(4, 12, 4, 18)
        }
        column.addView(status)
        setContentView(ScrollView(this).apply { addView(column) })
        requestMissingPermissions()
        refreshInputs()
    }

    private fun requestMissingPermissions() {
        val requested = listOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.BLUETOOTH_CONNECT
        ).filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (requested.isNotEmpty()) requestPermissions(requested.toTypedArray(), 14)
    }

    private fun refreshInputs() {
        try {
            inputs = getSystemService(AudioManager::class.java)
                .getDevices(AudioManager.GET_DEVICES_INPUTS).toList()
            val captions = inputs.map { "${it.productName} · ${deviceLabel(it.type)} · ID ${it.id}" }
            inputSpinner.adapter = ArrayAdapter(
                this, android.R.layout.simple_spinner_dropdown_item,
                if (captions.isEmpty()) listOf("No hay entradas disponibles") else captions
            )
            val headset = inputs.indexOfFirst {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_USB_HEADSET
            }
            if (headset >= 0) inputSpinner.setSelection(headset)
            report("Entradas encontradas: ${inputs.size}. Selecciona tus auriculares si aparecen.")
        } catch (error: Exception) {
            report("No se pudieron enumerar los micrófonos: ${error.message}")
        }
    }

    private fun stopListening() {
        startService(Intent(this, VoiceCaptureService::class.java).apply {
            action = VoiceCaptureService.ACTION_STOP
        })
    }

    private fun deviceLabel(type: Int) = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_MIC -> "micrófono del teléfono"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth (llamadas)"
        AudioDeviceInfo.TYPE_BLE_HEADSET -> "Bluetooth LE"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "auriculares con cable"
        AudioDeviceInfo.TYPE_USB_HEADSET -> "auriculares USB"
        AudioDeviceInfo.TYPE_USB_DEVICE -> "micrófono USB"
        else -> "entrada tipo $type"
    }

    private fun start(mode: String) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            report("Permiso de micrófono pendiente.")
            requestMissingPermissions()
            return
        }
        val selected = inputs.getOrNull(inputSpinner.selectedItemPosition)
        if (selected == null) {
            report("Selecciona primero un micrófono con ACTUALIZAR MICRÓFONOS.")
            return
        }
        val intent = Intent(this, VoiceCaptureService::class.java).apply {
            action = VoiceCaptureService.ACTION_START
            putExtra(VoiceCaptureService.EXTRA_INPUT_ID, selected.id)
            putExtra(VoiceCaptureService.EXTRA_MODE, mode)
        }
        try {
            startForegroundService(intent)
            report("Inicio solicitado: ${selected.productName}, modo $mode.")
        } catch (ex: Exception) {
            report("No se pudo iniciar la escucha: ${ex.message}")
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addAction(VoiceCaptureService.ACTION_STATUS)
            addAction(VoiceCaptureService.ACTION_METER)
        }
        registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
    }
    override fun onStop() {
        unregisterReceiver(receiver)
        super.onStop()
    }
}
