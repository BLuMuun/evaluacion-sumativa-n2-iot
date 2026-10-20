package cl.inacap.comederoiot.enlace

import cl.inacap.comederoiot.seguridad.Cripto
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.crypto.SecretKey

enum class ModoConexion { ANDROID_CIFRADO, ESP32_PRUEBA }

/** Servidor TCP para Nodo Android y cliente para Panel Android o gateway ESP32. */
class WifiEnlace(
    private val codigoVinculacion: String?,
    private val modo: ModoConexion
) : Enlace {
    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _estado = MutableStateFlow(EstadoEnlace.DESCONECTADO)
    private val _entrantes = MutableSharedFlow<Mensaje>(extraBufferCapacity = 32)
    private val cerrojoEnvio = Mutex()

    @Volatile private var servidor: ServerSocket? = null
    @Volatile private var socket: Socket? = null
    @Volatile private var modoNodo = false
    @Volatile private var cerrado = false
    @Volatile private var claveSesion: SecretKey? = null
    @Volatile private var nonceHardware: String? = null
    private var secuenciaSalida = 0L
    private val secuenciaEntrante = SecuenciaEntrante()

    override val estado = _estado.asStateFlow()
    override val entrantes = _entrantes.asSharedFlow()

    override fun iniciarNodo() {
        if (modo != ModoConexion.ANDROID_CIFRADO || _estado.value != EstadoEnlace.DESCONECTADO || cerrado) return
        if (codigoVinculacion == null) {
            _estado.value = EstadoEnlace.ERROR
            return
        }
        modoNodo = true
        _estado.value = EstadoEnlace.ESPERANDO

        alcance.launch {
            try {
                val server = ServerSocket(PUERTO)
                servidor = server
                while (isActive && !cerrado) {
                    _estado.value = EstadoEnlace.ESPERANDO
                    val cliente = server.accept()
                    if (cerrado) {
                        cliente.close()
                        break
                    }
                    atender(cliente, paraNodo = true)
                }
            } catch (_: CancellationException) {
                throw CancellationException()
            } catch (_: IOException) {
                if (!cerrado) _estado.value = EstadoEnlace.ERROR
            } finally {
                runCatching { servidor?.close() }
                servidor = null
                if (cerrado) _estado.value = EstadoEnlace.DESCONECTADO
            }
        }
    }

    override fun conectarPanel(ipNodo: String) {
        if (_estado.value != EstadoEnlace.DESCONECTADO || cerrado) return
        if (modo == ModoConexion.ANDROID_CIFRADO && codigoVinculacion == null) {
            _estado.value = EstadoEnlace.ERROR
            return
        }
        modoNodo = false
        _estado.value = EstadoEnlace.CONECTANDO

        alcance.launch {
            val cliente = Socket()
            try {
                cliente.tcpNoDelay = true
                cliente.keepAlive = true
                cliente.connect(InetSocketAddress(ipNodo, PUERTO), TIEMPO_ESPERA_MS)
                if (cerrado) {
                    cliente.close()
                    return@launch
                }
                atender(cliente, paraNodo = false)
            } catch (_: CancellationException) {
                throw CancellationException()
            } catch (_: Exception) {
                if (!cerrado) _estado.value = EstadoEnlace.ERROR
                runCatching { cliente.close() }
            }
        }
    }

    private suspend fun atender(cliente: Socket, paraNodo: Boolean) {
        socket = cliente
        cliente.tcpNoDelay = true
        cliente.keepAlive = true
        try {
            val entrada = cliente.getInputStream()
            prepararSesion(entrada, cliente, paraNodo)
            secuenciaSalida = 0
            secuenciaEntrante.reiniciar()
            _estado.value = EstadoEnlace.CONECTADO

            while (alcance.isActive && !cerrado && !cliente.isClosed) {
                val linea = leerLineaLimitada(entrada) ?: break
                val trama = decodificarTrama(linea) ?: continue
                if (!secuenciaEntrante.aceptar(trama.secuencia)) continue
                val mensaje = Mensaje.desdeLinea(trama.contenido) ?: continue
                val direccionValida = if (paraNodo) mensaje.permitidoParaNodo() else mensaje.permitidoParaPanel()
                if (direccionValida) {
                    _entrantes.emit(mensaje)
                }
            }
        } catch (_: CancellationException) {
            throw CancellationException()
        } catch (_: IOException) {
            // EOF o fallo de red. El controlador aplica el estado seguro al observar el cierre.
        } catch (_: Exception) {
            // Una negociación o trama inválida no debe cerrar la aplicación.
        } finally {
            runCatching { cliente.close() }
            if (socket === cliente) socket = null
            claveSesion = null
            nonceHardware = null
            if (!cerrado) {
                _estado.value = if (modoNodo) EstadoEnlace.ESPERANDO else EstadoEnlace.DESCONECTADO
            }
        }
    }

    private suspend fun prepararSesion(entrada: java.io.InputStream, cliente: Socket, paraNodo: Boolean) {
        if (modo == ModoConexion.ANDROID_CIFRADO) {
            val codigo = codigoVinculacion ?: throw IOException("Falta el código de vinculación.")
            val sal = if (paraNodo) {
                Cripto.nuevaSalSesion().also { nuevaSal ->
                    escribirLinea(cliente, "HELLO;${Cripto.codificarSal(nuevaSal)}")
                }
            } else {
                val saludo = leerLineaLimitada(entrada) ?: throw IOException("No se recibió el saludo seguro.")
                val campos = saludo.split(';', limit = 3)
                if (campos.size != 2 || campos[0] != "HELLO") throw IOException("Saludo inválido.")
                Cripto.decodificarSal(campos[1]) ?: throw IOException("Sal inválida.")
            }
            claveSesion = Cripto.claveDesdeCodigo(codigo, sal)
        } else {
            if (paraNodo) throw IOException("El modo ESP32 solo funciona como cliente Panel.")
            val saludo = leerLineaLimitada(entrada) ?: throw IOException("No se recibió el saludo del gateway.")
            val campos = saludo.split(';', limit = 3)
            if (campos.size != 2 || campos[0] != "HELLO" || Cripto.decodificarSal(campos[1]) == null) {
                throw IOException("Saludo del gateway inválido.")
            }
            nonceHardware = campos[1]
        }
    }

    override suspend fun enviar(mensaje: Mensaje): Boolean = cerrojoEnvio.withLock {
        withContext(Dispatchers.IO) {
            val cliente = socket ?: return@withContext false
            try {
                val siguiente = secuenciaSalida + 1
                if (siguiente <= 0) return@withContext false
                val trama = if (modo == ModoConexion.ANDROID_CIFRADO) {
                    val clave = claveSesion ?: return@withContext false
                    val contenidoAutenticado = "$siguiente;${mensaje.aLinea()}"
                    "$siguiente;${Cripto.cifrar(contenidoAutenticado, clave)}"
                } else {
                    val nonce = nonceHardware ?: return@withContext false
                    "$nonce;$siguiente;${mensaje.aLinea()}"
                }
                if (trama.length > MAX_LINEA) return@withContext false
                escribirLinea(cliente, trama)
                secuenciaSalida = siguiente
                true
            } catch (_: Exception) {
                runCatching { cliente.close() }
                if (socket === cliente) socket = null
                if (!cerrado) _estado.value = if (modoNodo) EstadoEnlace.ESPERANDO else EstadoEnlace.DESCONECTADO
                false
            }
        }
    }

    private fun decodificarTrama(linea: String): Trama? {
        if (linea.length > MAX_LINEA) return null
        return if (modo == ModoConexion.ANDROID_CIFRADO) {
            val separador = linea.indexOf(';')
            if (separador <= 0) return null
            val secuencia = linea.substring(0, separador).toLongOrNull() ?: return null
            if (secuencia <= 0) return null
            val clave = claveSesion ?: return null
            val contenidoAutenticado = Cripto.descifrar(linea.substring(separador + 1), clave) ?: return null
            val separadorInterno = contenidoAutenticado.indexOf(';')
            if (separadorInterno <= 0) return null
            val secuenciaAutenticada = contenidoAutenticado.substring(0, separadorInterno).toLongOrNull() ?: return null
            if (secuenciaAutenticada != secuencia) return null
            Trama(secuencia, contenidoAutenticado.substring(separadorInterno + 1))
        } else {
            val primero = linea.indexOf(';')
            val segundo = if (primero >= 0) linea.indexOf(';', primero + 1) else -1
            if (primero <= 0 || segundo <= primero + 1) return null
            val nonce = nonceHardware ?: return null
            if (linea.substring(0, primero) != nonce) return null
            val secuencia = linea.substring(primero + 1, segundo).toLongOrNull() ?: return null
            if (secuencia <= 0) return null
            Trama(secuencia, linea.substring(segundo + 1))
        }
    }

    private fun escribirLinea(cliente: Socket, linea: String) {
        cliente.getOutputStream().write((linea + "\n").toByteArray(Charsets.UTF_8))
        cliente.getOutputStream().flush()
    }

    /** Corta la sesión TCP; el servidor Nodo queda escuchando para una reconexión del Panel. */
    override fun cerrarConexionActual() {
        runCatching { socket?.close() }
        socket = null
        if (!cerrado) _estado.value = if (modoNodo) EstadoEnlace.ESPERANDO else EstadoEnlace.DESCONECTADO
    }

    override fun cerrar() {
        cerrado = true
        runCatching { socket?.close() }
        runCatching { servidor?.close() }
        socket = null
        servidor = null
        alcance.cancel()
        _estado.value = EstadoEnlace.DESCONECTADO
    }

    private data class Trama(val secuencia: Long, val contenido: String)

    companion object {
        const val PUERTO = 5050
        private const val TIEMPO_ESPERA_MS = 5_000
        private const val MAX_LINEA = 256

        /** Recibe únicamente una IP local IPv4; devuelve null si el teléfono no está en Wi-Fi. */
        fun ipLocal(): String? = try {
            NetworkInterface.getNetworkInterfaces()?.toList()
                ?.filter {
                    it.isUp && !it.isLoopback &&
                        (it.name.startsWith("wlan") || it.name.startsWith("ap") || it.name.startsWith("swlan"))
                }
                ?.flatMap { it.inetAddresses.toList() }
                ?.filterIsInstance<Inet4Address>()
                ?.firstOrNull { it.isSiteLocalAddress && !it.isLoopbackAddress }
                ?.hostAddress
        } catch (_: Exception) {
            null
        }

        /** Lee una línea con límite de tamaño para no reservar memoria por una trama enorme. */
        private fun leerLineaLimitada(entrada: java.io.InputStream): String? {
            val bytes = ByteArrayOutputStream()
            var excedida = false
            while (true) {
                val siguiente = entrada.read()
                if (siguiente == -1) return null
                if (siguiente == '\n'.code) {
                    if (excedida) return ""
                    val trama = bytes.toByteArray()
                    val largo = if (trama.lastOrNull() == '\r'.code.toByte()) trama.size - 1 else trama.size
                    return String(trama, 0, largo, Charsets.UTF_8)
                }
                if (!excedida) {
                    if (bytes.size() >= MAX_LINEA) excedida = true else bytes.write(siguiente)
                }
            }
        }
    }
}
