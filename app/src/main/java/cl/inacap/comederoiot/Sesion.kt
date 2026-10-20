package cl.inacap.comederoiot

import android.content.Context
import android.os.SystemClock
import cl.inacap.comederoiot.datos.PerfilUsuario
import cl.inacap.comederoiot.datos.RepositorioLecturas
import cl.inacap.comederoiot.datos.RolUsuario
import cl.inacap.comederoiot.enlace.Enlace
import cl.inacap.comederoiot.enlace.EstadoEnlace
import cl.inacap.comederoiot.enlace.Mensaje
import cl.inacap.comederoiot.enlace.ModoConexion
import cl.inacap.comederoiot.enlace.MonitorWifi
import cl.inacap.comederoiot.enlace.WifiEnlace
import cl.inacap.comederoiot.seguridad.Cripto
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class RolApp { NODO, PANEL }

data class EstadoSesion(
    val rol: RolApp? = null,
    val rolUsuario: RolUsuario? = null,
    val modoConexion: ModoConexion? = null,
    val enlace: EstadoEnlace = EstadoEnlace.DESCONECTADO,
    val ipLocal: String? = null,
    val rasgunos: Int = 0,
    val comedero: String = "CERRADO",
    val ultimaEntrega: String = "—",
    val notificacionesHabilitadas: Boolean = true,
    val aviso: String? = null,
    val error: String? = null
)

/** Coordina la interfaz y la sesión IoT sin bloquear el hilo principal. */
object Sesion {
    private const val VENTANA_MS = 5_000L
    private const val ENTREGA_MS = 1_500L
    private const val LATIDO_MS = 2_000L
    private const val TIEMPO_SIN_LATIDO_MS = 6_000L
    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _estado = MutableStateFlow(EstadoSesion())
    private var contexto: Context? = null
    private var enlace: Enlace? = null
    private var rol: RolApp? = null
    private var modoConexion = ModoConexion.ANDROID_CIFRADO
    private var perfil: PerfilUsuario? = null
    private var repositorioLecturas: RepositorioLecturas? = null
    private var sesionId = UUID.randomUUID().toString()
    private var ventana = VentanaRasgunos(VENTANA_MS)
    private var trabajoVentana: Job? = null
    private var trabajoEntrega: Job? = null
    private var trabajoLatido: Job? = null
    private var trabajoVigilancia: Job? = null
    private var trabajoEstado: Job? = null
    private var trabajoMensajes: Job? = null
    private var generacionVentana = 0
    private var generacionSesion = 0
    @Volatile private var ultimoMensajeValidoMs = 0L
    private var monitorWifi: MonitorWifi? = null

    val estado = _estado.asStateFlow()

    fun configurarUsuario(usuario: PerfilUsuario, lecturas: RepositorioLecturas) {
        perfil = usuario
        repositorioLecturas = lecturas
        _estado.value = _estado.value.copy(rolUsuario = usuario.rol)
    }

    fun limpiarUsuario() {
        desconectar()
        perfil = null
        repositorioLecturas = null
        _estado.value = EstadoSesion(notificacionesHabilitadas = _estado.value.notificacionesHabilitadas)
    }

    fun actualizarPermisoNotificaciones(contexto: Context) {
        _estado.value = _estado.value.copy(
            notificacionesHabilitadas = Notificaciones.permisoHabilitado(contexto)
        )
    }

    fun iniciarNodo(contexto: Context, codigo: String): String? {
        if (perfil == null) return "Inicia sesión antes de conectar un dispositivo."
        if (!Cripto.codigoValido(codigo)) return "El código debe tener exactamente 6 dígitos."
        val ip = WifiEnlace.ipLocal()
            ?: return "Conecta este teléfono a la red Wi-Fi o al hotspot antes de iniciar el Nodo."

        val generacion = preparar(contexto, RolApp.NODO, ModoConexion.ANDROID_CIFRADO, ip)
        alcance.launch {
            try {
                if (generacion != generacionSesion) return@launch
                val nuevoEnlace = WifiEnlace(codigo, ModoConexion.ANDROID_CIFRADO)
                enlace = nuevoEnlace
                monitorWifi = MonitorWifi(contexto) { notificarPerdidaDeRed() }.also { it.iniciar() }
                observarEnlace(nuevoEnlace, RolApp.NODO)
                _estado.value = _estado.value.copy(enlace = EstadoEnlace.ESPERANDO)
                nuevoEnlace.iniciarNodo()
            } catch (_: Exception) {
                if (generacion == generacionSesion) {
                    _estado.value = _estado.value.copy(
                        enlace = EstadoEnlace.ERROR,
                        error = "No se pudo iniciar el Nodo. Revisa Wi-Fi y el puerto 5050."
                    )
                }
            }
        }
        return null
    }

