package com.mapsender.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mapsender.ui.TestResolveViewModel

/**
 * Prueba de resolución (dry-run): pega un enlace y ve cada etapa
 * sin enviar nada a Gotify.
 */
@Composable
fun TestResolveScreen(vm: TestResolveViewModel, onClose: () -> Unit) {
    val ui by vm.ui.collectAsState()
    val context = LocalContext.current
    var input by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Probar resolución", style = MaterialTheme.typography.headlineMedium)
        Text("Ejecuta la cadena completa sin enviar. Útil para diagnosticar enlaces cortos.",
            style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            value = input, onValueChange = { input = it },
            label = { Text("Enlace o texto compartido") },
            modifier = Modifier.fillMaxWidth(), minLines = 2
        )
        Button(
            onClick = { vm.runTest(input, context) },
            enabled = !ui.running,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (ui.running) "Probando…" else "Probar")
        }
        ui.steps.forEach { s ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        (if (s.ok) "✓ " else "✗ ") + s.name + " (${s.ms} ms)",
                        style = MaterialTheme.typography.titleSmall
                    )
                    if (s.detail.isNotBlank()) Text(s.detail, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        ui.result?.let {
            Card(Modifier.fillMaxWidth()) {
                Text(it, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
            }
        }
        OutlinedButton(onClick = { vm.clear(); onClose() }, modifier = Modifier.fillMaxWidth()) {
            Text("Volver")
        }
    }
}
