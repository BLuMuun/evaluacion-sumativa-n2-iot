package cl.inacap.comederoiot.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cl.inacap.comederoiot.datos.Lectura
import cl.inacap.comederoiot.datos.RepositorioLecturas
import java.text.DateFormat

@Composable
fun PantallaHistorial(
    repositorio: RepositorioLecturas,
    onVolver: () -> Unit,
    modifier: Modifier = Modifier
) {
    var lecturas by remember { mutableStateOf<List<Lectura>>(emptyList()) }
    var cargando by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var recargar by remember { mutableIntStateOf(0) }

    LaunchedEffect(recargar) {
        cargando = true
        error = null
        repositorio.cargarUltimas { resultado ->
            resultado.onSuccess { lecturas = it }
                .onFailure { error = "No se pudo cargar el historial. Revisa la conexión y las reglas Firestore." }
            cargando = false
        }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Historial de lecturas", style = MaterialTheme.typography.headlineSmall)
        Text("Últimas ${RepositorioLecturas.LIMITE} lecturas guardadas en Cloud Firestore.")
        when {
            cargando -> CircularProgressIndicator()
            error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
            lecturas.isEmpty() -> Text("Todavía no hay lecturas guardadas.")
            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(lecturas, key = { it.id }) { lectura -> FilaLectura(lectura) }
            }
        }
        if (error != null || lecturas.isEmpty()) {
            Button(onClick = { recargar += 1 }, modifier = Modifier.fillMaxWidth()) { Text("Actualizar") }
        }
        OutlinedButton(onClick = onVolver, modifier = Modifier.fillMaxWidth()) { Text("Volver al Panel") }
    }
}

@Composable
private fun FilaLectura(lectura: Lectura) {
    val fecha = lectura.momento?.let { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(it) }
        ?: "Hora pendiente"
    Card {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text("$fecha · ${lectura.origen}", style = MaterialTheme.typography.labelMedium)
            Text("${lectura.clave}: ${lectura.valor}", style = MaterialTheme.typography.titleMedium)
        }
    }
}
