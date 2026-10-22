package cl.inacap.comederoiot.datos

import cl.inacap.comederoiot.enlace.Mensaje

/** Evita persistir varias veces el mismo estado mientras el latido sigue activo. */
class FiltroLecturasRepetidas {
    private val ultimosValores = mutableMapOf<String, String>()

    /** Devuelve true solo al recibir una lectura cuyo valor cambió para esa clave. */
    fun debeGuardar(mensaje: Mensaje): Boolean {
        if (mensaje.tipo != "LECTURA") return false

        val valorAnterior = ultimosValores[mensaje.clave]
        if (valorAnterior == mensaje.valor) return false

        ultimosValores[mensaje.clave] = mensaje.valor
        return true
    }

    /** Permite que el mismo estado vuelva a guardarse si Firestore rechazó la escritura. */
    fun permitirReintento(mensaje: Mensaje) {
        if (mensaje.tipo == "LECTURA" && ultimosValores[mensaje.clave] == mensaje.valor) {
            ultimosValores.remove(mensaje.clave)
        }
    }

    /** Cada conexión comienza un historial lógico nuevo. */
    fun reiniciar() {
        ultimosValores.clear()
    }
}
