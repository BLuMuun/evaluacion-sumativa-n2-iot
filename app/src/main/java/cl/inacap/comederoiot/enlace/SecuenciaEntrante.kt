package cl.inacap.comederoiot.enlace

/** Anti-repetición por dirección y por conexión; cada sesión comienza en cero. */
class SecuenciaEntrante {
    private var ultimaAceptada = 0L

    fun aceptar(secuencia: Long): Boolean {
        if (secuencia <= ultimaAceptada || secuencia <= 0L) return false
        ultimaAceptada = secuencia
        return true
    }

    fun reiniciar() {
        ultimaAceptada = 0L
    }
}
