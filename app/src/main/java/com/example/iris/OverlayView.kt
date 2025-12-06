package com.example.iris

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import android.view.View

class OverlayView(context: Context) : View(context) {

    // ================================================================
    // DATA HOLDERS
    // ================================================================
    private var yoloResults: List<YoloDetector.DetectionResult> = emptyList()
    private var currentPathDirection: String = ""

    private var imageWidth: Int = 1
    private var imageHeight: Int = 1

    // ================================================================
    // VOICE MODE STATE
    // ================================================================
    private var isVoiceMode: Boolean = false
    private var isListening: Boolean = false
    private var voiceStatusText: String = ""
    private var conversationText: String = ""

    // ================================================================
    // PAINT STYLES - RISK-BASED BOUNDING BOXES
    // ================================================================
    private val boxPaintCritical = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 14f
        isAntiAlias = true
        setShadowLayer(10f, 0f, 0f, Color.RED)  // Red glow for critical
    }

    private val boxPaintWarning = Paint().apply {
        color = Color.rgb(255, 165, 0) // Orange
        style = Paint.Style.STROKE
        strokeWidth = 10f
        isAntiAlias = true
    }

    private val boxPaintCaution = Paint().apply {
        color = Color.YELLOW
        style = Paint.Style.STROKE
        strokeWidth = 8f
        isAntiAlias = true
    }

    private val boxPaintInfo = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 6f
        isAntiAlias = true
    }

    // ================================================================
    // PAINT STYLES - SAFETY TUNNEL & DANGER ZONES
    // ================================================================
    private val tunnelPaint = Paint().apply {
        color = Color.CYAN
        style = Paint.Style.STROKE
        strokeWidth = 4f
        pathEffect = DashPathEffect(floatArrayOf(30f, 20f), 0f)
        alpha = 120
        isAntiAlias = true
    }

    private val dangerZonePaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 6f
        pathEffect = DashPathEffect(floatArrayOf(15f, 10f), 0f)
        alpha = 150
        isAntiAlias = true
    }

    // ================================================================
    // PAINT STYLES - TEXT & LABELS
    // ================================================================
    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 40f
        style = Paint.Style.FILL
        isFakeBoldText = true
        isAntiAlias = true
        setShadowLayer(5f, 0f, 0f, Color.BLACK)
    }

    private val textBackgroundPaint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.FILL
        alpha = 200
        isAntiAlias = true
    }

    private val smallTextPaint = Paint().apply {
        color = Color.LTGRAY
        textSize = 30f
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    // ================================================================
    // PAINT STYLES - PATH DIRECTION HUD (Top Banner)
    // ================================================================
    private val pathHudPaint = Paint().apply {
        textSize = 90f
        style = Paint.Style.FILL
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        setShadowLayer(15f, 0f, 0f, Color.BLACK)
    }

    private val pathHudBackgroundPaint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.FILL
        alpha = 180
        isAntiAlias = true
    }

    // ================================================================
    // PAINT STYLES - VOICE MODE UI
    // ================================================================
    private val voiceModePaint = Paint().apply {
        color = Color.CYAN
        textSize = 50f
        style = Paint.Style.FILL
        isFakeBoldText = true
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        setShadowLayer(10f, 0f, 0f, Color.BLACK)
    }

    private val listeningIndicatorPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val conversationPaint = Paint().apply {
        color = Color.WHITE
        textSize = 32f
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    // ================================================================
    // UPDATE FUNCTIONS - Called from MainActivity
    // ================================================================

    fun setResults(
        detectionResults: List<YoloDetector.DetectionResult>,
        imgWidth: Int = 640,
        imgHeight: Int = 640
    ) {
        this.yoloResults = detectionResults
        this.imageWidth = imgWidth
        this.imageHeight = imgHeight
        invalidate()
    }

    fun updatePathDirection(direction: String) {
        this.currentPathDirection = direction
        invalidate()
    }

    fun updateAll(
        yolo: List<YoloDetector.DetectionResult>,
        direction: String,
        imgWidth: Int = 640,
        imgHeight: Int = 640
    ) {
        this.yoloResults = yolo
        this.currentPathDirection = direction
        this.imageWidth = imgWidth
        this.imageHeight = imgHeight
        invalidate()
    }

    // ================================================================
    // VOICE MODE CONTROL METHODS
    // ================================================================

    fun setListening(listening: Boolean) {
        this.isListening = listening
        invalidate()
    }

    fun enterVoiceMode() {
        this.isVoiceMode = true
        invalidate()
    }

    fun exitVoiceMode() {
        this.isVoiceMode = false
        this.isListening = false
        this.voiceStatusText = ""
        this.conversationText = ""
        invalidate()
    }

    fun showVoiceStatus(status: String) {
        this.voiceStatusText = status
        invalidate()
    }

    fun updateConversation(conversation: String) {
        this.conversationText = conversation
        invalidate()
    }

    // ================================================================
    // MAIN DRAWING PIPELINE (Runs at ~60 FPS)
    // ================================================================
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val screenWidth = width.toFloat()
        val screenHeight = height.toFloat()

        if (isVoiceMode) {
            // VOICE MODE OVERLAY - Full screen conversation UI
            drawVoiceModeOverlay(canvas, screenWidth, screenHeight)
        } else {
            // NAVIGATION MODE - Layered AR display

            // Layer 1: Safety tunnel guides
            drawSafetyTunnel(canvas, screenWidth, screenHeight)

            // Layer 2: YOLO detections with risk-based styling
            drawYoloDetections(canvas, screenWidth, screenHeight)

            // Layer 3: Path direction HUD (top banner)
            drawPathDirectionHUD(canvas, screenWidth)

            // Layer 4: Debug statistics (bottom info)
            drawStatistics(canvas, screenWidth, screenHeight)
        }
    }

    // ================================================================
    // VOICE MODE OVERLAY - Full Screen Conversation UI
    // ================================================================
    private fun drawVoiceModeOverlay(canvas: Canvas, screenWidth: Float, screenHeight: Float) {
        // Semi-transparent dark background
        val overlayPaint = Paint().apply {
            color = Color.BLACK
            alpha = 180
        }
        canvas.drawRect(0f, 0f, screenWidth, screenHeight, overlayPaint)

        // Voice mode title
        voiceModePaint.color = Color.CYAN
        canvas.drawText("🎤 VOICE MODE", screenWidth / 2, 100f, voiceModePaint)

        // Listening indicator (pulsing red circle)
        if (isListening) {
            val centerX = screenWidth / 2
            val centerY = screenHeight / 2

            listeningIndicatorPaint.color = Color.RED
            canvas.drawCircle(centerX, centerY - 200f, 40f, listeningIndicatorPaint)

            voiceModePaint.color = Color.WHITE
            voiceModePaint.textSize = 40f
            canvas.drawText("Listening...", centerX, centerY - 100f, voiceModePaint)
            voiceModePaint.textSize = 50f
        }

        // Voice status message
        if (voiceStatusText.isNotEmpty()) {
            conversationPaint.textAlign = Paint.Align.CENTER
            conversationPaint.color = Color.YELLOW
            canvas.drawText(voiceStatusText, screenWidth / 2, screenHeight / 2 + 100f, conversationPaint)
        }

        // Conversation history (last 6 lines)
        if (conversationText.isNotEmpty()) {
            val lines = conversationText.split("\n")
            var yPos = screenHeight - 400f

            conversationPaint.textAlign = Paint.Align.LEFT
            conversationPaint.color = Color.WHITE
            conversationPaint.textSize = 28f

            for (line in lines.takeLast(6)) {
                if (line.isNotEmpty()) {
                    canvas.drawText(line, 40f, yPos, conversationPaint)
                    yPos += 50f
                }
            }
            conversationPaint.textSize = 32f
        }

        // Exit instructions
        smallTextPaint.textAlign = Paint.Align.CENTER
        smallTextPaint.color = Color.LTGRAY
        canvas.drawText("Press Volume Down to exit", screenWidth / 2, screenHeight - 50f, smallTextPaint)
        smallTextPaint.textAlign = Paint.Align.LEFT
    }

    // ================================================================
    // LAYER 1: SAFETY TUNNEL & DANGER ZONES
    // Visual guides for safe walking area
    // ================================================================
    private fun drawSafetyTunnel(canvas: Canvas, screenWidth: Float, screenHeight: Float) {
        // Define center tunnel (30% - 70% of screen width)
        val tunnelLeft = screenWidth * 0.30f
        val tunnelRight = screenWidth * 0.70f

        // Draw vertical tunnel boundaries
        canvas.drawLine(tunnelLeft, 0f, tunnelLeft, screenHeight, tunnelPaint)
        canvas.drawLine(tunnelRight, 0f, tunnelRight, screenHeight, tunnelPaint)

        // Draw danger zone boundary (bottom 60% of screen)
        val dangerZoneTop = screenHeight * 0.40f
        canvas.drawLine(tunnelLeft, dangerZoneTop, tunnelRight, dangerZoneTop, dangerZonePaint)

        // Labels
        smallTextPaint.color = Color.CYAN
        smallTextPaint.alpha = 150
        canvas.drawText("SAFE PATH", tunnelLeft + 20f, screenHeight - 100f, smallTextPaint)

        smallTextPaint.color = Color.RED
        canvas.drawText("DANGER ZONE", tunnelLeft + 20f, dangerZoneTop + 30f, smallTextPaint)
        smallTextPaint.alpha = 255
    }

    // ================================================================
    // LAYER 2: YOLO DETECTIONS
    // Risk-based color coding and labeling with distance info
    // ================================================================
    private fun drawYoloDetections(canvas: Canvas, screenWidth: Float, screenHeight: Float) {
        for (result in yoloResults) {
            // Convert normalized coordinates to screen pixels
            val left = (result.centerX - result.width / 2) * screenWidth
            val top = (result.centerY - result.height / 2) * screenHeight
            val right = (result.centerX + result.width / 2) * screenWidth
            val bottom = (result.centerY + result.height / 2) * screenHeight

            // Analyze position
            val isInTunnel = result.centerX in 0.30f..0.70f
            val isInDangerZone = result.centerY > 0.40f

            // Select paint based on risk level (from enhanced YOLO)
            val boxPaint = when (result.riskLevel) {
                RiskLevel.CRITICAL -> boxPaintCritical
                RiskLevel.WARNING -> boxPaintWarning
                RiskLevel.CAUTION -> boxPaintCaution
                else -> boxPaintInfo
            }

            // Enhance critical threats in danger zone (extra thick, extra glow)
            if (result.riskLevel == RiskLevel.CRITICAL && isInDangerZone) {
                boxPaint.strokeWidth = 18f
                boxPaint.setShadowLayer(15f, 0f, 0f, Color.RED)
            } else {
                // Reset to normal thickness
                boxPaint.strokeWidth = when (result.riskLevel) {
                    RiskLevel.CRITICAL -> 14f
                    RiskLevel.WARNING -> 10f
                    RiskLevel.CAUTION -> 8f
                    else -> 6f
                }
            }

            // Draw bounding box
            val rect = RectF(left, top, right, bottom)
            canvas.drawRect(rect, boxPaint)

            // Draw crosshair at center
            drawCrosshair(canvas, result.centerX * screenWidth, result.centerY * screenHeight, boxPaint)

            // Top label: Risk icon + class name + confidence
            val riskIcon = when (result.riskLevel) {
                RiskLevel.CRITICAL -> "⚠ "
                RiskLevel.WARNING -> "▲ "
                RiskLevel.CAUTION -> "• "
                else -> ""
            }
            val topLabel = "$riskIcon${result.label} ${(result.confidence * 100).toInt()}%"
            drawTextLabel(canvas, topLabel, left, top, boxPaint.color, isTop = true)

            // Bottom label: Distance + Position
            val distanceText = "${String.format("%.1f", result.distance)}m"
            val positionText = when {
                isInTunnel && isInDangerZone -> "🚨 COLLISION PATH"
                isInTunnel -> "⚠ IN PATH"
                result.centerX < 0.30 -> "← LEFT"
                else -> "RIGHT →"
            }
            val bottomLabel = "$distanceText | $positionText"
            drawTextLabel(canvas, bottomLabel, left, bottom, boxPaint.color, isTop = false)
        }
    }

    // ================================================================
    // HELPER: DRAW CROSSHAIR
    // ================================================================
    private fun drawCrosshair(canvas: Canvas, x: Float, y: Float, paint: Paint) {
        val size = 25f
        val crossPaint = Paint(paint).apply {
            strokeWidth = 5f
            style = Paint.Style.STROKE
        }
        canvas.drawLine(x - size, y, x + size, y, crossPaint)
        canvas.drawLine(x, y - size, x, y + size, crossPaint)
    }

    // ================================================================
    // HELPER: DRAW TEXT LABELS WITH BACKGROUNDS
    // ================================================================
    private fun drawTextLabel(canvas: Canvas, text: String, x: Float, y: Float, color: Int, isTop: Boolean) {
        val textBounds = Rect()
        textPaint.getTextBounds(text, 0, text.length, textBounds)

        val padding = 15f
        val bgHeight = textBounds.height() + padding * 2
        val bgWidth = textBounds.width() + padding * 2

        // Background rectangle
        val bgRect = if (isTop) {
            RectF(x, y - bgHeight, x + bgWidth, y)
        } else {
            RectF(x, y, x + bgWidth, y + bgHeight)
        }

        textBackgroundPaint.color = Color.BLACK
        textBackgroundPaint.alpha = 220
        canvas.drawRect(bgRect, textBackgroundPaint)

        // Color stripe on left edge
        val stripePaint = Paint().apply {
            this.color = color
            style = Paint.Style.FILL
        }
        val stripeRect = if (isTop) {
            RectF(x, y - bgHeight, x + 10f, y)
        } else {
            RectF(x, y, x + 10f, y + bgHeight)
        }
        canvas.drawRect(stripeRect, stripePaint)

        // Text
        textPaint.color = Color.WHITE
        val textY = if (isTop) y - padding - textBounds.bottom else y + padding - textBounds.top
        canvas.drawText(text, x + padding + 15f, textY, textPaint)
    }

    // ================================================================
    // LAYER 3: PATH DIRECTION HUD (Top Banner)
    // ================================================================
    private fun drawPathDirectionHUD(canvas: Canvas, screenWidth: Float) {
        if (currentPathDirection.isEmpty()) return

        // Color based on urgency
        val hudColor = when {
            currentPathDirection.contains("STOP", ignoreCase = true) ||
                    currentPathDirection.contains("Blocked", ignoreCase = true) -> Color.RED
            currentPathDirection.contains("Caution", ignoreCase = true) -> Color.rgb(255, 165, 0) // Orange
            currentPathDirection.contains("Curve", ignoreCase = true) -> Color.YELLOW
            else -> Color.GREEN
        }

        // Top bar background
        val barHeight = 200f
        val bgRect = RectF(0f, 0f, screenWidth, barHeight)
        pathHudBackgroundPaint.alpha = 200
        canvas.drawRect(bgRect, pathHudBackgroundPaint)

        // Direction text
        pathHudPaint.color = hudColor
        canvas.drawText(currentPathDirection, screenWidth / 2, 130f, pathHudPaint)

        // Directional arrows
        when {
            currentPathDirection.contains("Left", ignoreCase = true) ->
                drawArrow(canvas, screenWidth / 2 - 250f, 100f, -1)
            currentPathDirection.contains("Right", ignoreCase = true) ->
                drawArrow(canvas, screenWidth / 2 + 250f, 100f, 1)
        }
    }

    // ================================================================
    // HELPER: DRAW DIRECTIONAL ARROWS
    // ================================================================
    private fun drawArrow(canvas: Canvas, x: Float, y: Float, direction: Int) {
        val arrowPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val path = Path()

        if (direction == -1) { // Left arrow
            path.moveTo(x + 40f, y - 40f)
            path.lineTo(x - 20f, y)
            path.lineTo(x + 40f, y + 40f)
            path.lineTo(x + 20f, y + 40f)
            path.lineTo(x - 40f, y)
            path.lineTo(x + 20f, y - 40f)
        } else { // Right arrow
            path.moveTo(x - 40f, y - 40f)
            path.lineTo(x + 20f, y)
            path.lineTo(x - 40f, y + 40f)
            path.lineTo(x - 20f, y + 40f)
            path.lineTo(x + 40f, y)
            path.lineTo(x - 20f, y - 40f)
        }
        path.close()
        canvas.drawPath(path, arrowPaint)
    }

    // ================================================================
    // LAYER 4: DEBUG STATISTICS (Bottom Info Bar)
    // ================================================================
    private fun drawStatistics(canvas: Canvas, screenWidth: Float, screenHeight: Float) {
        val criticalCount = yoloResults.count { it.riskLevel == RiskLevel.CRITICAL }
        val warningCount = yoloResults.count { it.riskLevel == RiskLevel.WARNING }

        val stats = "Objects: ${yoloResults.size} | Critical: $criticalCount | Warnings: $warningCount"

        // Background box
        val statsRect = RectF(20f, screenHeight - 60f, 600f, screenHeight - 10f)
        textBackgroundPaint.alpha = 140
        canvas.drawRect(statsRect, textBackgroundPaint)

        // Text color based on risk level
        smallTextPaint.color = if (criticalCount > 0) Color.RED else Color.LTGRAY
        canvas.drawText(stats, 40f, screenHeight - 25f, smallTextPaint)
    }
}