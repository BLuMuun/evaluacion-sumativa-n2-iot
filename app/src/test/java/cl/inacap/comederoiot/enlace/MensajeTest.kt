package cl.inacap.comederoiot.enlace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MensajeTest {
    @Test
    fun aceptaSoloMensajesDelProtocolo() {
        val lectura = Mensaje.desdeLinea("LECTURA;rasgunos;3")
        assertEquals(Mensaje("LECTURA", "rasgunos", "3"), lectura)
        assertTrue(Mensaje.desdeLinea("CMD;comida;DISPENSAR")!!.permitidoParaNodo())
        assertTrue(Mensaje.desdeLinea("ACK;comida;DISPENSADA")!!.permitidoParaPanel())
        assertTrue(Mensaje.desdeLinea("ACK;comida;CANCELADA")!!.permitidoParaPanel())
        assertTrue(Mensaje.desdeLinea("ACK;enlace;LATIDO")!!.permitidoParaNodo())
        assertTrue(Mensaje.desdeLinea("ACK;enlace;LATIDO")!!.permitidoParaPanel())
    }

    @Test
    fun descartaCamposTiposClavesYValoresInvalidos() {
        listOf(
            "LECTURA;rasgunos",
            "LECTURA;rasgunos;1;extra",
            "DESCONOCIDO;rasgunos;1",
            "LECTURA;desconocida;1",
            "LECTURA;rasgunos;4",
            "LECTURA;rasgunos;12345678901234567",
            "CMD;comida;DISPENSADA",
            "LECTURA;rasgunos;"
        ).forEach { assertNull("Debe descartarse: $it", Mensaje.desdeLinea(it)) }
    }
}
