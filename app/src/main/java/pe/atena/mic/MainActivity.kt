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
import android.app.NotificationManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.view.View
import android.view.WindowManager
import android.graphics.Color
import android.graphics.Typeface
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView

/** ATENA V1.5: WASH → chat fijado SD → control de Voz. Pruebas de micrófono. */
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
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = (18 * resources.displayMetrics.density).toInt()
            setPadding(p, p, p, p)
        }
        fun text(value: String, size: Float = 16f) {
            col.addView(TextView(this).apply {
                this.text = value; textSize = size; setTextColor(Color.rgb(120, 230, 255))
                setPadding(0, 9, 0, 9)
            })
        }
        fun button(label: String, callback: () -> Unit) {
            col.addView(Button(this).apply { this.text = label; setOnClickListener { callback() } })
        }
        col.setBackgroundColor(Color.rgb(3, 15, 31))
        text("J.A.R.V.I.S.", 30f)
        col.addView(JarvisOrbView(this), LinearLayout.LayoutParams(-1, (260 * resources.displayMetrics.density).toInt()))
        text("● EN ESPERA", 18f)
        val advanced = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        fun advancedText(value: String, size: Float = 16f) {
            advanced.addView(TextView(this).apply { text = value; textSize = size; setTextColor(Color.rgb(150, 220, 245)) })
        }
        fun advancedButton(label: String, callback: () -> Unit) {
            advanced.addView(Button(this).apply { text = label; setOnClickListener { callback() } })
        }
        button("▶ INICIAR JARVIS") { startServiceMode(WashListenerService.MODE_COMPATIBLE) }
        button("■ DETENER") { stopService() }
        button("♿ ACTIVAR ACCESIBILIDAD") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        button("☾ PERMISO NO MOLESTAR") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        }
        button("⚙ MOSTRAR / OCULTAR OPCIONES") {
            advanced.visibility = if (advanced.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        // Options remain collapsed by default; technical tools are kept for troubleshooting.

        advancedText("Di «Jarvis» para abrir ChatGPT, buscar tu chat fijado «SD» e intentar pulsar Voz. Necesitas activar el servicio de Accesibilidad de JARVIS. La interfaz de ChatGPT puede cambiar.")
        advancedText("1 · PRUEBA DE MICRÓFONOS", 19f)
        advancedText("Selecciona el micrófono que quieres comprobar. La prueba de sonido es independiente del reconocimiento de la palabra «Jarvis».")
        devicesSpinner = Spinner(this)
        advanced.addView(devicesSpinner)
        advancedButton("ACTUALIZAR MICRÓFONOS") { refreshDevices() }
        meter = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100; progress = 0; isIndeterminate = false
        }
        advanced.addView(meter, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (20 * resources.displayMetrics.density).toInt()))
        meterText = TextView(this).apply { text = "Sonido: 0/100 · Prueba sin iniciar"; textSize = 14f }
        advanced.addView(meterText)
        advancedButton("PROBAR MICRÓFONO · VER BARRA") { startServiceMode(WashListenerService.MODE_MONITOR) }
        advancedButton("DETENER PRUEBA") { stopService() }
        advancedText("2 · ESCUCHA DE «JARVIS»", 19f)
        advancedText("El reconocimiento de Android puede usar otro micrófono y puede detenerse en segundo plano. Si falla el modo local, prueba compatible.")
        recognitionMode = Spinner(this)
        recognitionMode.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf(
            "Local (preferido; sin conexión si Android lo permite)",
            "Compatible (motor Android; podría usar Internet)"
        ))
        advanced.addView(recognitionMode)
        advancedButton("INICIAR JARVIS · ESCUCHAR JARVIS") {
            startServiceMode(if (recognitionMode.selectedItemPosition == 0) WashListenerService.MODE_LOCAL else WashListenerService.MODE_COMPATIBLE)
        }
        advancedButton("DETENER JARVIS") { stopService() }
        advancedText("Comando principal: Jarvis. El reconocimiento de Android puede elegir otro micrófono.", 14f)
        val aliasInput = EditText(this).apply {
            hint = "Ej.: uach, was, cuash"
            setSingleLine(true)
            setText(getSharedPreferences("atena", MODE_PRIVATE).getString("wash_aliases", ""))
        }
        advanced.addView(aliasInput)
        advancedButton("GUARDAR PRONUNCIACIONES ALTERNATIVAS") {
            val aliases = WashMatcher.parseAliases(aliasInput.text.toString())
            getSharedPreferences("atena", MODE_PRIVATE).edit()
                .putString("wash_aliases", aliases.joinToString(",")).apply()
            report("Pronunciaciones alternativas guardadas: ${aliases.joinToString(", ").ifBlank { "ninguna" }}")
        }
        advancedButton("PROBAR AHORA · ABRIR SD Y PULSAR VOZ") {
            val ok = WashListenerService.openFromMenuIfRunning() ||
                WashAccessibilityService.requestOpenSdAndVoice()
            report(if (ok) "JARVIS está buscando el chat SD. Revisa los mensajes de diagnóstico." else
                "Debes habilitar Accesibilidad de JARVIS para localizar SD y pulsar Voz.")
        }
        advancedText("3 · ACCESIBILIDAD: NECESARIA PARA ABRIR SD Y PULSAR VOZ", 19f)
        advancedText("Para buscar SD, JARVIS necesita leer los títulos y descripciones accesibles de la interfaz de ChatGPT. Solo intentará pulsar el chat «SD» y el botón de Voz identificado por su etiqueta; no guarda conversaciones ni hace capturas. Es un permiso sensible: habilítalo solo si confías en JARVIS.")
        advancedButton("ACTIVAR ACCESIBILIDAD PARA SD Y VOZ") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        val autoRearm = CheckBox(this).apply {
            text = "Reactivar JARVIS cuando salga de ChatGPT o parezca terminar Voz (experimental)"
            isChecked = getSharedPreferences("atena", MODE_PRIVATE).getBoolean("auto_rearm", true)
            setOnCheckedChangeListener { _, checked ->
                getSharedPreferences("atena", MODE_PRIVATE).edit().putBoolean("auto_rearm", checked).apply()
                report(if (checked) "Reactivación experimental activada." else "Reactivación experimental desactivada.")
            }
        }
        advanced.addView(autoRearm)
        // V1.4 mostraba activado el valor por defecto sin guardarlo. Alineamos UI y servicio.
        if (!getSharedPreferences("atena", MODE_PRIVATE).contains("auto_rearm")) {
            getSharedPreferences("atena", MODE_PRIVATE).edit().putBoolean("auto_rearm", true).apply()
        }
        advancedText("Esta opción depende de que ChatGPT exponga cambios detectables en Accesibilidad. Puede interferir con Voz en segundo plano: desmárcala si ocurre.")
        advancedText("V1.5: al salir de ChatGPT o cuando reaparece el cuadro para escribir, JARVIS reintenta escuchar «Jarvis». Durante una llamada de Voz mantiene liberado el micrófono; reactivarlo al finalizar es experimental.")
        advancedText("Tras detectar «Jarvis», JARVIS libera el micrófono. Si ChatGPT cambia el nombre del botón de Voz o no muestra SD, JARVIS lo indicará sin pulsar puntos desconocidos. La reactivación no es infalible.")
        advancedButton("REANUDAR ESCUCHA JARVIS") {
            startServiceMode(if (recognitionMode.selectedItemPosition == 0) WashListenerService.MODE_LOCAL else WashListenerService.MODE_COMPATIBLE)
        }
        val diagButton = Button(this).apply { text = "◉ MOSTRAR / OCULTAR DIAGNÓSTICO" }
        advanced.addView(diagButton)
        advancedText("DIAGNÓSTICO", 19f)
        status = TextView(this).apply {
            textSize = 14f
            text = getSharedPreferences("atena", MODE_PRIVATE)
                .getString("last_status", "• JARVIS lista. Prueba primero el sonido.")
            setPadding(0, 8, 0, 18)
        }
        status.visibility = View.GONE
        diagButton.setOnClickListener { status.visibility = if (status.visibility == View.GONE) View.VISIBLE else View.GONE }
        advanced.addView(status)
        col.addView(advanced)
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
            report("Micrófonos conectados (${inputs.size}): " + inputs.joinToString(" | ") { label(it) }.ifBlank { "ninguno" })
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
