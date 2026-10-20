package cl.inacap.comederoiot

/** Lógica pura del contador; la ventana empieza con el primer rasguño y no se desplaza. */
class VentanaRasgunos(private val duracionMs: Long = 5_000L) {
    var cantidad: Int = 0
        private set
    private var inicioMs: Long? = null

    val solicitudPendiente: Boolean
        get() = cantidad == 3

    fun registrar(instanteMs: Long): Resultado {
        if (solicitudPendiente) return Resultado(cantidad, ignorado = true, confirmada = false)

        val inicio = inicioMs
        if (cantidad == 0 || inicio == null || instanteMs - inicio >= duracionMs) {
            cantidad = 1
            inicioMs = instanteMs
            return Resultado(cantidad, ignorado = false, confirmada = false)
        }

        cantidad += 1
        val confirmada = solicitudPendiente
        return Resultado(cantidad, ignorado = false, confirmada = confirmada)
    }

    /** Vence a los 5 s desde el primero; llamar desde un temporizador no bloqueante. */
    fun vencer(instanteMs: Long): Boolean {
        val inicio = inicioMs ?: return false
        if (cantidad !in 1..2 || instanteMs - inicio < duracionMs) return false
        reiniciar()
        return true
    }

    fun reiniciar() {
        cantidad = 0
        inicioMs = null
    }

    data class Resultado(val cantidad: Int, val ignorado: Boolean, val confirmada: Boolean)
}
