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
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView

/** V0.8: primero prueba de casilla 16, después reconocimiento y ruta de audio. */
class MainActivity : Activity() {
    private lateinit var inputSpinner: Spinner
    private lateinit var status: TextView
    private var inputs = emptyList<AudioDeviceInfo>()
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.getStringExtra(VoiceCaptureService.EXTRA_MESSAGE)?.let { this@MainActivity.report(it) }
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
        addText("ATENA V0.8 · CASILLA DIECISÉIS", true)
        addText("El objetivo es: decir 'dieciséis' → ampliar la casilla 16 → decir 'dieciséis' → pulsar su subcasilla 16. Los números se dicen en español, del uno al dieciséis.")
        addText("PASO 1 · Activar el servicio de accesibilidad de ATENA. Este permiso permite pulsaciones en pantalla; actívalo únicamente si confías en la aplicación.")
        addButton("ABRIR ACCESIBILIDAD") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        addText("PASO 2 · Probar las pulsaciones SIN usar la voz. Esto distingue los errores de accesibilidad de los del micrófono.")
        addButton("MOSTRAR CUADRÍCULA") { report(AtenaAccessibilityService.showGrid()) }
        addButton("1. CASILLA 16 · AMPLIAR") { report(AtenaAccessibilityService.selectCell(16)) }
        addButton("2. SUBCASILLA 16 · TOCAR") { report(AtenaAccessibilityService.selectCell(16)) }
        addButton("REINICIAR CUADRÍCULA") { report(AtenaAccessibilityService.restartGrid()) }
        addButton("OCULTAR CUADRÍCULA") { report(AtenaAccessibilityService.hideGrid()) }

        addText("PASO 3 · Conectar auriculares y seleccionar su entrada. Desactiva Google Voice Access mientras pruebas ATENA para evitar conflictos.")
        inputSpinner = Spinner(this)
        column.addView(inputSpinner)
        addButton("ACTUALIZAR MICRÓFONOS") { refreshInputs() }
        addText("PASO 4 · Iniciar la escucha. Primero usa el modo compatible. Ese modo utiliza el reconocimiento habitual de Android y NO garantiza que el audio proceda de los auriculares.")
        addButton("INICIAR ESCUCHA · MODO COMPATIBLE") { start(VoiceCaptureService.MODE_COMPATIBLE) }
        addText("Si el modo compatible reconoce 'dieciséis', prueba el modo avanzado. Este comprueba la ruta física del micrófono elegido, pero el motor de voz podría no aceptar el audio externo.")
        addButton("INICIAR ESCUCHA · MICRÓFONO ELEGIDO (EXPERIMENTAL)") { start(VoiceCaptureService.MODE_ROUTED) }
        addButton("DETENER ESCUCHA") {
            startService(Intent(this, VoiceCaptureService::class.java).apply {
                action = VoiceCaptureService.ACTION_STOP
            })
        }
        addText("DIAGNÓSTICO · Los mensajes más recientes aparecen primero. La cuadrícula también muestra el último estado de ATENA.")
        status = TextView(this).apply {
            textSize = 15f
            text = "• ATENA lista. Primero prueba 16 → 16 con botones.\n"
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
        registerReceiver(receiver, IntentFilter(VoiceCaptureService.ACTION_STATUS), Context.RECEIVER_NOT_EXPORTED)
    }
    override fun onStop() {
        unregisterReceiver(receiver)
        super.onStop()
    }
}
