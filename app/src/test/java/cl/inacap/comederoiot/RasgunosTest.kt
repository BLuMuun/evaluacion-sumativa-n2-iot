package cl.inacap.comederoiot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RasgunosTest {
    @Test
    fun conservaLaVentanaFijaDesdeElPrimerRasgun() {
        val ventana = VentanaRasgunos()
        assertEquals(1, ventana.registrar(1_000).cantidad)
        assertEquals(2, ventana.registrar(5_999).cantidad)

        // El segundo rasguño no mueve el vencimiento: t=6 s ya pertenece a otra ventana.
        assertTrue(ventana.vencer(6_000))
        assertEquals(0, ventana.cantidad)
        assertEquals(1, ventana.registrar(6_100).cantidad)
    }

    @Test
    fun tercerRasgunConfirmaYLosSiguientesSeIgnoranHastaReinicio() {
        val ventana = VentanaRasgunos()
        ventana.registrar(100)
        ventana.registrar(1_000)
        val tercero = ventana.registrar(2_000)

        assertTrue(tercero.confirmada)
        assertEquals(3, ventana.cantidad)
        assertFalse(ventana.vencer(5_100))
        assertTrue(ventana.registrar(3_000).ignorado)

        ventana.reiniciar()
        assertEquals(1, ventana.registrar(4_000).cantidad)
    }
}
