package com.dieselalert.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

object Ui {
    var rev by mutableIntStateOf(0)
}

private val AppColors = lightColorScheme(
    primary = Color(0xFF0B5CAD),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7E7FA),
    onPrimaryContainer = Color(0xFF062F5A),
    secondary = Color(0xFFF59E0B),
    onSecondary = Color(0xFF3A2500),
    secondaryContainer = Color(0xFFFFEBC2),
    onSecondaryContainer = Color(0xFF3A2500),
    tertiary = Color(0xFF1FA971),
    onTertiary = Color.White,
    background = Color(0xFFF1F5FA),
    onBackground = Color(0xFF14202E),
    surface = Color.White,
    onSurface = Color(0xFF14202E),
    surfaceVariant = Color(0xFFE3EBF5),
    onSurfaceVariant = Color(0xFF4A5A6C),
    outline = Color(0xFF8A99AB)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Notifs.ensureChannels(this)
        ActivityTracking.register(this)
        setContent { MaterialTheme(colorScheme = AppColors) { Root() } }
    }

    override fun onResume() {
        super.onResume()
        ActivityTracking.register(this)
        TripEnd.recover(this)
        Relay.flush(this)
        Maint.check(this)
        Ui.rev++
    }
}

@Composable
fun Root() {
    val ctx = LocalContext.current
    val store = remember { Store(ctx) }
    var rev by remember { mutableIntStateOf(0) }
    var tab by remember { mutableIntStateOf(0) }
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val tick = rev + Ui.rev

    val toast: (String) -> Unit = { msg ->
        scope.launch {
            snack.currentSnackbarData?.dismiss()
            snack.showSnackbar(msg)
        }
    }
    val change: () -> Unit = { rev++ }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            NavigationBar {
                val items = listOf("⛽" to "Inicio", "📋" to "Historial", "⚙️" to "Ajustes")
                items.forEachIndexed { i, it ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Text(it.first, fontSize = 20.sp) },
                        label = { Text(it.second) }
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> HomeScreen(store, tick, change, toast)
                1 -> HistoryScreen(store, tick, toast)
                else -> SettingsScreen(store, tick, change, toast)
            }
        }
    }
}
