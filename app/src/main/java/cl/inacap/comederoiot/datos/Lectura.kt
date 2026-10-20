package cl.inacap.comederoiot.datos

import java.util.Date

data class Lectura(
    val id: String,
    val momento: Date?,
    val clave: String,
    val valor: String,
    val origen: String,
    val sesionId: String
)
