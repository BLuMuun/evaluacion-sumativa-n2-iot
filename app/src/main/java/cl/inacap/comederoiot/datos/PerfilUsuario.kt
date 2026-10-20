package cl.inacap.comederoiot.datos

enum class RolUsuario(val documento: String) {
    OPERADOR("operador"),
    OBSERVADOR("observador");

    companion object {
        fun desdeDocumento(valor: String?): RolUsuario? = entries.firstOrNull { it.documento == valor }
    }
}

data class PerfilUsuario(
    val uid: String,
    val correo: String,
    val rol: RolUsuario
)
