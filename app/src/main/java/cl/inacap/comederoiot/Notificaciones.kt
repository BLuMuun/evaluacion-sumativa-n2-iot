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
    private const val ID_SOLICITUD = 1
    private val siguienteId = AtomicInteger(2)

    fun crearCanal(contexto: Context) {
        val canal = NotificationChannel(
            CANAL,
            "Alertas del comedero",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Avisos de solicitudes y comandos del comedero IoT"
        }
        contexto.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(canal)
        // Elimina avisos que hayan quedado activos de versiones anteriores de la app.
        NotificationManagerCompat.from(contexto).cancelAll()
    }

    fun permisoHabilitado(contexto: Context): Boolean {
        val concedido = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(
                contexto,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        if (!concedido) return false
        return try {
            NotificationManagerCompat.from(contexto).areNotificationsEnabled()
        } catch (_: SecurityException) {
            false
        }
    }

    fun mostrar(contexto: Context, titulo: String, texto: String) {
        mostrarConId(contexto, siguienteId.getAndIncrement(), titulo, texto)
    }

    fun mostrarSolicitud(contexto: Context, titulo: String, texto: String) {
        mostrarConId(contexto, ID_SOLICITUD, titulo, texto)
    }

    fun cancelarSolicitud(contexto: Context?) {
        if (contexto == null) return
        try {
            NotificationManagerCompat.from(contexto).cancel(ID_SOLICITUD)
        } catch (_: SecurityException) {
            // La cancelación también debe ser segura si Android revoca el permiso en paralelo.
        }
    }

    private fun mostrarConId(contexto: Context, id: Int, titulo: String, texto: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                contexto,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        val notificacion = NotificationCompat.Builder(contexto, CANAL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(titulo)
            .setContentText(texto)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        try {
            val gestor = NotificationManagerCompat.from(contexto)
            if (gestor.areNotificationsEnabled()) {
                gestor.notify(id, notificacion)
            }
        } catch (_: SecurityException) {
            // Si el permiso cambia justo antes de publicar, se descarta el aviso sin cerrar la app.
        }
    }
}
