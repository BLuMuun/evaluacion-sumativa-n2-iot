package cl.inacap.comederoiot.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cl.inacap.comederoiot.EstadoSesion
import cl.inacap.comederoiot.enlace.EstadoEnlace

@Composable
fun PantallaNodo(
    estado: EstadoSesion,
    onSimularRasgun: () -> Unit,
    onDesconectar: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text("Nodo IoT", style = MaterialTheme.typography.headlineMedium)
        EstadoTarjeta("Enlace", estado.enlace.etiqueta)
        estado.aviso?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        estado.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Rasguños", style = MaterialTheme.typography.titleMedium)
                Text("${estado.rasgunos} / 3", style = MaterialTheme.typography.displaySmall)
                Text("Tres rasguños en una ventana fija de 5 segundos crean una solicitud.")
            }
        }

        Button(
            enabled = estado.enlace == EstadoEnlace.CONECTADO && estado.rasgunos < 3,
            onClick = onSimularRasgun,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (estado.rasgunos == 3) "Solicitud pendiente" else "Simular rasguño")
        }

        EstadoTarjeta("Comedero", estado.comedero)
        if (!estado.notificacionesHabilitadas) {
            Text("Las notificaciones están deshabilitadas; el Nodo seguirá funcionando.")
        }
        OutlinedButton(onClick = onDesconectar, modifier = Modifier.fillMaxWidth()) {
            Text("Desconectar")
        }
    }
}

@Composable
internal fun EstadoTarjeta(titulo: String, valor: String) {
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(titulo, style = MaterialTheme.typography.titleSmall)
            Text(valor, style = MaterialTheme.typography.titleMedium)
        }
    }
}
