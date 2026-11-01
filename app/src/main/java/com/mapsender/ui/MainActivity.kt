package com.mapsender.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mapsender.ui.TestResolveViewModel
import com.mapsender.ui.screens.MainScreen
import com.mapsender.ui.screens.SettingsScreen
import com.mapsender.ui.screens.ShareScreen
import com.mapsender.ui.screens.TestResolveScreen
import com.mapsender.ui.theme.MapSenderTheme

/** Single-activity: principal + ajustes + destino compartido (SEND text/plain). */
class MainActivity : ComponentActivity() {

    private val mainVm: MainViewModel by viewModels()
    private val settingsVm: SettingsViewModel by viewModels()
    private val shareVm: ShareViewModel by viewModels()
    private val testVm: TestResolveViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MapSenderTheme {
                val nav = rememberNavController()
                // Si llega un SEND, ir directo a share.
                val startShare = intent?.action == Intent.ACTION_SEND && intent?.type == "text/plain"
                NavHost(nav, startDestination = if (startShare) "share" else "main") {
                    composable("main") { MainScreen(mainVm) { nav.navigate("settings") } }
                    composable("settings") {
                        SettingsScreen(settingsVm, onDone = { nav.popBackStack() }, onOpenTest = { nav.navigate("test") })
                    }
                    composable("test") { TestResolveScreen(testVm) { nav.popBackStack() } }
                    composable("share") {
                        ShareScreen(
                            shareVm,
                            sharedText = intent?.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString(),
                            onClose = { finish() },
                            onOpenSettings = { nav.navigate("settings") }
                        )
                    }
                }
                // Observa cambios de intent (singleTop): onNewIntent reinyecta el texto.
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            shareVm.handleSharedText(intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString())
        }
    }
}
