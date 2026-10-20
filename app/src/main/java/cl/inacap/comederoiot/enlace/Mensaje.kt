package cl.inacap.comederoiot.enlace

/** Mensaje de aplicación descifrado. La secuencia pertenece a la envoltura de transporte. */
data class Mensaje(val tipo: String, val clave: String, val valor: String) {
    fun aLinea(): String = "$tipo;$clave;$valor"

    fun permitidoParaNodo(): Boolean =
        tipo == "CMD" && clave == "comida" && valor == "DISPENSAR" ||
            tipo == "ACK" && clave == "enlace" && valor == "LATIDO"

    fun permitidoParaPanel(): Boolean = when {
        tipo == "LECTURA" && clave == "rasgunos" -> valor in RASGUNOS
        tipo == "LECTURA" && clave == "comedero" -> valor in ESTADOS_COMEDERO
        tipo == "ACK" && clave == "comida" -> valor in ESTADOS_ENTREGA
        tipo == "ACK" && clave == "enlace" -> valor == "LATIDO"
        else -> false
    }

    companion object {
        private const val MAX_CAMPO = 16
        private val TIPOS = setOf("LECTURA", "CMD", "ACK")
        private val RASGUNOS = setOf("0", "1", "2", "3")
        private val ESTADOS_COMEDERO = setOf("CERRADO", "DISPENSANDO")
        private val ESTADOS_ENTREGA = setOf("DISPENSADA", "CANCELADA")

        /** Descarta formatos, claves y valores desconocidos sin propagar excepciones. */
        fun desdeLinea(linea: String): Mensaje? {
            if (linea.isEmpty() || linea.length > 48 || linea.any(Char::isISOControl)) return null

            val campos = linea.split(';', limit = 4)
            if (campos.size != 3) return null

            val (tipo, clave, valor) = campos
            if (tipo !in TIPOS || clave.isEmpty() || valor.isEmpty()) return null
            if (tipo.length > MAX_CAMPO || clave.length > MAX_CAMPO || valor.length > MAX_CAMPO) return null

            val mensaje = Mensaje(tipo, clave, valor)
            return mensaje.takeIf { it.permitidoParaNodo() || it.permitidoParaPanel() }
        }
    }
}
