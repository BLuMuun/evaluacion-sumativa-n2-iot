package cl.inacap.comederoiot.enlace

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import cl.inacap.comederoiot.datos.RolUsuario

enum class EstadoEnlace(val etiqueta: String) {
    DESCONECTADO("DESCONECTADO"),
    ESPERANDO("ESPERANDO CONEXIÓN"),
    CONECTANDO("CONECTANDO"),
    CONECTADO("CONECTADO"),
    ERROR("ERROR DE CONEXIÓN")
}

/** Contrato pequeño para que la lógica IoT no dependa de los detalles de los sockets. */
interface Enlace {
    val estado: StateFlow<EstadoEnlace>
    val entrantes: SharedFlow<Mensaje>
    /** Rol Firebase verificado por el Nodo para la sesión Android actual. */
    val rolPanelAutorizado: StateFlow<RolUsuario?>

    fun iniciarNodo()
    fun conectarPanel(ipNodo: String)
    suspend fun enviar(mensaje: Mensaje): Boolean
    fun cerrarConexionActual()
    fun cerrar()
}
