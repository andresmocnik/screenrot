package com.screenrot.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.screenrot.app.debug.DebugUsageSimulator
import com.screenrot.core.AppUsage
import kotlinx.coroutines.launch

/** Debug-only screen: fake screen time per app so the character can be exercised without
 * waiting hours. Only reachable from [CharacterScreen]'s debug entry point, itself only shown
 * when the host Activity passes a non-null onOpenDebug (wired to BuildConfig.DEBUG). */
@Composable
fun DebugScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val simulator = remember { DebugUsageSimulator(context) }
    val scope = rememberCoroutineScope()
    val usage by simulator.fakeUsage.collectAsState(initial = emptyList())

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("Debug: simulate screen time", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))

        DebugUsageSimulator.PRESET_APPS.forEach { (pkg, name) ->
            val minutes = usage.find { it.packageName == pkg }?.minutes ?: 0
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text("$name: ${minutes}m", modifier = Modifier.weight(1f))
                TextButton(onClick = { scope.launch { simulator.addMinutes(pkg, name, 30) } }) { Text("+30m") }
                TextButton(onClick = { scope.launch { simulator.addMinutes(pkg, name, 60) } }) { Text("+1h") }
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(onClick = { scope.launch { simulator.resetDay() } }) { Text("Reset day") }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onBack) { Text("Back") }
    }
}
