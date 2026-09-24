package com.screenrot.app.render

import android.graphics.*
import com.screenrot.core.CharacterState
import kotlin.math.sin

/**
 * Draws the character procedurally on a [Canvas] — no bitmap assets for the MVP. Each visual
 * feature is a small function that reads ONE (or a couple) fields off [CharacterState] and
 * nothing else, so adding a new damage channel later means adding one function here, not
 * touching the others. This is the "layers" architecture from the brief, implemented as
 * draw-order rather than as separate bitmap layers, since procedural shapes compose more
 * cheaply than bitmap layering for a simple cartoon style and avoid needing an art pipeline
 * for the MVP. Swapping to bitmap layers later (for a nicer hand-drawn style) means replacing
 * the body of these functions with canvas.drawBitmap calls — callers and CharacterState don't
 * change.
 *
 * `animT` is a slow-moving phase value in [0, 2*PI) supplied by the caller (wallpaper engine
 * or Compose preview) driving idle animation: blinking and a gentle head bob. It never affects
 * damage math — purely cosmetic idle motion.
 */
object CharacterRenderer {

    private val skinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F5C99B") }
    private val hairPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#4A2E1E") }
    private val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#2B2B2B") }
    private val bagPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#8A6E63"); alpha = 140 }
    private val mouthPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#7A3B3B"); style = Paint.Style.STROKE; strokeWidth = 6f; strokeCap = Paint.Cap.ROUND
    }
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2B2B2B"); style = Paint.Style.STROKE; strokeWidth = 5f
    }
    private val backgroundPaint = Paint().apply { color = Color.parseColor("#FFF3E0") }

    fun draw(canvas: Canvas, width: Int, height: Int, state: CharacterState, animT: Float) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        val cx = width / 2f
        // Posture: higher posture damage -> character sits lower and head tilts, plus a slow
        // idle bob layered on top for "breathing".
        val bob = sin(animT.toDouble()).toFloat() * 4f
        val postureDrop = state.posture * (height * 0.05f)
        val cy = height * 0.42f + postureDrop + bob
        val tilt = state.posture * 10f // degrees

        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(tilt)

        val headRadius = width * 0.18f
        drawBody(canvas, headRadius, state)
        drawHead(canvas, headRadius, state)
        drawHair(canvas, headRadius, state)
        drawEyes(canvas, headRadius, state, animT)
        drawEars(canvas, headRadius, state)
        drawNose(canvas, headRadius, state)
        drawMouth(canvas, headRadius, state)

        canvas.restore()
    }

    private fun drawBody(canvas: Canvas, r: Float, state: CharacterState) {
        // Weight change widens (or, near 0, doesn't shrink below a floor) the body silhouette.
        val widthFactor = 1f + state.weightChange * 0.6f
        val bodyRect = RectF(-r * 1.1f * widthFactor, r * 0.9f, r * 1.1f * widthFactor, r * 2.6f)
        canvas.drawRoundRect(bodyRect, r * 0.6f, r * 0.6f, skinPaint.also { it.color = Color.parseColor("#6FA8DC") })
        canvas.drawRoundRect(bodyRect, r * 0.6f, r * 0.6f, outlinePaint)
    }

    private fun drawHead(canvas: Canvas, r: Float, state: CharacterState) {
        // Skin fatigue desaturates/darkens the skin tone slightly.
        val base = Color.parseColor("#F5C99B")
        val tired = Color.parseColor("#C9A489")
        skinPaint.color = lerpColor(base, tired, state.skinFatigue)
        canvas.drawCircle(0f, 0f, r, skinPaint)
        canvas.drawCircle(0f, 0f, r, outlinePaint)

        // Skin fatigue also adds a couple of small "stress" marks on the cheek.
        if (state.skinFatigue > 0.4f) {
            val markPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#B08968"); alpha = (state.skinFatigue * 120).toInt()
            }
            canvas.drawCircle(r * 0.5f, r * 0.35f, r * 0.05f, markPaint)
            canvas.drawCircle(r * 0.35f, r * 0.55f, r * 0.03f, markPaint)
        }
    }

    private fun drawHair(canvas: Canvas, r: Float, state: CharacterState) {
        // hairLoss 0 = full coverage arc; 1 = a few sad strands.
        val coverage = 1f - state.hairLoss
        val sweepDegrees = 180f * coverage
        val hairRect = RectF(-r * 1.02f, -r * 1.15f, r * 1.02f, r * 0.4f)
        canvas.drawArc(hairRect, 180f, sweepDegrees, true, hairPaint)
        if (state.hairLoss > 0.7f) {
            // A few lonely strands for comic effect once mostly bald.
            val strandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = hairPaint.color; style = Paint.Style.STROKE; strokeWidth = 4f
            }
            for (i in 0..2) {
                val x = -r * 0.2f + i * r * 0.2f
                canvas.drawLine(x, -r * 0.95f, x + r * 0.05f, -r * 1.15f, strandPaint)
            }
        }
    }

    private fun drawEyes(canvas: Canvas, r: Float, state: CharacterState, animT: Float) {
        val eyeY = -r * 0.05f
        val eyeSpacing = r * 0.4f
        // Blink: eyes close briefly every animation cycle regardless of damage.
        val blinkPhase = (sin(animT * 3.0).toFloat() + 1f) / 2f
        val isBlinking = blinkPhase > 0.96f
        val eyeOpenness = if (isBlinking) 0.15f else 1f

        // Eye fatigue shrinks the eyes (half-lidded look) and adds under-eye bags.
        val eyeRadius = r * 0.09f * (1f - state.eyeFatigue * 0.4f) * eyeOpenness

        for (side in listOf(-1, 1)) {
            val ex = side * eyeSpacing
            canvas.drawOval(
                RectF(ex - eyeRadius, eyeY - eyeRadius, ex + eyeRadius, eyeY + eyeRadius),
                eyePaint
            )
            if (state.eyeFatigue > 0.2f) {
                val bagRect = RectF(
                    ex - eyeRadius * 1.3f, eyeY + eyeRadius * 1.2f,
                    ex + eyeRadius * 1.3f, eyeY + eyeRadius * (1.2f + state.eyeFatigue * 1.6f)
                )
                canvas.drawOval(bagRect, bagPaint)
            }
        }
    }

    private fun drawEars(canvas: Canvas, r: Float, state: CharacterState) {
        val earRadius = r * 0.22f
        // Ear distortion: one ear grows and rotates for comic asymmetry; the other stays normal.
        val leftEar = RectF(-r * 1.05f - earRadius * 0.3f, -earRadius, -r * 1.05f + earRadius * 0.3f, earRadius)
        canvas.drawOval(leftEar, skinPaint)
        canvas.drawOval(leftEar, outlinePaint)

        canvas.save()
        canvas.translate(r * 1.05f, 0f)
        canvas.rotate(state.earDistortion * 35f)
        val scale = 1f + state.earDistortion * 0.8f
        canvas.scale(scale, scale)
        val rightEar = RectF(-earRadius * 0.3f, -earRadius, earRadius * 0.3f, earRadius)
        canvas.drawOval(rightEar, skinPaint)
        canvas.drawOval(rightEar, outlinePaint)
        canvas.restore()
    }

    private fun drawNose(canvas: Canvas, r: Float, state: CharacterState) {
        val skew = state.noseDistortion * r * 0.15f
        val path = Path().apply {
            moveTo(-r * 0.04f, r * 0.05f)
            lineTo(skew, r * 0.22f + state.noseDistortion * r * 0.1f)
            lineTo(r * 0.04f, r * 0.05f)
        }
        canvas.drawPath(path, outlinePaint)
    }

    private fun drawMouth(canvas: Canvas, r: Float, state: CharacterState) {
        // Mouth distortion bends the smile into a lopsided wobble instead of a clean arc.
        val y = r * 0.42f
        val wobble = state.mouthDistortion * r * 0.15f
        val path = Path().apply {
            moveTo(-r * 0.3f, y)
            quadTo(0f, y + r * 0.15f - wobble, r * 0.3f, y - wobble * 0.5f)
        }
        canvas.drawPath(path, mouthPaint)
    }

    private fun lerpColor(from: Int, to: Int, t: Float): Int {
        val tt = t.coerceIn(0f, 1f)
        val a = (Color.alpha(from) + (Color.alpha(to) - Color.alpha(from)) * tt).toInt()
        val r = (Color.red(from) + (Color.red(to) - Color.red(from)) * tt).toInt()
        val g = (Color.green(from) + (Color.green(to) - Color.green(from)) * tt).toInt()
        val b = (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * tt).toInt()
        return Color.argb(a, r, g, b)
    }
}
