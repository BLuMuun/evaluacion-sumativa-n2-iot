package cl.inacap.comederoiot.seguridad

import android.content.Context
import android.os.SystemClock
import android.util.Patterns
import cl.inacap.comederoiot.datos.PerfilUsuario
import cl.inacap.comederoiot.datos.RepositorioLecturas
import cl.inacap.comederoiot.datos.RepositorioUsuarios
import cl.inacap.comederoiot.datos.RolUsuario
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/** Registro e inicio de sesión con Firebase Authentication y perfil Firestore. */
class AutenticacionFirebase(contexto: Context) {
    private val app = FirebaseApp.initializeApp(contexto.applicationContext)
    private val auth = app?.let(FirebaseAuth::getInstance)
    private val firestore = app?.let(FirebaseFirestore::getInstance)
    private val usuarios = firestore?.let(::RepositorioUsuarios)
    private val autorizadorComandos = app?.options?.projectId?.let(::AutorizadorComandosFirebase)
    private var intentosIncorrectos = 0
    private var bloqueadoHastaMs = 0L

    val estaConfigurada: Boolean
        get() = auth != null && usuarios != null

    val haySesionActiva: Boolean
        get() = auth?.currentUser != null

    fun nuevoRepositorioLecturas(): RepositorioLecturas? = firestore?.let(::RepositorioLecturas)

    /** Token temporal del usuario autenticado; no se guarda ni se registra en archivos. */
    suspend fun tokenActual(): String? {
        val usuario = auth?.currentUser ?: return null
        return suspendCancellableCoroutine { continuation ->
            usuario.getIdToken(false)
                .addOnSuccessListener { resultado ->
                    if (continuation.isActive) continuation.resume(resultado.token)
                }
                .addOnFailureListener {
                    if (continuation.isActive) continuation.resume(null)
                }
        }
    }

    /** El Nodo verifica este token contra Firestore y sus reglas de acceso por usuario. */
    suspend fun rolDeToken(token: String): RolUsuario? =
        autorizadorComandos?.resolverRol(token)

    fun iniciarSesion(correo: String, contrasena: String, alTerminar: (PerfilUsuario?, String?) -> Unit) {
        val instancia = auth
        val repoUsuarios = usuarios
        if (instancia == null || repoUsuarios == null) {
            alTerminar(null, "Firebase aún no está configurado. Revisa docs/FIREBASE_SETUP.md.")
            return
        }
        val segundosRestantes = ((bloqueadoHastaMs - SystemClock.elapsedRealtime()).coerceAtLeast(0) + 999) / 1000
        if (segundosRestantes > 0) {
            alTerminar(null, "Demasiados intentos. Espera $segundosRestantes segundos antes de volver a intentar.")
            return
        }
        val correoLimpio = correo.trim()
        if (!Patterns.EMAIL_ADDRESS.matcher(correoLimpio).matches()) {
            alTerminar(null, "Escribe un correo electrónico válido.")
            return
        }
        if (contrasena.isBlank()) {
            alTerminar(null, "Escribe tu contraseña.")
            return
        }

        instancia.signInWithEmailAndPassword(correoLimpio, contrasena)
            .addOnCompleteListener { tarea ->
                if (!tarea.isSuccessful) {
                    if (esCredencialIncorrecta(tarea.exception)) {
                        registrarFallo()
                        val segundos = ((bloqueadoHastaMs - SystemClock.elapsedRealtime()).coerceAtLeast(0) + 999) / 1000
                        val mensaje = if (segundos > 0) {
                            "Demasiados intentos. Espera $segundos segundos antes de volver a intentar."
                        } else mensajeDeError(tarea.exception)
                        alTerminar(null, mensaje)
                    } else {
                        alTerminar(null, mensajeDeError(tarea.exception))
                    }
                    return@addOnCompleteListener
                }
                val usuario = instancia.currentUser
                if (usuario == null) {
                    alTerminar(null, "Firebase no devolvió el perfil de la sesión.")
                    return@addOnCompleteListener
                }
                repoUsuarios.cargarOCrear(usuario.uid, usuario.email ?: correoLimpio) { resultado ->
                    resultado.onSuccess {
                        intentosIncorrectos = 0
                        bloqueadoHastaMs = 0L
                        alTerminar(it, null)
                    }.onFailure {
                        alTerminar(null, "No se pudo cargar el rol desde Firestore: ${it.message ?: "revisa la conexión"}.")
                    }
                }
            }
    }

