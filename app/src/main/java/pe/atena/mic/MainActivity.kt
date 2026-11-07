
package pe.atena.mic

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView

class MainActivity : Activity() {

    private val background = Color.rgb(3, 10, 22)
    private val panelColor = Color.rgb(10, 27, 45)
    private val cyan = Color.rgb(0, 225, 255)
    private val white = Color.WHITE
    private val muted = Color.rgb(175, 205, 220)

    private lateinit var devicesSpinner: Spinner
    private lateinit var meter: ProgressBar
    private lateinit var meterText: TextView
    private lateinit var status: TextView
    private lateinit var recognitionMode: Spinner
    private lateinit var stateLabel: TextView

    private var inputs: List<AudioDeviceInfo> = emptyList()
    private var listening = false

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(
            context: Context?,
            intent: Intent?
        ) {
            when (intent?.action) {
                WashListenerService.ACTION_METER -> {
                    val level = intent.getIntExtra(
                        WashListenerService.EXTRA_LEVEL, 0
                    ).coerceIn(0, 100)

                    meter.progress = level

                    val source = intent.getStringExtra(
                        WashListenerService.EXTRA_SOURCE
                    ) ?: "Sin fuente confirmada"

                    meterText.text =
                        "NIVEL: $level/100\n$source"
                }

                WashListenerService.ACTION_STATUS -> {
                    intent.getStringExtra(
                        WashListenerService.EXTRA_MESSAGE
                    )?.let { report(it) }
                }
            }
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun shape(
        color: Int = panelColor,
        borderColor: Int = cyan,
        radius: Int = 18
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            setStroke(dp(1), borderColor)
        }
    }

    private fun label(
        value: String,
        size: Float = 15f,
        color: Int = white
    ): TextView {
        return TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
    }

    private fun button(
        title: String,
        action: () -> Unit
    ): Button {
        return Button(this).apply {
            text = title
            textSize = 13f
            isAllCaps = false
            setTextColor(white)
            background = shape()
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setOnClickListener { action() }
        }
    }

    private fun addButton(
        parent: LinearLayout,
        title: String,
        action: () -> Unit
    ) {
        parent.addView(
            button(title, action),
            LinearLayout.LayoutParams(
                -1, dp(53)
            ).apply {
                topMargin = dp(7)
            }
        )
    }

    private fun section(
        parent: LinearLayout,
        title: String
    ) {
        parent.addView(
            label(title, 17f, cyan).apply {
                setTypeface(null, Typeface.BOLD)
            }
        )
    }

    private fun report(message: String) {
        if (::status.isInitialized) {
            status.text =
                ("• $message\n" + status.text.toString())
                    .take(4800)
        }

        if (::stateLabel.isInitialized) {
            stateLabel.text = message
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = background
        window.navigationBarColor = background
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(background)
            setPadding(dp(16), dp(20), dp(16), dp(24))
        }

        val title = label(
            "J.A.R.V.I.S.",
            32f,
            cyan
        ).apply {
            gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
        }

        root.addView(title)

        root.addView(
            label(
                "SISTEMA DE ASISTENCIA POR VOZ",
                11f,
                muted
            ).apply {
                gravity = Gravity.CENTER
            }
        )

        root.addView(
            JarvisOrbView(this),
            LinearLayout.LayoutParams(
                -1, dp(230)
            )
        )

        stateLabel = label(
            "● EN ESPERA DE COMANDO",
            15f,
            cyan
        ).apply {
            gravity = Gravity.CENTER
        }

        root.addView(stateLabel)

        val mainControls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = shape()
        }

        addButton(
            mainControls,
            "▶ ACTIVAR JARVIS"
        ) {
            startServiceMode(
                WashListenerService.MODE_COMPATIBLE
            )
        }

        addButton(
            mainControls,
            "◉ ABRIR CHAT SD + VOZ"
        ) {
            val ok =
                WashListenerService.openFromMenuIfRunning() ||
                WashAccessibilityService.requestOpenSdAndVoice()

            report(
                if (ok) {
                    "Buscando ChatGPT y conversación SD..."
                } else {
                    "Activa Accesibilidad de JARVIS."
                }
            )
        }

