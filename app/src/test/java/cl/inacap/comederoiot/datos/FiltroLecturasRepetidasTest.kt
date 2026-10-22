package cl.inacap.comederoiot.datos

import cl.inacap.comederoiot.enlace.Mensaje
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FiltroLecturasRepetidasTest {
    private val filtro = FiltroLecturasRepetidas()

    @Test
    fun `guarda cambios y omite latidos repetidos`() {
        assertTrue(filtro.debeGuardar(Mensaje("LECTURA", "rasgunos", "0")))
        assertFalse(filtro.debeGuardar(Mensaje("LECTURA", "rasgunos", "0")))
        assertTrue(filtro.debeGuardar(Mensaje("LECTURA", "rasgunos", "1")))
        assertTrue(filtro.debeGuardar(Mensaje("LECTURA", "rasgunos", "2")))
        assertTrue(filtro.debeGuardar(Mensaje("LECTURA", "rasgunos", "3")))
        assertFalse(filtro.debeGuardar(Mensaje("LECTURA", "rasgunos", "3")))

        // La confirmación de entrega vuelve a cero y se conserva como cambio real.
        assertTrue(filtro.debeGuardar(Mensaje("LECTURA", "rasgunos", "0")))
    }

    @Test
    fun `filtra cada clave por separado y solo acepta lecturas`() {
        assertTrue(filtro.debeGuardar(Mensaje("LECTURA", "comedero", "CERRADO")))
        assertTrue(filtro.debeGuardar(Mensaje("LECTURA", "rasgunos", "0")))
        assertFalse(filtro.debeGuardar(Mensaje("ACK", "comida", "DISPENSADA")))
        assertFalse(filtro.debeGuardar(Mensaje("LECTURA", "comedero", "CERRADO")))
        assertTrue(filtro.debeGuardar(Mensaje("LECTURA", "comedero", "DISPENSANDO")))
        assertTrue(filtro.debeGuardar(Mensaje("LECTURA", "comedero", "CERRADO")))
    }

    @Test
    fun `reiniciar permite guardar el estado inicial de otra sesion`() {
        val estadoInicial = Mensaje("LECTURA", "rasgunos", "0")
        assertTrue(filtro.debeGuardar(estadoInicial))
        assertFalse(filtro.debeGuardar(estadoInicial))

        filtro.reiniciar()

        assertTrue(filtro.debeGuardar(estadoInicial))
    }

    @Test
    fun `permite reintentar solo el valor que fallo`() {
        val inicial = Mensaje("LECTURA", "rasgunos", "0")
        val siguiente = Mensaje("LECTURA", "rasgunos", "1")
        assertTrue(filtro.debeGuardar(inicial))

        filtro.permitirReintento(inicial)

        assertTrue(filtro.debeGuardar(inicial))
        assertTrue(filtro.debeGuardar(siguiente))
        filtro.permitirReintento(inicial)
        assertFalse(filtro.debeGuardar(siguiente))
    }
}
