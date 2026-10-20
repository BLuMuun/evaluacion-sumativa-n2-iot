package cl.inacap.comederoiot.enlace

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest

/** Informa si Android pierde la red Wi-Fi mientras hay una sesión activa. */
class MonitorWifi(contexto: Context, private val alPerderWifi: () -> Unit) {
    private val gestor = contexto.applicationContext
        .getSystemService(ConnectivityManager::class.java)
    private var registrado = false

    private val solicitud = NetworkRequest.Builder()
        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        .build()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onLost(network: Network) {
            alPerderWifi()
        }
    }

    fun iniciar() {
        if (registrado) return
        try {
            gestor.registerNetworkCallback(solicitud, callback)
            registrado = true
        } catch (_: SecurityException) {
            // Sin permiso de estado de red, el socket aún conserva su detección de EOF/error.
        } catch (_: RuntimeException) {
            // El monitor es complementario; una falla no debe cerrar la aplicación.
        }
    }

    fun detener() {
        if (!registrado) return
        try {
            gestor.unregisterNetworkCallback(callback)
        } catch (_: RuntimeException) {
            // Puede haberse liberado durante el cierre de la Activity.
        } finally {
            registrado = false
        }
    }
}
