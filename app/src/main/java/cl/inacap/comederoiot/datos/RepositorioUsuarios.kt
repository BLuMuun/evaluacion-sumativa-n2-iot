package cl.inacap.comederoiot.datos

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

/** Lee el perfil Firestore y crea uno observador si corresponde. La app no puede elevar su rol. */
class RepositorioUsuarios(private val base: FirebaseFirestore) {
    fun cargarOCrear(uid: String, correo: String, alTerminar: (Result<PerfilUsuario>) -> Unit) {
        val perfil = base.collection(COLECCION).document(uid)
        perfil.get()
            .addOnSuccessListener { documento ->
                if (documento.exists()) {
                    val rol = RolUsuario.desdeDocumento(documento.getString("rol"))
                    if (rol == null) {
                        alTerminar(Result.failure(IllegalStateException("El rol del usuario no es válido.")))
                    } else {
                        alTerminar(Result.success(PerfilUsuario(uid, correo, rol)))
                    }
                } else {
                    val campos = mapOf(
                        "correo" to correo,
                        "rol" to RolUsuario.OBSERVADOR.documento,
                        "creadoEn" to FieldValue.serverTimestamp()
                    )
                    perfil.set(campos)
                        .addOnSuccessListener {
                            alTerminar(Result.success(PerfilUsuario(uid, correo, RolUsuario.OBSERVADOR)))
                        }
                        .addOnFailureListener { error -> alTerminar(Result.failure(error)) }
                }
            }
            .addOnFailureListener { error -> alTerminar(Result.failure(error)) }
    }

    companion object {
        const val COLECCION = "usuarios"
    }
}