    fun conectarPanel(
        contexto: Context,
        ip: String,
        codigo: String,
        modo: ModoConexion
    ): String? {
        if (perfil == null) return "Inicia sesión antes de conectar un dispositivo."
        if (!esIpv4(ip.trim())) return "Escribe una dirección IPv4 válida del Nodo o gateway."
        if (modo == ModoConexion.ANDROID_CIFRADO && !Cripto.codigoValido(codigo)) {
            return "El código debe tener exactamente 6 dígitos."
        }

        val generacion = preparar(contexto, RolApp.PANEL, modo, null)
        alcance.launch {
            try {
                if (generacion != generacionSesion) return@launch
                val codigoSesion = codigo.takeIf { modo == ModoConexion.ANDROID_CIFRADO }
                val nuevoEnlace = WifiEnlace(codigoSesion, modo)
                enlace = nuevoEnlace
                monitorWifi = MonitorWifi(contexto) { notificarPerdidaDeRed() }.also { it.iniciar() }
                observarEnlace(nuevoEnlace, RolApp.PANEL)
                _estado.value = _estado.value.copy(enlace = EstadoEnlace.CONECTANDO)
                nuevoEnlace.conectarPanel(ip.trim())
            } catch (_: Exception) {
                if (generacion == generacionSesion) {
                    _estado.value = _estado.value.copy(
                        enlace = EstadoEnlace.ERROR,
                        error = "No se pudo preparar el enlace. Revisa la IP y la red local."
                    )
                }
            }
        }
        return null
    }

    private fun preparar(
        contextoNuevo: Context,
        nuevoRol: RolApp,
        modo: ModoConexion,
        ipLocal: String?
    ): Int {
        detenerEnlace()
        generacionSesion += 1
        contexto = contextoNuevo.applicationContext
        rol = nuevoRol
        modoConexion = modo
        sesionId = UUID.randomUUID().toString()
        ventana = VentanaRasgunos(VENTANA_MS)
        _estado.value = EstadoSesion(
            rol = nuevoRol,
            rolUsuario = perfil?.rol,
            modoConexion = modo,
            enlace = EstadoEnlace.DESCONECTADO,
            ipLocal = ipLocal,
            notificacionesHabilitadas = Notificaciones.permisoHabilitado(contextoNuevo)
        )
        return generacionSesion
    }

    private fun observarEnlace(nuevoEnlace: Enlace, rolConexion: RolApp) {
        var estadoAnterior = nuevoEnlace.estado.value
        trabajoEstado = alcance.launch {
            nuevoEnlace.estado.collect { nuevoEstado ->
                _estado.value = _estado.value.copy(
                    enlace = nuevoEstado,
                    aviso = if (nuevoEstado == EstadoEnlace.CONECTADO) null else _estado.value.aviso,
                    error = if (nuevoEstado == EstadoEnlace.ERROR) {
                        "No se pudo iniciar o conectar. Revisa la red, la IP y el puerto 5050."
                    } else null
                )

                if (nuevoEstado != EstadoEnlace.CONECTADO) {
                    trabajoLatido?.cancel()
                    trabajoLatido = null
                    trabajoVigilancia?.cancel()
                    trabajoVigilancia = null
                }

                if (estadoAnterior == EstadoEnlace.CONECTADO && nuevoEstado != EstadoEnlace.CONECTADO) {
                    volverAEstadoSeguro()
                    _estado.value = _estado.value.copy(
                        aviso = "Enlace perdido. Comedero cerrado y solicitud reiniciada."
                    )
                    contexto?.let {
                        Notificaciones.mostrar(
                            it,
                            "Enlace perdido",
                            "El comedero quedó cerrado y la solicitud se reinició."
                        )
                    }
                } else if (estadoAnterior != EstadoEnlace.CONECTADO &&
                    nuevoEstado == EstadoEnlace.CONECTADO
                ) {
                    ultimoMensajeValidoMs = SystemClock.elapsedRealtime()
                    iniciarLatido(nuevoEnlace, rolConexion)
                    iniciarVigilancia(nuevoEnlace)
                    if (rolConexion == RolApp.NODO) enviarEstadoInicial()
                }
                estadoAnterior = nuevoEstado
            }
        }

        trabajoMensajes = alcance.launch {
            nuevoEnlace.entrantes.collect { mensaje ->
                if (!esMensajeEntranteValido(rolConexion, mensaje)) return@collect
                ultimoMensajeValidoMs = SystemClock.elapsedRealtime()
                when (rolConexion) {
                    RolApp.NODO -> procesarComando(mensaje)
                    RolApp.PANEL -> procesarLectura(mensaje)
                }
            }
        }
    }

