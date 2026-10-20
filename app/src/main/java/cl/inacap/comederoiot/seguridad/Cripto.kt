package cl.inacap.comederoiot.seguridad

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Criptografía del enlace Android–Android usando solo APIs estándar. */
object Cripto {
    private const val ITERACIONES = 210_000
    private const val BITS_CLAVE = 256
    private const val BYTES_SAL = 16
    private const val BYTES_IV = 12
    private const val BITS_ETIQUETA = 128
    private const val MAX_TAMANO_CIFRADO = 256
    private val aleatorio = SecureRandom()

    fun codigoValido(codigo: String): Boolean =
        codigo.length == 6 && codigo.all { it in '0'..'9' }

    /** Sal nueva por sesión, creada por el teléfono Nodo antes de aceptar mensajes. */
    fun nuevaSalSesion(): ByteArray = ByteArray(BYTES_SAL).also(aleatorio::nextBytes)

    fun codificarSal(sal: ByteArray): String {
        require(sal.size == BYTES_SAL)
        return Base64.getEncoder().encodeToString(sal)
    }

    fun decodificarSal(texto: String): ByteArray? {
        if (texto.length > 32) return null
        return try {
            Base64.getDecoder().decode(texto).takeIf { it.size == BYTES_SAL }
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    /** PBKDF2-HMAC-SHA256 convierte el código y la sal de sesión en una clave AES-256. */
    fun claveDesdeCodigo(codigo: String, sal: ByteArray): SecretKey {
        require(codigoValido(codigo)) { "El código debe tener exactamente 6 dígitos." }
        require(sal.size == BYTES_SAL) { "La sal de sesión debe tener 16 bytes." }
        val caracteres = codigo.toCharArray()
        val spec = PBEKeySpec(caracteres, sal, ITERACIONES, BITS_CLAVE)
        return try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).encoded
            SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
            caracteres.fill('\u0000')
        }
    }

    /** Base64(IV aleatorio + ciphertext y etiqueta GCM). */
    fun cifrar(texto: String, clave: SecretKey): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, clave, aleatorio)
        val iv = cipher.iv
        require(iv.size == BYTES_IV) { "Android devolvió un IV GCM inesperado." }
        val cifrado = cipher.doFinal(texto.toByteArray(Charsets.UTF_8))
        val paquete = ByteArray(iv.size + cifrado.size)
        iv.copyInto(paquete)
        cifrado.copyInto(paquete, destinationOffset = iv.size)
        return Base64.getEncoder().encodeToString(paquete)
    }

    /** Devuelve null ante Base64 inválido, clave distinta, truncamiento o datos alterados. */
    fun descifrar(linea: String, clave: SecretKey): String? {
        if (linea.isEmpty() || linea.length > MAX_TAMANO_CIFRADO) return null
        return try {
            val paquete = Base64.getDecoder().decode(linea)
            if (paquete.size < BYTES_IV + BITS_ETIQUETA / 8) return null

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val parametros = GCMParameterSpec(BITS_ETIQUETA, paquete, 0, BYTES_IV)
            cipher.init(Cipher.DECRYPT_MODE, clave, parametros)
            String(cipher.doFinal(paquete, BYTES_IV, paquete.size - BYTES_IV), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }
}
