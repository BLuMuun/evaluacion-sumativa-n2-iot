package cl.inacap.comederoiot.seguridad

import android.util.Base64
import cl.inacap.comederoiot.datos.RolUsuario
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Verifica la identidad y el rol Firebase del Panel antes de aceptar una orden en el Nodo. */
class AutorizadorComandosFirebase(private val projectId: String) {
    suspend fun resolverRol(idToken: String): RolUsuario? = withContext(Dispatchers.IO) {
        val uid = uidDelToken(idToken) ?: return@withContext null
        val project = projectId.takeIf { it.matches(Regex("[a-z][a-z0-9-]{4,28}[a-z0-9]")) }
            ?: return@withContext null
        val connection = URL(
            "https://firestore.googleapis.com/v1/projects/$project" +
                "/databases/(default)/documents/usuarios/${URLEncoder.encode(uid, "UTF-8")}" +
                "?mask.fieldPaths=rol"
        ).openConnection() as? HttpsURLConnection ?: return@withContext null

        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Authorization", "Bearer $idToken")
            connection.setRequestProperty("Accept", "application/json")

            if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
            val cuerpo = connection.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            val rol = JSONObject(cuerpo)
                .optJSONObject("fields")
                ?.optJSONObject("rol")
                ?.optString("stringValue")
                ?: return@withContext null
            RolUsuario.desdeDocumento(rol)
        } catch (_: Exception) {
            // Se rechaza la sesión si Firebase no puede confirmar el rol.
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun uidDelToken(idToken: String): String? {
        if (idToken.length !in TOKEN_MINIMO..TOKEN_MAXIMO) return null
        val partes = idToken.split('.')
        if (partes.size != 3) return null
        return try {
            val payload = JSONObject(String(Base64.decode(partes[1], Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING), Charsets.UTF_8))
            val audiencia = payload.optString("aud")
            val emisor = payload.optString("iss")
            val expira = payload.optLong("exp", 0L)
            val uid = payload.optString("user_id").ifBlank { payload.optString("sub") }

            if (audiencia != projectId || emisor != "https://securetoken.google.com/$projectId") return null
            if (expira <= System.currentTimeMillis() / 1000L) return null
            uid.takeIf { it.matches(Regex("[A-Za-z0-9_-]{1,128}")) }
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val TIMEOUT_MS = 5_000
        private const val TOKEN_MINIMO = 100
        private const val TOKEN_MAXIMO = 8_192
    }
}
