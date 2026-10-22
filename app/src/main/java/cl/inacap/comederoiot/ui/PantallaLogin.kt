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
import androidx.compose.material3.Card
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

@Composable
fun PantallaLogin(
    firebaseConfigurado: Boolean,
    onIniciarSesion: (String, String, (PerfilUsuario?, String?) -> Unit) -> Unit,
    onLoginCorrecto: (PerfilUsuario) -> Unit,
    onCrearCuenta: () -> Unit,
    modifier: Modifier = Modifier,
    avisoInicial: String? = null
) {
    var correo by rememberSaveable { mutableStateOf("") }
    var contrasena by remember { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var cargando by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Comedero IoT", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text("Iniciar sesión", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(20.dp))
        avisoInicial?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
        }

        OutlinedTextField(
            value = correo,
            onValueChange = { correo = it; error = null },
            label = { Text("Usuario (correo electrónico)") },
            supportingText = { Text("Usa una cuenta registrada en Firebase Authentication.") },
            singleLine = true,
            enabled = !cargando,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = contrasena,
            onValueChange = { contrasena = it; error = null },
            label = { Text("Contraseña") },
            singleLine = true,
            enabled = !cargando,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(16.dp))
        if (!firebaseConfigurado) {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Falta configurar Firebase", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Agrega app/google-services.json siguiendo docs/FIREBASE_SETUP.md. " +
                            "Ese archivo permanece local y no se sube a GitHub."
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
        }

        Button(
            enabled = firebaseConfigurado && !cargando && correo.isNotBlank() && contrasena.isNotBlank(),
            onClick = {
                error = null
                cargando = true
                onIniciarSesion(correo, contrasena) { perfil, mensaje ->
                    cargando = false
                    if (perfil != null) {
                        contrasena = ""
                        onLoginCorrecto(perfil)
                    } else {
                        error = mensaje ?: "No se pudo iniciar sesión."
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            if (cargando) CircularProgressIndicator(Modifier.height(20.dp), strokeWidth = 2.dp)
            else Text("Iniciar sesión")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onCrearCuenta,
            enabled = firebaseConfigurado && !cargando,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Crear cuenta") }
    }
}
