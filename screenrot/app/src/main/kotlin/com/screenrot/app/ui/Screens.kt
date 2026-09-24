package com.screenrot.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.screenrot.app.render.CharacterRenderer
import com.screenrot.core.CharacterState

/**
 * Copy is deliberately playful, never guilt-tripping — per the product brief's explicit
 * "no medical/depressive/moralizing tone" requirement.
 */

@Composable
fun OnboardingScreen(onContinue: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Meet your new Screen Time victim.", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Text(
            "Your character changes based on how you use your phone today. The more you scroll, the more... unfortunate things happen to it.",
            fontSize = 16.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))
        Button(onClick = onContinue) { Text("Let's do this") }
    }
}

@Composable
fun PermissionScreen(onGrantClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("We need permission to see how long you use your apps.", fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Text(
            "We only use app names and how long you used them today — nothing else. It never leaves your phone, we don't see your messages, photos, or contacts, and there's no account or login.",
            fontSize = 15.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))
        Button(onClick = onGrantClick) { Text("Grant usage access") }
    }
}

@Composable
fun CharacterScreen(
    state: CharacterState,
    topAppsSummary: String,
    onSetWallpaper: () -> Unit,
    onShare: () -> Unit,
    onOpenDebug: (() -> Unit)?
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Your character", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))

        Canvas(modifier = Modifier.fillMaxWidth().height(360.dp)) {
            CharacterRenderer.draw(
                drawContext.canvas.nativeCanvas,
                size.width.toInt(),
                size.height.toInt(),
                state,
                animT = 0f
            )
        }

        Spacer(Modifier.height(16.dp))
        val hours = state.totalMinutes / 60
        val mins = state.totalMinutes % 60
        Text("Screen time today: ${hours}h ${mins}m", fontSize = 16.sp)
        if (topAppsSummary.isNotBlank()) {
            Text(topAppsSummary, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("Damage: ${(state.overallDamage * 100).toInt()}%", fontSize = 18.sp, fontWeight = FontWeight.Bold)

        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onSetWallpaper) { Text("Set as wallpaper") }
            OutlinedButton(onClick = onShare) { Text("Share") }
        }

        if (onOpenDebug != null) {
            Spacer(Modifier.height(24.dp))
            TextButton(onClick = onOpenDebug) { Text("Debug: simulate screen time") }
        }
    }
}
