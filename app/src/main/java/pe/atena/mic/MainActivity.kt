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
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView

/** Selector explícito de entrada. No cambia el micrófono de Google Voice Access. */
class MainActivity : Activity() {
    private lateinit var inputSpinner: Spinner
    private lateinit var status: TextView
    private var inputs = emptyList<AudioDeviceInfo>()
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val message = intent?.getStringExtra(VoiceCaptureService.EXTRA_MESSAGE).orEmpty()
            if (message.isNotEmpty()) status.text = message
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = ScrollView(this)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(30, 36, 30, 36)
        }
        fun text(value: String, heading: Boolean = false) {
            column.addView(TextView(this).apply {
                this.text = value
                textSize = if (heading) 24f else 16f
                setPadding(0, 10, 0, 10)
            })
        }
        fun button(value: String, cb: () -> Unit) {
            column.addView(Button(this).apply { text = value; setOnClickListener { cb() } })
        }

        text("ATENA · CONTROL DE MICRÓFONO", true)
        text("Prueba independiente de Voice Access. Elige el micrófono REAL y verifica la ruta. No modifica ninguna app de Google.")
        text("1. Conecta tus auriculares Bluetooth o de cable y selecciona su micrófono:")
        inputSpinner = Spinner(this)
        column.addView(inputSpinner)
        button("ACTUALIZAR MICRÓFONOS") { refreshInputs() }
        button("INICIAR ESCUCHA CON MICRÓFONO ELEGIDO") { start() }
        button("DETENER ESCUCHA") {
            startService(Intent(this, VoiceCaptureService::class.java).apply {
                action = VoiceCaptureService.ACTION_STOP
            })
        }
        text("2. Para controlar Atrás/Inicio/Desplazar, activa ATENA manualmente en Accesibilidad:")
        button("ABRIR AJUSTES DE ACCESIBILIDAD") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        text("3. Cuadrícula de precisión en dos niveles (4×4): aparece al activar el servicio de Accesibilidad.")
        button("MOSTRAR CUADRÍCULA") { status.text = AtenaAccessibilityService.showGrid() }
        button("PROBAR CASILLA 12 · AMPLIAR") { status.text = AtenaAccessibilityService.selectCell(12) }
        button("PROBAR SUBCASILLA 5 · TOCAR") { status.text = AtenaAccessibilityService.selectCell(5) }
        button("REINICIAR CUADRÍCULA") { status.text = AtenaAccessibilityService.restartGrid() }
        button("OCULTAR CUADRÍCULA") { status.text = AtenaAccessibilityService.hideGrid() }
        text("4. Con el micrófono conectado di '12', luego '5'; o 'cuadrícula', 'ocultar cuadrícula', 'atrás', 'inicio' y 'detener escucha'.")
        text("5. La aplicación muestra la RUTA REAL de AudioRecord. Si Android usa el micrófono equivocado, detiene la prueba y te avisa.")
        text("Importante: algunos motores de voz de Android ignoran la entrada de audio externa. La ruta del reconocimiento no puede garantizarse solo con que se vea el micrófono elegido. No graba archivos ni envía voz a servidores propios.")
        status = TextView(this).apply {
            textSize = 15f
            text = "Estado: esperando selección de micrófono."
            setPadding(4, 20, 4, 12)
        }
        column.addView(status)
        root.addView(column)
        setContentView(root)
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
        val manager = getSystemService(AudioManager::class.java)
        try {
            inputs = manager.getDevices(AudioManager.GET_DEVICES_INPUTS).toList()
            val captions = inputs.map { device ->
                "${device.productName} · ${deviceLabel(device.type)} · ID ${device.id}"
            }
            val labels = if (captions.isEmpty()) listOf("No hay micrófonos disponibles") else captions
            inputSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
            val headset = inputs.indexOfFirst {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET
            }
            if (headset >= 0) inputSpinner.setSelection(headset)
        } catch (error: SecurityException) {
            status.text = "Concede el permiso Bluetooth y vuelve a actualizar: ${error.message}"
        }
    }

    private fun deviceLabel(type: Int): String = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_MIC -> "micrófono del celular"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "micrófono Bluetooth de llamadas"
        AudioDeviceInfo.TYPE_BLE_HEADSET -> "micrófono Bluetooth LE"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "auriculares con cable"
        AudioDeviceInfo.TYPE_USB_HEADSET -> "auriculares USB"
        AudioDeviceInfo.TYPE_USB_DEVICE -> "micrófono USB"
        else -> "entrada tipo $type"
    }

    private fun start() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            status.text = "Falta permiso del micrófono."
            requestMissingPermissions()
            return
        }
        val selected = inputs.getOrNull(inputSpinner.selectedItemPosition)
        if (selected == null) {
            status.text = "Conecta un micrófono y pulsa ACTUALIZAR."
            return
        }
        val intent = Intent(this, VoiceCaptureService::class.java).apply {
            action = VoiceCaptureService.ACTION_START
            putExtra(VoiceCaptureService.EXTRA_INPUT_ID, selected.id)
        }
        startForegroundService(intent)
        status.text = "Solicitando ${selected.productName}. Espera la comprobación de ruta real..."
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
