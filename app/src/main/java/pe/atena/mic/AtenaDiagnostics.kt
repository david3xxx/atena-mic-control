package pe.atena.mic

import android.content.Context
import android.os.SystemClock

/** Historial corto y local: solo estados técnicos, sin contenido de conversaciones. */
object AtenaDiagnostics {
    fun append(context: Context, message: String) {
        val prefs = context.getSharedPreferences("atena", Context.MODE_PRIVATE)
        val line = "${SystemClock.elapsedRealtime() / 1000}s · ${message.take(220)}"
        val old = prefs.getString("diagnostic_history", "").orEmpty()
        prefs.edit()
            .putString("diagnostic_history", ("• $line\n" + old).take(6500))
            .putString("last_status", "• $message")
            .apply()
    }
}
