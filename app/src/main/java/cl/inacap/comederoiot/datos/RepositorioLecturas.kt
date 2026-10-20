package cl.inacap.comederoiot.datos

import cl.inacap.comederoiot.enlace.Mensaje
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

/** Persiste solo lecturas válidas y consulta las últimas 50 para la pantalla Historial. */
class RepositorioLecturas(private val base: FirebaseFirestore) {
    fun guardar(
        uid: String,
        sesionId: String,
        origen: String,
        mensaje: Mensaje,
        alFallar: (Exception) -> Unit = {}
    ) {
        if (mensaje.tipo != "LECTURA" || Mensaje.desdeLinea(mensaje.aLinea()) != mensaje) return
        val campos = mapOf(
            "usuarioUid" to uid,
            "momento" to FieldValue.serverTimestamp(),
            "clave" to mensaje.clave,
            "valor" to mensaje.valor,
            "origen" to origen.take(32),
            "sesionId" to sesionId.take(64)
        )
        base.collection(COLECCION).add(campos).addOnFailureListener { error -> alFallar(error) }
    }

    fun cargarUltimas(alTerminar: (Result<List<Lectura>>) -> Unit) {
        base.collection(COLECCION)
            .orderBy("momento", Query.Direction.DESCENDING)
            .limit(LIMITE.toLong())
            .get()
            .addOnSuccessListener { resultado ->
                val lecturas = resultado.documents.mapNotNull { documento ->
                    val clave = documento.getString("clave") ?: return@mapNotNull null
                    val valor = documento.getString("valor") ?: return@mapNotNull null
                    val origen = documento.getString("origen") ?: return@mapNotNull null
                    val sesionId = documento.getString("sesionId") ?: return@mapNotNull null
                    if (Mensaje.desdeLinea("LECTURA;$clave;$valor") == null) return@mapNotNull null
                    Lectura(
                        id = documento.id,
                        momento = (documento.get("momento") as? Timestamp)?.toDate(),
                        clave = clave,
                        valor = valor,
                        origen = origen,
                        sesionId = sesionId
                    )
                }
                alTerminar(Result.success(lecturas))
            }
            .addOnFailureListener { error -> alTerminar(Result.failure(error)) }
    }

    companion object {
        const val COLECCION = "lecturas"
        const val LIMITE = 50
    }
}
