package com.screenrot.app.wallpaper

import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import com.screenrot.app.CharacterUpdatePipeline
import com.screenrot.app.render.CharacterRenderer
import com.screenrot.core.CharacterState
import kotlinx.coroutines.*

/**
 * Live wallpaper Engine. Two independent update loops, deliberately kept separate:
 *
 *  1. IDLE ANIMATION (frequent, cheap): redraws at ~a few fps ONLY while visible, just to
 *     advance the blink/bob phase and repaint the current [CharacterState] — no I/O, no
 *     UsageStats query. This is what makes the wallpaper feel alive without costing battery.
 *
 *  2. DAMAGE REFRESH (infrequent, does I/O): re-reads UsageStats and recomputes CharacterState
 *     at most every REFRESH_INTERVAL_MS while visible, plus once immediately on
 *     onVisibilityChanged(true) (covers "user just unlocked their phone" and "user just set
 *     this wallpaper"). This is intentionally NOT tied to the animation frame loop — the
 *     brief is explicit that Screen Time does not need to be polled anywhere near real-time.
 *
 * Rendering stops entirely (no Handler callbacks posted) while !visible, which is the standard
 * WallpaperService battery pattern — a wallpaper Engine keeps running even when not shown
 * unless it explicitly pauses itself in onVisibilityChanged.
 */
class ScreenRotWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = CharacterEngine()

    private inner class CharacterEngine : Engine() {

        private val handler = Handler(Looper.getMainLooper())
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private var visible = false
        private var animT = 0f
        private var currentState = CharacterState.PRISTINE
        private lateinit var pipeline: CharacterUpdatePipeline

        private val animRunnable = object : Runnable {
            override fun run() {
                if (!visible) return
                animT += ANIM_STEP
                drawFrame()
                handler.postDelayed(this, ANIM_FRAME_INTERVAL_MS)
            }
        }

        private val refreshRunnable = object : Runnable {
            override fun run() {
                if (!visible) return
                refreshDamage()
                handler.postDelayed(this, REFRESH_INTERVAL_MS)
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            pipeline = CharacterUpdatePipeline(applicationContext)
        }

        override fun onVisibilityChanged(isVisible: Boolean) {
            visible = isVisible
            if (isVisible) {
                refreshDamage() // immediate: covers "just unlocked" / "just applied wallpaper"
                handler.post(animRunnable)
                handler.postDelayed(refreshRunnable, REFRESH_INTERVAL_MS)
            } else {
                handler.removeCallbacks(animRunnable)
                handler.removeCallbacks(refreshRunnable)
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            visible = false
            handler.removeCallbacks(animRunnable)
            handler.removeCallbacks(refreshRunnable)
            scope.cancel()
        }

        private fun refreshDamage() {
            scope.launch {
                when (val result = pipeline.refresh()) {
                    is CharacterUpdatePipeline.Result.Updated -> currentState = result.state
                    // Permission missing: keep showing the last known/pristine character rather
                    // than crashing or blanking the wallpaper; the app's own UI is responsible
                    // for prompting the user to grant Usage Access.
                    CharacterUpdatePipeline.Result.PermissionMissing -> Unit
                }
                drawFrame()
            }
        }

        private fun drawFrame() {
            val holder = surfaceHolder ?: return
            if (!holder.surface.isValid) return
            var canvas: android.graphics.Canvas? = null
            try {
                canvas = holder.lockCanvas()
                canvas?.let {
                    CharacterRenderer.draw(it, it.width, it.height, currentState, animT)
                }
            } finally {
                canvas?.let { holder.unlockCanvasAndPost(it) }
            }
        }

        companion object {
            private const val ANIM_FRAME_INTERVAL_MS = 200L // ~5fps idle animation, plenty for a blink/bob
            private const val ANIM_STEP = 0.15f
            private const val REFRESH_INTERVAL_MS = 15 * 60 * 1000L // matches the WorkManager cadence
        }
    }
}