    private fun esMensajeEntranteValido(rolConexion: RolApp, mensaje: Mensaje): Boolean =
        if (rolConexion == RolApp.NODO) mensaje.permitidoParaNodo() else mensaje.permitidoParaPanel()

    private fun iniciarLatido(actual: Enlace, rolConexion: RolApp) {
        trabajoLatido?.cancel()
        trabajoLatido = alcance.launch {
            while (_estado.value.enlace == EstadoEnlace.CONECTADO) {
                delay(LATIDO_MS)
                if (_estado.value.enlace != EstadoEnlace.CONECTADO) break
                val latido = if (rolConexion == RolApp.NODO) {
                    // Reutiliza la lectura definida: el Panel la interpreta como vida y dato.
                    Mensaje("LECTURA", "rasgunos", _estado.value.rasgunos.toString())
                } else {
                    // El Nodo o el gateway responde con sus lecturas/estado.
                    Mensaje("ACK", "enlace", "LATIDO")
                }
                if (!enviarYEsperar(latido)) break
            }
        }
    }

    private fun iniciarVigilancia(actual: Enlace) {
        trabajoVigilancia?.cancel()
        trabajoVigilancia = alcance.launch {
            while (_estado.value.enlace == EstadoEnlace.CONECTADO) {
                delay(500)
                if (SystemClock.elapsedRealtime() - ultimoMensajeValidoMs >= TIEMPO_SIN_LATIDO_MS) {
                    _estado.value = _estado.value.copy(
                        aviso = "No se recibieron mensajes válidos por 6 segundos. Cerrando enlace por seguridad."
                    )
                    actual.cerrarConexionActual()
                    break
                }
            }
        }
    }

    private fun enviarEstadoInicial() {
        alcance.launch {
            if (enviarYEsperar(Mensaje("LECTURA", "rasgunos", "0"))) {
                enviarYEsperar(Mensaje("LECTURA", "comedero", "CERRADO"))
            }
        }
    }

    fun simularRasgun() {
        if (rol != RolApp.NODO || _estado.value.enlace != EstadoEnlace.CONECTADO) return

        val resultado = ventana.registrar(SystemClock.elapsedRealtime())
        if (resultado.ignorado) return

        _estado.value = _estado.value.copy(rasgunos = resultado.cantidad, error = null)
        enviar(Mensaje("LECTURA", "rasgunos", resultado.cantidad.toString()))

        if (resultado.confirmada) {
            generacionVentana += 1
            trabajoVentana?.cancel()
            trabajoVentana = null
        } else if (resultado.cantidad == 1) {
            programarVencimiento()
        }
    }

    private fun programarVencimiento() {
        trabajoVentana?.cancel()
        val miGeneracion = ++generacionVentana
        trabajoVentana = alcance.launch {
            delay(VENTANA_MS)
            if (miGeneracion == generacionVentana &&
                _estado.value.enlace == EstadoEnlace.CONECTADO &&
                ventana.vencer(SystemClock.elapsedRealtime())
            ) {
                _estado.value = _estado.value.copy(rasgunos = 0)
                enviar(Mensaje("LECTURA", "rasgunos", "0"))
            }
        }
    }

    fun dispensarComida() {
        if (rol != RolApp.PANEL || perfil?.rol != RolUsuario.OPERADOR) return
        if (_estado.value.enlace != EstadoEnlace.CONECTADO) return
        if (_estado.value.rasgunos != 3 || _estado.value.comedero != "CERRADO") return
        alcance.launch {
            if (enviarYEsperar(Mensaje("CMD", "comida", "DISPENSAR"))) {
                _estado.value = _estado.value.copy(comedero = "DISPENSANDO", ultimaEntrega = "en curso")
            }
        }
    }

    private fun procesarComando(mensaje: Mensaje) {
        if (modoConexion != ModoConexion.ANDROID_CIFRADO || !mensaje.permitidoParaNodo()) return
        if (!ventana.solicitudPendiente || _estado.value.comedero != "CERRADO" ||
            trabajoEntrega?.isActive == true
        ) return

        _estado.value = _estado.value.copy(comedero = "DISPENSANDO")
        enviar(Mensaje("LECTURA", "comedero", "DISPENSANDO"))
        contexto?.let {
            Notificaciones.mostrar(
                it,
                "Comando de alimentación recibido",
                "El comedero se abrirá durante aproximadamente 1,5 segundos."
            )
        }

        trabajoEntrega = alcance.launch {
            delay(ENTREGA_MS)
            if (_estado.value.enlace != EstadoEnlace.CONECTADO) return@launch

            _estado.value = _estado.value.copy(comedero = "CERRADO")
            if (!enviarYEsperar(Mensaje("LECTURA", "comedero", "CERRADO"))) return@launch
            if (!enviarYEsperar(Mensaje("ACK", "comida", "DISPENSADA"))) return@launch

            ventana.reiniciar()
            generacionVentana += 1
            _estado.value = _estado.value.copy(rasgunos = 0, comedero = "CERRADO")
            enviar(Mensaje("LECTURA", "rasgunos", "0"))
        }
    }

