package com.screenrot.app

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.screenrot.app.BuildConfig
import com.screenrot.app.screentime.UsagePermissionHelper
import com.screenrot.app.share.ShareImageGenerator
import com.screenrot.app.ui.*
import com.screenrot.app.wallpaper.ScreenRotWallpaperService
import com.screenrot.app.work.CharacterUpdateWorker

private enum class Screen { ONBOARDING, PERMISSION, CHARACTER, DEBUG }

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CharacterUpdateWorker.schedule(applicationContext)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavHost(viewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onScreenResumed()
    }
}

@Composable
private fun AppNavHost(viewModel: MainViewModel) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    var screen by remember {
        mutableStateOf(if (uiState.hasPermission) Screen.CHARACTER else Screen.ONBOARDING)
    }

    // If permission got granted while off-screen (user came back from Settings), jump forward.
    LaunchedEffect(uiState.hasPermission) {
        if (uiState.hasPermission && screen == Screen.PERMISSION) {
            screen = Screen.CHARACTER
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (screen) {
            Screen.ONBOARDING -> OnboardingScreen(onContinue = { screen = Screen.PERMISSION })

            Screen.PERMISSION -> PermissionScreen(onGrantClick = {
                UsagePermissionHelper.openUsageAccessSettings(context)
            })

            Screen.CHARACTER -> CharacterScreen(
                state = uiState.character,
                topAppsSummary = uiState.topAppsSummary,
                onSetWallpaper = {
                    val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                        putExtra(
                            WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                            ComponentName(context, ScreenRotWallpaperService::class.java)
                        )
                    }
                    context.startActivity(intent)
                },
                onShare = {
                    val file = ShareImageGenerator.generate(context, uiState.character, uiState.topAppsSummary)
                    context.startActivity(ShareImageGenerator.shareIntent(context, file))
                },
                onOpenDebug = if (BuildConfig.DEBUG) ({ screen = Screen.DEBUG }) else null
            )

            Screen.DEBUG -> DebugScreen(onBack = {
                viewModel.refresh()
                screen = Screen.CHARACTER
            })
        }
    }

    LaunchedEffect(Unit) { if (uiState.hasPermission) viewModel.refresh() }
}
