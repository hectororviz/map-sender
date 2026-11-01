package com.mapsender.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mapsender.ui.ShareUiState
import com.mapsender.ui.ShareViewModel

@Composable
fun ShareScreen(vm: ShareViewModel, sharedText: String?, onClose: () -> Unit, onOpenSettings: () -> Unit) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    fun openInBrowser(url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
    LaunchedEffect(sharedText) { vm.handleSharedText(sharedText) }

    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Enviar destino", style = MaterialTheme.typography.headlineMedium)
        when (val s = state) {
            is ShareUiState.Idle, is ShareUiState.Parsing ->
                Text(if (s is ShareUiState.Parsing) "Analizando enlace…" else "…")
            is ShareUiState.Ready -> {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(s.place.displayTitle, style = MaterialTheme.typography.titleMedium)
                        Text(s.place.displaySubtitle)
                        Text("Abrirá: ${s.place.navigationUrl}", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = { vm.send() }, modifier = Modifier.weight(1f)) { Text("Enviar") }
                    OutlinedButton(onClick = onClose, modifier = Modifier.weight(1f)) { Text("Cancelar") }
                }
            }
            is ShareUiState.Resolving ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Resolviendo enlace corto…")
                    Text(s.url, style = MaterialTheme.typography.bodySmall)
                    Text("Esto necesita Internet y puede tardar unos segundos con poca cobertura.")
                    OutlinedButton(onClick = onClose) { Text("Cancelar") }
                }
            is ShareUiState.NeedsResolution ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enlace corto sin coordenadas: ${s.url}")
                    Text("No se pudo resolver automáticamente. Abre el enlace con Internet y comparte el lugar con coordenadas.")
                    Button(onClick = { vm.retryParse() }) { Text("Reintentar") }
                    OutlinedButton(onClick = { openInBrowser(s.url) }) { Text("Abrir en navegador") }
                    OutlinedButton(onClick = { vm.openManualEntry() }) { Text("Introducir manualmente") }
                    OutlinedButton(onClick = onClose) { Text("Cancelar") }
                }
            is ShareUiState.Unrecognized ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enlace no reconocido: ${s.reason}")
                    s.raw?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    OutlinedButton(onClick = onClose) { Text("Cerrar") }
                    OutlinedButton(onClick = { vm.openManualEntry() }) { Text("Introducir manualmente") }
                }
            is ShareUiState.Sending -> Text("Enviando a Gotify…")
            is ShareUiState.Sent -> {
                Text("Enviado: ${s.summary}")
                Button(onClick = onClose) { Text("Cerrar") }
            }
            is ShareUiState.Error -> {
                Text(s.message)
                if (s.canRetry) {
                    Button(onClick = { vm.retryParse() }) { Text("Reintentar") }
                }
                s.linkUrl?.let { url ->
                    OutlinedButton(onClick = { openInBrowser(url) }) { Text("Abrir en navegador") }
                }
                OutlinedButton(onClick = { vm.openManualEntry() }) { Text("Introducir manualmente") }
                OutlinedButton(onClick = onOpenSettings) { Text("Abrir ajustes") }
                OutlinedButton(onClick = onClose) { Text("Cerrar") }
            }
            is ShareUiState.Geocoding ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Buscando coordenadas de «${s.name}»…")
                    Text("Último recurso: geocodificación del nombre.", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = onClose) { Text("Cancelar") }
                }
            is ShareUiState.ManualEntry -> ManualEntryForm(
                error = s.error,
                onAccept = { lat, lng, name -> vm.submitManual(lat, lng, name) },
                onCancel = onClose
            )
        }
    }
}

@Composable
private fun ManualEntryForm(error: String?, onAccept: (String, String, String) -> Unit, onCancel: () -> Unit) {
    var lat by remember { mutableStateOf("") }
    var lng by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Coordenadas manuales", style = MaterialTheme.typography.titleMedium)
        Text("Copia la latitud y longitud desde Google Maps (p. ej. 40.41536 y -3.70734).",
            style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(value = lat, onValueChange = { lat = it }, label = { Text("Latitud") },
            modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(value = lng, onValueChange = { lng = it }, label = { Text("Longitud") },
            modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nombre (opcional)") },
            modifier = Modifier.fillMaxWidth(), singleLine = true)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = { onAccept(lat, lng, name) }, modifier = Modifier.fillMaxWidth()) { Text("Continuar") }
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancelar") }
    }
}
