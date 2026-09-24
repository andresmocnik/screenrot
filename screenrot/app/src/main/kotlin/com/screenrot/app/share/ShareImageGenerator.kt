package com.screenrot.app.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.core.content.FileProvider
import com.screenrot.app.render.CharacterRenderer
import com.screenrot.core.CharacterState
import java.io.File
import java.io.FileOutputStream

/**
 * Renders the current character plus a stats overlay ("Today's Damage: 74%", screen time, top
 * apps) into a square-ish PNG and hands it to the system Sharesheet. Uses a FileProvider (not
 * a raw file:// Uri) since that's required for sharing to other apps on modern Android.
 */
object ShareImageGenerator {

    private const val WIDTH = 1080
    private const val HEIGHT = 1350 // 4:5, good for both IG feed and stories crop

    fun generate(
        context: Context,
        state: CharacterState,
        topAppsSummary: String
    ): File {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        CharacterRenderer.draw(canvas, WIDTH, HEIGHT, state, animT = 0f)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#2B2B2B"); textSize = 56f; isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#5A5A5A"); textSize = 38f; textAlign = Paint.Align.CENTER
        }

        val damagePct = (state.overallDamage * 100).toInt()
        val hours = state.totalMinutes / 60
        val mins = state.totalMinutes % 60

        canvas.drawText("Today's Damage: $damagePct%", WIDTH / 2f, HEIGHT - 220f, titlePaint)
        canvas.drawText("Screen time: ${hours}h ${mins}m", WIDTH / 2f, HEIGHT - 160f, subPaint)
        if (topAppsSummary.isNotBlank()) {
            canvas.drawText(topAppsSummary, WIDTH / 2f, HEIGHT - 110f, subPaint)
        }

        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, "screenrot_character.png")
        FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
        return file
    }

    fun shareIntent(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(intent, "Share your character")
    }
}
