package com.mapsender.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.mapsender.domain.NavAppPreference
import com.mapsender.ui.SettingsViewModel

@Composable
fun SettingsScreen(vm: SettingsViewModel, onDone: () -> Unit) {
    LaunchedEffect(Unit) { vm.loadOnce() }
    val errors by vm.errors.collectAsState()
    val status by vm.status.collectAsState()
    val testing by vm.testing.collectAsState()
    val baseUrl by vm.baseUrl.collectAsState()
    val token by vm.token.collectAsState()
    val device by vm.deviceName.collectAsState()
    val prio by vm.priority.collectAsState()
    val navApp by vm.navApp.collectAsState()
    val wazeNav by vm.wazeNavigate.collectAsState()
    val confirm by vm.confirmBeforeSend.collectAsState()
    val timeout by vm.timeout.collectAsState()
    val retry by vm.retry.collectAsState()

    Column(Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Ajustes", style = MaterialTheme.typography.headlineMedium)
        Field("URL de Gotify (https)", baseUrl, "baseUrl", errors) { vm.baseUrl.value = it }
        Field("Token de aplicación (se conserva si lo dejas vacío)", token, "token", errors, secret = true) { vm.token.value = it }
        Field("Nombre del dispositivo", device, "deviceName", errors) { vm.deviceName.value = it }
        Field("Prioridad (0-10)", prio, "priority", errors) { vm.priority.value = it }
        Field("Timeout (3-120 s)", timeout, "timeout", errors) { vm.timeout.value = it }

        Text("App de navegación preferida", style = MaterialTheme.typography.titleSmall)
        NavAppPreference.entries.forEach { opt ->
            Row(Modifier.fillMaxWidth().selectable(selected = navApp == opt, onClick = { vm.navApp.value = opt }), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = navApp == opt, onClick = { vm.navApp.value = opt })
                Text(opt.name)
            }
        }
        CheckRow("Añadir navigate=yes a Waze", wazeNav) { vm.wazeNavigate.value = it }
        CheckRow("Confirmar antes de enviar", confirm) { vm.confirmBeforeSend.value = it }
        CheckRow("Reintentar ante fallo", retry) { vm.retry.value = it }

        status?.let { Text(it) }
        Button(onClick = { vm.save(onDone) }, modifier = Modifier.fillMaxWidth()) { Text("Guardar") }
        OutlinedButton(onClick = { vm.testAndSave() }, enabled = !testing, modifier = Modifier.fillMaxWidth()) {
            Text(if (testing) "Probando…" else "Probar conexión")
        }
        OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Volver") }
    }
}

@Composable
private fun Field(label: String, value: String, key: String, errors: Map<String, String>, secret: Boolean = false, onChange: (String) -> Unit) {
    Column {
        OutlinedTextField(
            value = value, onValueChange = onChange, label = { Text(label) },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            isError = errors.containsKey(key)
        )
        errors[key]?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label)
    }
}
