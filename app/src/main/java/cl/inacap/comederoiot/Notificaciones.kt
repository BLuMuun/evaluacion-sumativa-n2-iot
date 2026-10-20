package cl.inacap.comederoiot

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicInteger

/** Notificaciones locales; si el permiso se deniega, el resto de la aplicación sigue funcionando. */
object Notificaciones {
    private const val CANAL = "comedero_alertas"
    private val siguienteId = AtomicInteger(1)

    fun crearCanal(contexto: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canal = NotificationChannel(
                CANAL,
                "Alertas del comedero",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Avisos de solicitudes y comandos del comedero IoT"
            }
            contexto.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(canal)
        }
    }

    fun permisoHabilitado(contexto: Context): Boolean {
        val concedido = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(
                contexto,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        return concedido && NotificationManagerCompat.from(contexto).areNotificationsEnabled()
    }

    fun mostrar(contexto: Context, titulo: String, texto: String) {
        if (!permisoHabilitado(contexto)) return

        val notificacion = NotificationCompat.Builder(contexto, CANAL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(titulo)
            .setContentText(texto)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(contexto)
            .notify(siguienteId.getAndIncrement(), notificacion)
    }
}
