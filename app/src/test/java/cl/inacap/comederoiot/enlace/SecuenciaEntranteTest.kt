package cl.inacap.comederoiot.enlace

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecuenciaEntranteTest {
    @Test
    fun aceptaSoloSecuenciaCrecienteYReiniciaPorSesion() {
        val control = SecuenciaEntrante()
        assertTrue(control.aceptar(1))
        assertTrue(control.aceptar(3))
        assertFalse(control.aceptar(3))
        assertFalse(control.aceptar(2))
        assertFalse(control.aceptar(0))

        control.reiniciar()
        assertTrue(control.aceptar(1))
    }
}
