package pe.atena.mic

import android.content.Context
import android.content.Intent

/** Solo abre la app oficial; no controla ni se conecta a la API interna del modo Voz. */
object ChatOpener {
    private const val PACKAGE_NAME = "com.openai.chatgpt"
    fun intent(context: Context): Intent? = context.packageManager.getLaunchIntentForPackage(PACKAGE_NAME)
        ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED) }

    fun open(context: Context): Boolean {
        val intent = intent(context) ?: return false
        return try {
            context.startActivity(intent)
            true // Android puede aceptar la solicitud y aun así restringir la apertura visual.
        } catch (_: Exception) {
            false
        }
    }
}
