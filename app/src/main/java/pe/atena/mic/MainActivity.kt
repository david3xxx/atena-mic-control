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
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView

/** ATENA V1.4: WASH → chat fijado SD → control de Voz. Pruebas de micrófono. */
class MainActivity : Activity() {
    private lateinit var devicesSpinner: Spinner
    private lateinit var meter: ProgressBar
    private lateinit var meterText: TextView
    private lateinit var status: TextView
    private lateinit var recognitionMode: Spinner
    private var inputs: List<AudioDeviceInfo> = emptyList()

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                WashListenerService.ACTION_METER -> {
                    val level = intent.getIntExtra(WashListenerService.EXTRA_LEVEL, 0).coerceIn(0, 100)
                    meter.progress = level
                    val label = intent.getStringExtra(WashListenerService.EXTRA_SOURCE) ?: "Sin fuente confirmada"
                    meterText.text = "Sonido: $level/100 · $label"
                }
                WashListenerService.ACTION_STATUS -> intent.getStringExtra(WashListenerService.EXTRA_MESSAGE)?.let(::report)
            }
        }
    }

    private fun report(msg: String) {
        status.text = ("• $msg\n" + status.text.toString()).take(4800)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = (18 * resources.displayMetrics.density).toInt()
            setPadding(p, p, p, p)
        }
        fun text(value: String, size: Float = 16f) {
            col.addView(TextView(this).apply {
                this.text = value; textSize = size
                setPadding(0, 9, 0, 9)
            })
        }
        fun button(label: String, callback: () -> Unit) {
            col.addView(Button(this).apply { this.text = label; setOnClickListener { callback() } })
        }
        text("ATENA V1.4 · WASH → SD → VOZ", 24f)
        text("Di «wash» para abrir ChatGPT, buscar tu chat fijado «SD» e intentar pulsar Voz. Necesitas activar el servicio de Accesibilidad de ATENA. La interfaz de ChatGPT puede cambiar.")
        text("1 · PRUEBA DE MICRÓFONOS", 19f)
        text("Selecciona el micrófono que quieres comprobar. La prueba de sonido es independiente del reconocimiento de la palabra «wash».")
        devicesSpinner = Spinner(this)
        col.addView(devicesSpinner)
        button("ACTUALIZAR MICRÓFONOS") { refreshDevices() }
        meter = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100; progress = 0; isIndeterminate = false
        }
        col.addView(meter, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (20 * resources.displayMetrics.density).toInt()))
        meterText = TextView(this).apply { text = "Sonido: 0/100 · Prueba sin iniciar"; textSize = 14f }
        col.addView(meterText)
        button("PROBAR MICRÓFONO · VER BARRA") { startServiceMode(WashListenerService.MODE_MONITOR) }
        button("DETENER PRUEBA") { stopService() }
        text("2 · ESCUCHA DE «WASH»", 19f)
        text("El reconocimiento de Android puede usar otro micrófono y puede detenerse en segundo plano. Si falla el modo local, prueba compatible.")
        recognitionMode = Spinner(this)
        recognitionMode.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf(
            "Local (preferido; sin conexión si Android lo permite)",
            "Compatible (motor Android; podría usar Internet)"
        ))
        col.addView(recognitionMode)
        button("INICIAR ATENA · ESCUCHAR WASH") {
            startServiceMode(if (recognitionMode.selectedItemPosition == 0) WashListenerService.MODE_LOCAL else WashListenerService.MODE_COMPATIBLE)
        }
        button("DETENER ATENA") { stopService() }
        button("PROBAR AHORA · ABRIR SD Y PULSAR VOZ") {
            val ok = WashListenerService.openFromMenuIfRunning() ||
                WashAccessibilityService.requestOpenSdAndVoice()
            report(if (ok) "ATENA está buscando el chat SD. Revisa los mensajes de diagnóstico." else
                "Debes habilitar Accesibilidad de ATENA para localizar SD y pulsar Voz.")
        }
        text("3 · ACCESIBILIDAD: NECESARIA PARA ABRIR SD Y PULSAR VOZ", 19f)
        text("Para buscar SD, ATENA necesita leer los títulos y descripciones accesibles de la interfaz de ChatGPT. Solo intentará pulsar el chat «SD» y el botón de Voz identificado por su etiqueta; no guarda conversaciones ni hace capturas. Es un permiso sensible: habilítalo solo si confías en ATENA.")
        button("ACTIVAR ACCESIBILIDAD PARA SD Y VOZ") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        val autoRearm = CheckBox(this).apply {
            text = "Reactivar WASH cuando salga de ChatGPT o parezca terminar Voz (experimental)"
            isChecked = getSharedPreferences("atena", MODE_PRIVATE).getBoolean("auto_rearm", true)
            setOnCheckedChangeListener { _, checked ->
                getSharedPreferences("atena", MODE_PRIVATE).edit().putBoolean("auto_rearm", checked).apply()
                report(if (checked) "Reactivación experimental activada." else "Reactivación experimental desactivada.")
            }
        }
        col.addView(autoRearm)
        text("Esta opción depende de que ChatGPT exponga cambios detectables en Accesibilidad. Puede interferir con Voz en segundo plano: desmárcala si ocurre.")
        text("Tras detectar «wash», ATENA libera el micrófono. Si ChatGPT cambia el nombre del botón de Voz o no muestra SD, ATENA lo indicará sin pulsar puntos desconocidos. La reactivación no es infalible.")
        button("REANUDAR ESCUCHA WASH") {
            startServiceMode(if (recognitionMode.selectedItemPosition == 0) WashListenerService.MODE_LOCAL else WashListenerService.MODE_COMPATIBLE)
        }
        text("DIAGNÓSTICO", 19f)
        status = TextView(this).apply {
            textSize = 14f
            text = getSharedPreferences("atena", MODE_PRIVATE)
                .getString("last_status", "• ATENA lista. Prueba primero el sonido.")
            setPadding(0, 8, 0, 18)
        }
        col.addView(status)
        setContentView(ScrollView(this).apply { addView(col) })
        permissions()
        refreshDevices()
    }

    private fun permissions() {
        val requests = listOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.BLUETOOTH_CONNECT)
            .filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (requests.isNotEmpty()) requestPermissions(requests.toTypedArray(), 15)
    }

    private fun label(device: AudioDeviceInfo): String {
        val name = when (device.type) {
            AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Micrófono del teléfono"
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Auriculares con cable"
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth (llamadas)"
            AudioDeviceInfo.TYPE_BLE_HEADSET -> "Bluetooth LE"
            AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE -> "Micrófono USB"
            else -> "Entrada de audio"
        }
        return "$name · ${device.productName} · ID ${device.id}"
    }

    private fun refreshDevices() {
        try {
            inputs = getSystemService(AudioManager::class.java)
                .getDevices(AudioManager.GET_DEVICES_INPUTS).toList()
            devicesSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
                inputs.map(::label).ifEmpty { listOf("Sin micrófonos detectados") })
            val headsetIndex = inputs.indexOfFirst { it.type in listOf(AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADSET) }
            if (headsetIndex >= 0) devicesSpinner.setSelection(headsetIndex)
            report("Detectados ${inputs.size} dispositivos de entrada. Selecciona el que quieras probar.")
        } catch (ex: Exception) {
            report("Error al consultar micrófonos: ${ex.message}")
        }
    }

    private fun startServiceMode(mode: String) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            report("Falta permiso de micrófono."); permissions(); return
        }
        val device = inputs.getOrNull(devicesSpinner.selectedItemPosition)
        if (device == null) {
            report("Actualiza la lista de micrófonos."); return
        }
        try {
            startForegroundService(Intent(this, WashListenerService::class.java).apply {
                action = WashListenerService.ACTION_START
                putExtra(WashListenerService.EXTRA_MODE, mode)
                putExtra(WashListenerService.EXTRA_INPUT_ID, device.id)
            })
            report("Solicitado: $mode · ${device.productName}")
        } catch (ex: Exception) {
            report("No se pudo iniciar la escucha: ${ex.message}")
        }
    }

    private fun stopService() {
        startService(Intent(this, WashListenerService::class.java).apply { action = WashListenerService.ACTION_STOP })
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addAction(WashListenerService.ACTION_STATUS)
            addAction(WashListenerService.ACTION_METER)
        }
        registerReceiver(statusReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
    }

    override fun onStop() {
        unregisterReceiver(statusReceiver)
        super.onStop()
    }
}
