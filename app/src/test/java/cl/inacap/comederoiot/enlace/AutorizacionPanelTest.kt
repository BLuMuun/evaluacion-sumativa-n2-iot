package cl.inacap.comederoiot.enlace

import cl.inacap.comederoiot.datos.RolUsuario
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutorizacionPanelTest {
    @Test
    fun observadorPuedeMonitorearPeroNodoDescartaSuComando() = runBlocking {
        val nodo = nuevoNodo { token ->
            if (token == "token-observador") RolUsuario.OBSERVADOR else null
        }
        nodo.iniciarNodo()
        val puerto = esperarPuerto(nodo)
        val panel = nuevoPanel("token-observador", RolUsuario.OBSERVADOR, puerto)

        try {
            panel.conectarPanel("127.0.0.1")
            esperarConectados(nodo, panel)
            assertEquals(RolUsuario.OBSERVADOR, nodo.rolPanelAutorizado.value)
            val recibidos = mutableListOf<Mensaje>()
            val collector = launch(start = CoroutineStart.UNDISPATCHED) {
                nodo.entrantes.collect { recibidos += it }
            }

            assertTrue(panel.enviar(Mensaje("CMD", "comida", "DISPENSAR")))
            delay(250)
            assertTrue(recibidos.isEmpty())

            val latido = Mensaje("ACK", "enlace", "LATIDO")
            assertTrue(panel.enviar(latido))
            withTimeout(2_000) { while (recibidos.isEmpty()) delay(10) }
            assertEquals(listOf(latido), recibidos)
            collector.cancel()
        } finally {
            panel.cerrar()
            nodo.cerrar()
        }
    }

    @Test
    fun operadorVerificadoPuedeEnviarComando() = runBlocking {
        val nodo = nuevoNodo { token ->
            if (token == "token-operador") RolUsuario.OPERADOR else null
        }
        nodo.iniciarNodo()
        val panel = nuevoPanel("token-operador", RolUsuario.OPERADOR, esperarPuerto(nodo))

        try {
            panel.conectarPanel("127.0.0.1")
            esperarConectados(nodo, panel)
            assertEquals(RolUsuario.OPERADOR, nodo.rolPanelAutorizado.value)

            val comando = Mensaje("CMD", "comida", "DISPENSAR")
            val recibidos = mutableListOf<Mensaje>()
            val collector = launch(start = CoroutineStart.UNDISPATCHED) {
                nodo.entrantes.collect { recibidos += it }
            }
            assertTrue(panel.enviar(comando))
            withTimeout(2_000) { while (recibidos.isEmpty()) delay(10) }
            assertEquals(listOf(comando), recibidos)
            collector.cancel()
        } finally {
            panel.cerrar()
            nodo.cerrar()
        }
    }

    @Test
    fun tokenNoValidadoNoEstableceUnaSesion() = runBlocking {
        val nodo = nuevoNodo { null }
        nodo.iniciarNodo()
        val panel = nuevoPanel("token-desconocido", RolUsuario.OBSERVADOR, esperarPuerto(nodo))

        try {
            panel.conectarPanel("127.0.0.1")
            withTimeout(4_000) {
                while (panel.estado.value != EstadoEnlace.ERROR) delay(10)
            }
            assertNotEquals(EstadoEnlace.CONECTADO, nodo.estado.value)
            assertEquals(null, nodo.rolPanelAutorizado.value)
        } finally {
            panel.cerrar()
            nodo.cerrar()
        }
    }

    private fun nuevoNodo(resolver: suspend (String) -> RolUsuario?) = WifiEnlace(
        codigoVinculacion = "123456",
        modo = ModoConexion.ANDROID_CIFRADO,
        resolverRolPanel = resolver,
        puerto = 0
    )

    private fun nuevoPanel(token: String, rol: RolUsuario, puerto: Int) = WifiEnlace(
        codigoVinculacion = "123456",
        modo = ModoConexion.ANDROID_CIFRADO,
        obtenerTokenPanel = { token },
        rolPanelEsperado = rol,
        puerto = puerto
    )

    private suspend fun esperarPuerto(nodo: WifiEnlace): Int = withTimeout(2_000) {
        while (nodo.puertoEscucha == 0) delay(10)
        nodo.puertoEscucha
    }

    private suspend fun esperarConectados(nodo: WifiEnlace, panel: WifiEnlace) {
        withTimeout(5_000) {
            while (nodo.estado.value != EstadoEnlace.CONECTADO ||
                panel.estado.value != EstadoEnlace.CONECTADO
            ) delay(10)
        }
    }
}