        addButton(
            mainControls,
            "■ DETENER JARVIS"
        ) {
            stopServiceMode()
        }

        root.addView(mainControls)

        val advanced = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(dp(10), dp(12), dp(10), dp(12))
            background = shape()
        }

        addButton(
            root,
            "☰ MENÚ Y CONFIGURACIÓN"
        ) {
            advanced.visibility =
                if (advanced.visibility == View.VISIBLE) {
                    View.GONE
                } else {
                    View.VISIBLE
                }
        }

        section(advanced, "MICRÓFONOS")

        advanced.addView(
            label(
                "Selecciona el micrófono para la prueba:",
                14f,
                muted
            )
        )

        devicesSpinner = Spinner(this).apply {
            background = shape(
                Color.rgb(230, 245, 250),
                cyan
            )
            setPadding(dp(10), dp(5), dp(10), dp(5))
        }

        advanced.addView(
            devicesSpinner,
            LinearLayout.LayoutParams(
                -1, dp(58)
            )
        )

        addButton(
            advanced,
            "↻ ACTUALIZAR MICRÓFONOS"
        ) {
            refreshDevices()
        }

        section(advanced, "PRUEBA DE MICRÓFONO")

        meter = ProgressBar(
            this,
            null,
            android.R.attr.progressBarStyleHorizontal
        ).apply {
            max = 100
            progress = 0
            isIndeterminate = false

            progressTintList =
                android.content.res.ColorStateList.valueOf(cyan)

            progressBackgroundTintList =
                android.content.res.ColorStateList.valueOf(
                    Color.rgb(55, 83, 105)
                )
        }

        advanced.addView(
            meter,
            LinearLayout.LayoutParams(
                -1, dp(24)
            ).apply {
                topMargin = dp(10)
                bottomMargin = dp(10)
            }
        )

        meterText = label(
            "NIVEL: 0/100\nPrueba sin iniciar",
            14f,
            white
        )

        advanced.addView(meterText)

        addButton(
            advanced,
            "🎤 PROBAR MICRÓFONO"
        ) {
            startServiceMode(
                WashListenerService.MODE_MONITOR
            )
        }

        addButton(
            advanced,
            "DETENER PRUEBA"
        ) {
            stopServiceMode()
        }

        section(advanced, "RECONOCIMIENTO DE VOZ")

        recognitionMode = Spinner(this)

        val recognitionOptions = listOf(
            "Local (si está disponible)",
            "Compatible con Android"
        )

        recognitionMode.adapter = spinnerAdapter(
            recognitionOptions
        )

        recognitionMode.background = shape(
            Color.rgb(230, 245, 250),
            cyan
        )

        advanced.addView(
            recognitionMode,
            LinearLayout.LayoutParams(
                -1, dp(55)
            )
        )

        addButton(
            advanced,
            "INICIAR ESCUCHA CON MODO ELEGIDO"
        ) {
            val mode =
                if (recognitionMode.selectedItemPosition == 0) {
                    WashListenerService.MODE_LOCAL
                } else {
                    WashListenerService.MODE_COMPATIBLE
                }

            startServiceMode(mode)
        }

        advanced.addView(
            label(
                "El reconocimiento de Android puede utilizar " +
                "un micrófono distinto al seleccionado " +
                "para la prueba.",
                12f,
                muted
            )
        )

        section(advanced, "PRONUNCIACIONES ALTERNATIVAS")

        val aliasInput = EditText(this).apply {
            hint = "Ej.: yarvis, jarbis"
            setHintTextColor(muted)
            setTextColor(white)
            setSingleLine(true)
            background = shape()
            setPadding(dp(12), dp(8), dp(12), dp(8))

            setText(
                getSharedPreferences(
                    "atena",
                    MODE_PRIVATE
                ).getString("wash_aliases", "")
            )
        }

        advanced.addView(aliasInput)

        addButton(
            advanced,
            "GUARDAR PRONUNCIACIONES"
        ) {
            val aliases = WashMatcher.parseAliases(
                aliasInput.text.toString()
            )

            getSharedPreferences(
                "atena",
                MODE_PRIVATE
            ).edit()
                .putString(
                    "wash_aliases",
                    aliases.joinToString(",")
                )
                .apply()

            report("Pronunciaciones guardadas.")
        }

        section(advanced, "CONFIGURACIÓN")

        addButton(
            advanced,
            "ACTIVAR ACCESIBILIDAD"
        ) {
            startActivity(
                Intent(
                    Settings.ACTION_ACCESSIBILITY_SETTINGS
                )
            )
        }

        addButton(
            advanced,
            "PERMISO NO MOLESTAR"
        ) {
            startActivity(
                Intent(
                    Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS
                )
            )
        }

        val autoRearm = CheckBox(this).apply {
            text = "Reactivar escucha después de ChatGPT"
            setTextColor(white)
            buttonTintList =
                android.content.res.ColorStateList.valueOf(cyan)

            isChecked = getSharedPreferences(
                "atena",
                MODE_PRIVATE
            ).getBoolean("auto_rearm", true)

            setOnCheckedChangeListener { _, checked ->
                getSharedPreferences(
                    "atena",
                    MODE_PRIVATE
                ).edit()
                    .putBoolean("auto_rearm", checked)
                    .apply()
            }
        }

        advanced.addView(autoRearm)

        if (
            !getSharedPreferences(
                "atena",
                MODE_PRIVATE
            ).contains("auto_rearm")
        ) {
            getSharedPreferences(
                "atena",
                MODE_PRIVATE
            ).edit()
                .putBoolean("auto_rearm", true)
                .apply()
        }

        section(advanced, "DIAGNÓSTICO")

        status = label(
            getSharedPreferences(
                "atena",
                MODE_PRIVATE
            ).getString(
                "last_status",
                "JARVIS lista."
            ) ?: "JARVIS lista.",
            12f,
            muted
        )

        status.visibility = View.GONE

        addButton(
            advanced,
            "MOSTRAR / OCULTAR DIAGNÓSTICO"
        ) {
            status.visibility =
                if (status.visibility == View.VISIBLE) {
                    View.GONE
                } else {
                    View.VISIBLE
                }
        }

        advanced.addView(status)

        advanced.addView(
            label(
                "JARVIS V1.7.2\n" +
                "ChatGPT · SD · Control de Voz",
                12f,
                cyan
            ).apply {
                gravity = Gravity.CENTER
            }
        )

        root.addView(advanced)

        setContentView(
            ScrollView(this).apply {
                fillViewport = true
                addView(root)
            }
        )

        permissions()
        refreshDevices()
    }

    private fun spinnerAdapter(
        items: List<String>
    ): ArrayAdapter<String> {
        return object : ArrayAdapter<String>(
            this,
            android.R.layout.simple_spinner_item,
            items
        ) {
            override fun getView(
                position: Int,
                convertView: View?,
                parent: android.view.ViewGroup
            ): View {
                return super.getView(
                    position,
                    convertView,
                    parent
                ).apply {
                    if (this is TextView) {
                        setTextColor(Color.BLACK)
                        textSize = 14f
                        setPadding(
                            dp(12), dp(8),
                            dp(12), dp(8)
                        )
                    }
                }
            }

            override fun getDropDownView(
                position: Int,
                convertView: View?,
                parent: android.view.ViewGroup
            ): View {
                return super.getDropDownView(
                    position,
                    convertView,
                    parent
                ).apply {
                    setBackgroundColor(
                        Color.rgb(235, 247, 252)
                    )

                    if (this is TextView) {
                        setTextColor(Color.BLACK)
                        textSize = 14f
                        setPadding(
                            dp(12), dp(14),
                            dp(12), dp(14)
                        )
                    }
                }
            }
        }
    }

    private fun permissions() {
        val requests = listOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.BLUETOOTH_CONNECT
        ).filter {
            checkSelfPermission(it) !=
                PackageManager.PERMISSION_GRANTED
        }

        if (requests.isNotEmpty()) {
            requestPermissions(
                requests.toTypedArray(),
                15
            )
        }
    }

    private fun deviceLabel(
        device: AudioDeviceInfo
    ): String {
        val name = when (device.type) {
            AudioDeviceInfo.TYPE_BUILTIN_MIC ->
                "Micrófono del teléfono"

            AudioDeviceInfo.TYPE_WIRED_HEADSET ->
                "Auriculares con cable"

            AudioDeviceInfo.TYPE_BLUETOOTH_SCO ->
                "Bluetooth (llamadas)"

            AudioDeviceInfo.TYPE_BLE_HEADSET ->
                "Bluetooth LE"

            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE ->
                "Micrófono USB"

            else -> "Entrada de audio"
        }

        return "$name · ${device.productName} · ID ${device.id}"
    }

    private fun refreshDevices() {
        try {
            inputs = getSystemService(
                AudioManager::class.java
            ).getDevices(
                AudioManager.GET_DEVICES_INPUTS
            ).toList()

            val options = inputs.map(::deviceLabel)
                .ifEmpty {
                    listOf("Sin micrófonos detectados")
                }

            devicesSpinner.adapter =
                spinnerAdapter(options)

            val preferredIndex = inputs.indexOfFirst {
                it.type in listOf(
                    AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                    AudioDeviceInfo.TYPE_BLE_HEADSET,
                    AudioDeviceInfo.TYPE_USB_HEADSET,
                    AudioDeviceInfo.TYPE_WIRED_HEADSET
                )
            }

            if (preferredIndex >= 0) {
                devicesSpinner.setSelection(
                    preferredIndex
                )
            }

            report(
                "Micrófonos detectados: ${inputs.size}"
            )
        } catch (ex: Exception) {
            report(
                "Error consultando micrófonos: ${ex.message}"
            )
        }
    }

    private fun startServiceMode(mode: String) {
        if (
            checkSelfPermission(
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            report("Falta permiso de micrófono.")
            permissions()
            return
        }

        val device = inputs.getOrNull(
            devicesSpinner.selectedItemPosition
        )

        if (device == null) {
            report("Actualiza la lista de micrófonos.")
            return
        }

        try {
            startForegroundService(
                Intent(
                    this,
                    WashListenerService::class.java
                ).apply {
                    action = WashListenerService.ACTION_START

                    putExtra(
                        WashListenerService.EXTRA_MODE,
                        mode
                    )

                    putExtra(
                        WashListenerService.EXTRA_INPUT_ID,
                        device.id
                    )
                }
            )

            listening = true

            stateLabel.text =
                if (mode == WashListenerService.MODE_MONITOR) {
                    "● PROBANDO MICRÓFONO"
                } else {
                    "● ESCUCHANDO JARVIS"
                }

            report(
                "Modo iniciado: $mode · ${device.productName}"
            )
        } catch (ex: Exception) {
            report(
                "No se pudo iniciar: ${ex.message}"
            )
        }
    }

    private fun stopServiceMode() {
        try {
            startService(
                Intent(
                    this,
                    WashListenerService::class.java
                ).apply {
                    action = WashListenerService.ACTION_STOP
                }
            )

            listening = false
            stateLabel.text = "● EN ESPERA"
            meter.progress = 0

            report("JARVIS detenida.")
        } catch (ex: Exception) {
            report(
                "No se pudo detener: ${ex.message}"
            )
        }
    }

    override fun onStart() {
        super.onStart()

        val filter = IntentFilter().apply {
            addAction(
                WashListenerService.ACTION_STATUS
            )

            addAction(
                WashListenerService.ACTION_METER
            )
        }

        registerReceiver(
            statusReceiver,
            filter,
            Context.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStop() {
        unregisterReceiver(statusReceiver)
        super.onStop()
    }
}