    private fun procesarLectura(mensaje: Mensaje) {
        if (mensaje.tipo == "LECTURA") {
            val usuario = perfil
            // El observador puede mirar en vivo, pero solo el operador guarda lecturas.
            if (usuario?.rol == RolUsuario.OPERADOR) {
                repositorioLecturas?.guardar(
                    uid = usuario.uid,
                    sesionId = sesionId,
                    origen = if (modoConexion == ModoConexion.ESP32_PRUEBA) "ESP32_COMEDERO" else "ANDROID_NODO",
                    mensaje = mensaje
                ) { error ->
                    _estado.value = _estado.value.copy(error = "No se pudo guardar la lectura: ${error.message ?: "revisa Firestore"}.")
                }
            }
            when (mensaje.clave) {
                "rasgunos" -> {
                    val cantidad = mensaje.valor.toIntOrNull() ?: return
                    val anterior = _estado.value.rasgunos
                    _estado.value = _estado.value.copy(rasgunos = cantidad)
                    if (anterior < 3 && cantidad == 3) {
                        contexto?.let {
                            Notificaciones.mostrar(
                                it,
                                "Solicitud de alimento",
                                "El gato está solicitando comida"
                            )
                        }
                    }
                }
                "comedero" -> _estado.value = _estado.value.copy(comedero = mensaje.valor)
            }
        } else if (mensaje.tipo == "ACK" && mensaje.clave == "comida") {
            when (mensaje.valor) {
                "DISPENSADA" -> _estado.value = _estado.value.copy(
                    rasgunos = 0,
                    comedero = "CERRADO",
                    ultimaEntrega = "confirmada"
                )
                "CANCELADA" -> _estado.value = _estado.value.copy(
                    rasgunos = 0,
                    comedero = "CERRADO",
                    ultimaEntrega = "cancelada por seguridad",
                    aviso = "La entrega se canceló y el comedero quedó cerrado."
                )
            }
        }
    }

    private fun enviar(mensaje: Mensaje) {
        alcance.launch { enviarYEsperar(mensaje) }
    }

    private suspend fun enviarYEsperar(mensaje: Mensaje): Boolean {
        val actual = enlace ?: return false
        val enviado = actual.enviar(mensaje)
        if (!enviado && _estado.value.enlace == EstadoEnlace.CONECTADO) actual.cerrarConexionActual()
        return enviado
    }

    fun notificarPerdidaDeRed() {
        if (_estado.value.enlace == EstadoEnlace.CONECTADO) enlace?.cerrarConexionActual()
    }

    fun desconectar() {
        detenerEnlace()
        generacionSesion += 1
        rol = null
        contexto = null
        ventana = VentanaRasgunos(VENTANA_MS)
        _estado.value = EstadoSesion(
            rolUsuario = perfil?.rol,
            notificacionesHabilitadas = _estado.value.notificacionesHabilitadas
        )
    }

    private fun detenerEnlace() {
        trabajoVentana?.cancel()
        trabajoEntrega?.cancel()
        trabajoLatido?.cancel()
        trabajoVigilancia?.cancel()
        trabajoLatido = null
        trabajoVigilancia = null
        trabajoEstado?.cancel()
        trabajoMensajes?.cancel()
        generacionVentana += 1
        enlace?.cerrar()
        enlace = null
        monitorWifi?.detener()
        monitorWifi = null
        ventana.reiniciar()
    }

    private fun volverAEstadoSeguro() {
        trabajoEntrega?.cancel()
        trabajoEntrega = null
        trabajoVentana?.cancel()
        trabajoVentana = null
        generacionVentana += 1
        ventana.reiniciar()
        _estado.value = _estado.value.copy(
            rasgunos = 0,
            comedero = "CERRADO",
            ultimaEntrega = if (_estado.value.ultimaEntrega == "en curso") "cancelada por seguridad" else _estado.value.ultimaEntrega
        )
    }

    private fun esIpv4(ip: String): Boolean {
        val partes = ip.trim().split('.')
        if (partes.size != 4) return false
        return partes.all { parte ->
            parte.isNotEmpty() && parte.all { it in '0'..'9' } &&
                parte.toIntOrNull()?.let { it in 0..255 } == true
        }
    }
}
