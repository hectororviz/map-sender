package com.mapsender.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mapsender.ui.MainViewModel

@Composable
fun MainScreen(vm: MainViewModel, onOpenSettings: () -> Unit) {
    val settings by vm.settings.collectAsState()
    val conn by vm.conn.collectAsState()
    Column(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Map Sender", style = MaterialTheme.typography.headlineMedium)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    if (settings.isConfigured) "Estado: configurado" else "Estado: sin configurar",
                    style = MaterialTheme.typography.titleMedium
                )
                Text("Servidor: ${settings.gotifyBaseUrl.ifBlank { "—" }}")
                Text("Dispositivo: ${settings.deviceName.ifBlank { "—" }}")
                Text("Último envío: ${settings.lastSentSummary.ifBlank { "ninguno" }}")
                conn.message?.let { Text(it) }
                if (conn.testing) Text("Probando conexión…")
            }
        }
        Button(onClick = { vm.testConnection() }, enabled = !conn.testing, modifier = Modifier.fillMaxWidth()) {
            Text("Probar conexión")
        }
        OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
            Text("Abrir ajustes")
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Comparte desde Google Maps: Compartir → Map Sender. Al tocar la notificación en el receptor se abre Waze.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}
