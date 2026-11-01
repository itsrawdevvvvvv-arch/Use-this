package dev.rawdev.gaffer

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.rawdev.gaffer.agent.AgentEngine
import dev.rawdev.gaffer.ui.ChatScreen
import dev.rawdev.gaffer.ui.ChatViewModel
import dev.rawdev.gaffer.ui.GafferTheme
import dev.rawdev.gaffer.ui.SettingsScreen

class MainActivity : ComponentActivity() {

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AgentEngine.init(applicationContext)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            GafferTheme { Root() }
        }
    }
}

@Composable
private fun Root() {
    val vm: ChatViewModel = viewModel()
    var showSettings by remember { mutableStateOf(false) }
    if (showSettings) {
        SettingsScreen(vm = vm, onBack = { showSettings = false })
    } else {
        ChatScreen(vm = vm, onOpenSettings = { showSettings = true })
    }
}
