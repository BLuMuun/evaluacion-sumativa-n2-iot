package cl.inacap.comederoiot.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import cl.inacap.comederoiot.datos.PerfilUsuario
import cl.inacap.comederoiot.seguridad.AutenticacionFirebase

@Composable
fun PantallaRegistro(
    firebaseConfigurado: Boolean,
    onRegistrar: (String, String, String, (PerfilUsuario?, String?) -> Unit) -> Unit,
    onRegistroCorrecto: (PerfilUsuario) -> Unit,
    onVolver: () -> Unit,
    modifier: Modifier = Modifier
) {
    var correo by rememberSaveable { mutableStateOf("") }
    var contrasena by remember { mutableStateOf("") }
    var confirmar by remember { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var cargando by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Crear cuenta", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text("La cuenta nueva tendrá el rol Observador. Un administrador puede asignar Operador.")
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = correo,
            onValueChange = { correo = it; error = null },
            label = { Text("Correo electrónico") },
            singleLine = true,
            enabled = !cargando,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = contrasena,
            onValueChange = { contrasena = it; error = null },
            label = { Text("Contraseña (mínimo ${AutenticacionFirebase.MINIMO_CONTRASENA} caracteres)") },
            singleLine = true,
            enabled = !cargando,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = confirmar,
            onValueChange = { confirmar = it; error = null },
            label = { Text("Confirmar contraseña") },
            singleLine = true,
            enabled = !cargando,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
        }
        Button(
            enabled = firebaseConfigurado && !cargando && correo.isNotBlank() && contrasena.isNotBlank(),
            onClick = {
                cargando = true
                error = null
                onRegistrar(correo, contrasena, confirmar) { perfil, mensaje ->
                    cargando = false
                    if (perfil != null) {
                        contrasena = ""
                        confirmar = ""
                        onRegistroCorrecto(perfil)
                    } else error = mensaje ?: "No se pudo crear la cuenta."
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            if (cargando) CircularProgressIndicator(Modifier.height(20.dp), strokeWidth = 2.dp)
            else Text("Crear cuenta")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onVolver, enabled = !cargando, modifier = Modifier.fillMaxWidth()) {
            Text("Volver al Login")
        }
    }
}
