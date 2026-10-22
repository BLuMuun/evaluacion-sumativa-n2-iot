package cl.inacap.comederoiot.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cl.inacap.comederoiot.EstadoSesion
import cl.inacap.comederoiot.datos.RolUsuario
import cl.inacap.comederoiot.enlace.EstadoEnlace

@Composable
fun PantallaPanel(
    estado: EstadoSesion,
    onDispensar: () -> Unit,
    onAbrirHistorial: () -> Unit,
    onVolverAConexion: () -> Unit,
    modifier: Modifier = Modifier
) {
    val esOperador = estado.rolUsuario == RolUsuario.OPERADOR
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Panel de control", style = MaterialTheme.typography.headlineMedium)
        Text("Rol: ${estado.rolUsuario?.documento?.uppercase() ?: "SIN PERFIL"}")
        EstadoTarjeta("Enlace", estado.enlace.etiqueta)
        estado.aviso?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        estado.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        EstadoTarjeta("Rasguños", "${estado.rasgunos} / 3")
        EstadoTarjeta("Comedero", estado.comedero)

        Button(
            enabled = esOperador && estado.enlace == EstadoEnlace.CONECTADO &&
                estado.comedero == "CERRADO" && estado.rasgunos == 3,
            onClick = onDispensar,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Dispensar comida") }
        if (!esOperador) {
            Text("Tu rol es Observador. Un administrador debe asignar Operador para dispensar.")
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Última entrega", style = MaterialTheme.typography.titleSmall)
            Text(estado.ultimaEntrega, style = MaterialTheme.typography.bodyLarge)
        }

        if (!estado.notificacionesHabilitadas) {
            Text("Notificaciones deshabilitadas; conexión y control siguen disponibles.")
        }
        OutlinedButton(onClick = onAbrirHistorial, modifier = Modifier.fillMaxWidth()) {
            Text("Historial de lecturas")
        }
        OutlinedButton(onClick = onVolverAConexion, modifier = Modifier.fillMaxWidth()) {
            Text(if (estado.enlace == EstadoEnlace.CONECTADO) "Desconectar" else "Volver a Conexión")
        }
    }
}