    fun registrar(correo: String, contrasena: String, confirmar: String, alTerminar: (PerfilUsuario?, String?) -> Unit) {
        val instancia = auth
        val repoUsuarios = usuarios
        if (instancia == null || repoUsuarios == null) {
            alTerminar(null, "Firebase aún no está configurado. Revisa docs/FIREBASE_SETUP.md.")
            return
        }
        val correoLimpio = correo.trim()
        if (!Patterns.EMAIL_ADDRESS.matcher(correoLimpio).matches()) {
            alTerminar(null, "Escribe un correo electrónico válido.")
            return
        }
        if (contrasena.length < MINIMO_CONTRASENA) {
            alTerminar(null, "La contraseña debe tener al menos $MINIMO_CONTRASENA caracteres.")
            return
        }
        if (contrasena != confirmar) {
            alTerminar(null, "Las contraseñas no coinciden.")
            return
        }

        instancia.createUserWithEmailAndPassword(correoLimpio, contrasena)
            .addOnCompleteListener { tarea ->
                if (!tarea.isSuccessful) {
                    alTerminar(null, mensajeDeError(tarea.exception))
                    return@addOnCompleteListener
                }
                val usuario = instancia.currentUser
                if (usuario == null) {
                    alTerminar(null, "La cuenta se creó, pero Firebase no devolvió la sesión.")
                    return@addOnCompleteListener
                }
                repoUsuarios.cargarOCrear(usuario.uid, correoLimpio) { resultado ->
                    resultado.onSuccess { alTerminar(it, null) }
                        .onFailure {
                            instancia.signOut()
                            alTerminar(null, "La cuenta se creó, pero no se guardó su perfil. Inicia sesión para reintentar.")
                        }
                }
            }
    }

    fun cargarPerfilActual(alTerminar: (PerfilUsuario?, String?) -> Unit) {
        val usuario = auth?.currentUser
        val repoUsuarios = usuarios
        if (usuario == null || repoUsuarios == null) {
            alTerminar(null, null)
            return
        }
        repoUsuarios.cargarOCrear(usuario.uid, usuario.email.orEmpty()) { resultado ->
            resultado.onSuccess { alTerminar(it, null) }
                .onFailure { alTerminar(null, "No se pudo cargar el perfil. Comprueba Firebase y la conexión.") }
        }
    }

    fun cerrarSesion() {
        auth?.signOut()
    }

    private fun registrarFallo() {
        intentosIncorrectos += 1
        if (intentosIncorrectos >= INTENTOS_MAXIMOS) {
            intentosIncorrectos = 0
            bloqueadoHastaMs = SystemClock.elapsedRealtime() + DURACION_BLOQUEO_MS
        }
    }

    private fun esCredencialIncorrecta(error: Exception?): Boolean {
        val codigo = (error as? FirebaseAuthException)?.errorCode.orEmpty()
        return codigo in setOf("ERROR_USER_NOT_FOUND", "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL") ||
            codigo == "INVALID_LOGIN_CREDENTIALS"
    }

    private fun mensajeDeError(error: Exception?): String {
        val codigo = (error as? FirebaseAuthException)?.errorCode.orEmpty()
        return when (codigo) {
            "ERROR_INVALID_EMAIL" -> "El correo electrónico no tiene un formato válido."
            "ERROR_USER_DISABLED" -> "Esta cuenta está deshabilitada."
            "ERROR_TOO_MANY_REQUESTS" -> "Firebase limitó los intentos. Espera un momento y vuelve a probar."
            "ERROR_NETWORK_REQUEST_FAILED" -> "No se pudo conectar con Firebase. Revisa Internet e inténtalo otra vez."
            "ERROR_EMAIL_ALREADY_IN_USE" -> "Ya existe una cuenta con ese correo."
            "ERROR_WEAK_PASSWORD" -> "La contraseña no cumple la seguridad requerida."
            "ERROR_USER_NOT_FOUND", "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL",
            "INVALID_LOGIN_CREDENTIALS" -> "Correo o contraseña incorrectos."
            else -> "No se pudo completar la operación. Revisa tus datos y la conexión a Internet."
        }
    }

    companion object {
        const val MINIMO_CONTRASENA = 8
        private const val INTENTOS_MAXIMOS = 5
        private const val DURACION_BLOQUEO_MS = 30_000L
    }
}
