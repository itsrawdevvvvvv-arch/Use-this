package dev.rawdev.gaffer.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.rawdev.gaffer.agent.AgentState
import dev.rawdev.gaffer.agent.Role
import dev.rawdev.gaffer.agent.UiMessage
import dev.rawdev.gaffer.service.ScreenAgentService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(vm: ChatViewModel, onOpenSettings: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val screenControl = rememberScreenControl()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(state.messages.size, state.running) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                title = {
                    Column {
                        Text("Gaffer", fontWeight = FontWeight.SemiBold)
                        Text(
                            when {
                                state.running -> state.status.ifBlank { "Working" }
                                !screenControl -> "Screen control off"
                                else -> "Ready"
                            },
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { vm.reset() }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Clear conversation")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            if (!screenControl) {
                PermissionBanner(
                    title = "Screen control is off",
                    body = "Gaffer can chat without it, but it cannot see or tap anything until you switch it on under Settings > Accessibility > Gaffer.",
                    action = "Enable",
                    onAction = { openAccessibility(context) }
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                if (state.messages.isEmpty()) {
                    EmptyState()
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.messages, key = { it.id }) { msg ->
                            MessageRow(msg)
                        }
                    }
                }
            }

            state.pending?.let { pending ->
                ApprovalCard(
                    tool = pending.tool,
                    description = pending.description,
                    onAllow = { vm.approve(pending.id, true) },
                    onDeny = { vm.approve(pending.id, false) }
                )
            }

            if (state.running) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "step ${state.step}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { vm.stop() }) { Text("Stop") }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Tell Gaffer what to do") },
                    maxLines = 5,
                    shape = RoundedCornerShape(18.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (input.isNotBlank() && !state.running) {
                            vm.send(input)
                            input = ""
                        }
                    })
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    enabled = input.isNotBlank() && !state.running,
                    onClick = {
                        vm.send(input)
                        input = ""
                    }
                ) {
                    Icon(
                        Icons.Filled.Send,
                        contentDescription = "Send",
                        tint = if (input.isNotBlank() && !state.running) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("The agent is on set.", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        Spacer(Modifier.size(8.dp))
        Text(
            "Try: \"open WhatsApp and reply to the last message from Rahul\", or \"draft a follow-up email to the producer about the shoot date\".",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MessageRow(msg: UiMessage) {
    val scheme = MaterialTheme.colorScheme
    val (bg, fg, label) = when (msg.role) {
        Role.USER -> Triple(scheme.primary, scheme.onPrimary, "")
        Role.ASSISTANT -> Triple(scheme.surfaceVariant, scheme.onSurfaceVariant, "")
        Role.TOOL -> Triple(scheme.surface, scheme.onSurfaceVariant, msg.title.uppercase())
        Role.ERROR -> Triple(scheme.error.copy(alpha = 0.12f), scheme.error, msg.title.uppercase())
        Role.SYSTEM -> Triple(Color.Transparent, scheme.onSurfaceVariant, msg.title.uppercase())
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (msg.role == Role.USER) Alignment.End else Alignment.Start
    ) {
        if (label.isNotBlank()) {
            Text(
                label,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, bottom = 2.dp)
            )
        }
        Box(
            modifier = Modifier
                .background(bg, RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                msg.text,
                fontSize = if (msg.role == Role.TOOL) 11.sp else 14.sp,
                fontFamily = if (msg.role == Role.TOOL) FontFamily.Monospace else FontFamily.Default,
                color = fg
            )
        }
    }
}

@Composable
private fun ApprovalCard(
    tool: String,
    description: String,
    onAllow: () -> Unit,
    onDeny: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                "Gaffer wants to ${tool.replace('_', ' ')}",
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
            Spacer(Modifier.size(6.dp))
            Text(description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.size(10.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onDeny) {
                    Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("No")
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onAllow) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Allow")
                }
            }
        }
    }
}

@Composable
private fun PermissionBanner(title: String, body: String, action: String, onAction: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(Modifier.size(4.dp))
            Text(body, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.size(6.dp))
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
fun rememberScreenControl(): Boolean {
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var enabled by remember { mutableStateOf(ScreenAgentService.ready()) }
    androidx.compose.runtime.DisposableEffect(owner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                enabled = ScreenAgentService.ready()
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return enabled
}

fun openAccessibility(context: Context) {
    try {
        context.startActivity(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: Exception) {
    }
}

fun openNotificationAccess(context: Context) {
    try {
        context.startActivity(
            Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: Exception) {
    }
}
