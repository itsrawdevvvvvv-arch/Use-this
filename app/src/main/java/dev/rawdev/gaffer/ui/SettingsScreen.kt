package dev.rawdev.gaffer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: ChatViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = vm.prefs

    var apiKey by remember { mutableStateOf(prefs.apiKey) }
    var baseUrl by remember { mutableStateOf(prefs.baseUrl) }
    var model by remember { mutableStateOf(prefs.model) }
    var confirm by remember { mutableStateOf(prefs.requireConfirmation) }
    var steps by remember { mutableStateOf(prefs.maxSteps.toString()) }
    var memory by remember { mutableStateOf(vm.memory()) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                navigationIcon = {
                    IconButton(onClick = {
                        prefs.apiKey = apiKey
                        prefs.baseUrl = baseUrl
                        prefs.model = model
                        prefs.requireConfirmation = confirm
                        prefs.maxSteps = steps.toIntOrNull() ?: 24
                        onBack()
                    }) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                },
                title = { Text("Settings", fontWeight = FontWeight.SemiBold) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionTitle("Model")
            Text(
                "Any OpenAI-compatible endpoint works. Paste the base URL that ends in /v1, the model name, and a key. The key is stored only in this app's private storage.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("Base URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next, keyboardType = KeyboardType.Uri)
            )
            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                label = { Text("Model") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("API key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
            )

            Spacer(Modifier.height(4.dp))
            SectionTitle("Behaviour")
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Ask before sending", fontSize = 14.sp)
                    Text(
                        "Pause for your approval before email, WhatsApp, shares and calendar events.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = confirm, onCheckedChange = { confirm = it })
            }
            OutlinedTextField(
                value = steps,
                onValueChange = { steps = it.filter { c -> c.isDigit() }.take(2) },
                label = { Text("Max steps per task") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done)
            )

            Spacer(Modifier.height(4.dp))
            SectionTitle("Permissions")
            OutlinedButton(
                onClick = { openAccessibility(context) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Accessibility (screen control)") }
            OutlinedButton(
                onClick = { openNotificationAccess(context) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Notification access") }

            Spacer(Modifier.height(4.dp))
            SectionTitle("Memory")
            if (memory.isEmpty()) {
                Text("Nothing saved yet.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    memory.takeLast(20).reversed().forEach { note ->
                        Text(
                            note,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            OutlinedButton(
                onClick = {
                    vm.clearMemory()
                    memory = emptyList()
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Clear memory") }

            Button(
                onClick = {
                    prefs.apiKey = apiKey
                    prefs.baseUrl = baseUrl
                    prefs.model = model
                    prefs.requireConfirmation = confirm
                    prefs.maxSteps = steps.toIntOrNull() ?: 24
                    onBack()
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save") }

            Spacer(Modifier.size(24.dp))
            Text(
                "Gaffer 1.0 · sideload build. It never touches banking or UPI apps, and it will always hand you the keyboard for passwords and OTPs.",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary
    )
}
