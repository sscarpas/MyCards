package com.example.mycards

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.toColorInt

/**
 * Semi-transparent overlay drawn on top of [CameraFragment]'s PreviewView.
 *
 * Renders:
 *  - a dark (#99000000) vignette covering the entire surface with an even-odd cutout
 *    shaped to ISO/IEC 7810 ID-1 card proportions (1.586 : 1, landscape);
 *  - a white rounded-rectangle border (cornerRadius = 8 dp) around the cutout;
 *  - a hint label beneath the frame.
 *
 * Frame width:
 *  - 85 % of view width in portrait orientation
 *  - 60 % of view width in landscape orientation
 *
 * The overlay is purely decorative; it does NOT crop the image itself.
 * Cropping is performed in [CardImagePickerHelper.cropCenterToCardRatio].
 */
class CardFrameOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val CARD_RATIO = 1.586f
        private const val FRAME_FRACTION_PORTRAIT  = 0.85f
        private const val FRAME_FRACTION_LANDSCAPE = 0.60f
    }

    private val dp = resources.displayMetrics.density

    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#99000000".toColorInt()
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color  = Color.WHITE
        style  = Paint.Style.STROKE
        strokeWidth = 2f * dp
    }

    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color     = Color.WHITE
        textSize  = 14f * dp
        textAlign = Paint.Align.CENTER
        setShadowLayer(4f * dp, 0f, 1f * dp, "#AA000000".toColorInt())
    }

    private val cornerRadius = 8f * dp
    private val overlayPath  = Path()
    private val frameRect    = RectF()

    private val hintText: String by lazy {
        context.getString(R.string.camera_frame_hint)
    }

    // ── Layout ────────────────────────────────────────────────────────────────

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        val fraction   = if (w > h) FRAME_FRACTION_LANDSCAPE else FRAME_FRACTION_PORTRAIT
        val frameWidth  = w * fraction
        val frameHeight = frameWidth / CARD_RATIO

        val left = (w - frameWidth)  / 2f
        val top  = (h - frameHeight) / 2f
        frameRect.set(left, top, left + frameWidth, top + frameHeight)
    }

    // ── Drawing ───────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        // 1. Vignette with transparent cutout (even-odd winding)
        overlayPath.reset()
        overlayPath.fillType = Path.FillType.EVEN_ODD
        overlayPath.addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
        overlayPath.addRoundRect(frameRect, cornerRadius, cornerRadius, Path.Direction.CW)
        canvas.drawPath(overlayPath, overlayPaint)

        // 2. Rounded border
        canvas.drawRoundRect(frameRect, cornerRadius, cornerRadius, borderPaint)

        // 3. Hint text below the frame
        canvas.drawText(hintText, width / 2f, frameRect.bottom + 24f * dp, hintPaint)
    }
}



