package com.example.iris

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.max
import kotlin.math.min

class YoloDetector(context: Context) {

    // --- SETTINGS ---
    private val modelName = "best_float32.tflite"
    private val labelName = "labels.txt"
    private val CONFIDENCE_THRESHOLD = 0.40f
    private val INPUT_SIZE = 640

    // Auto-Detected Model Properties
    private var OUTPUT_CHANNELS = 0
    private var OUTPUT_ANCHORS = 0
    private var NUM_CLASSES = 0

    private var interpreter: Interpreter? = null
    private var labels = emptyList<String>()

    // Buffers
    private var inputBuffer: ByteBuffer? = null
    private var outputBuffer: ByteBuffer? = null
    private val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)

    // Scaling variables
    private var scaleX = 1.0f
    private var scaleY = 1.0f
    private var offsetX = 0f
    private var offsetY = 0f

    // NEW: Distance & Risk Calculation (using shared classes)
    private val distanceCalculator = DistanceCalculator()
    private val riskScorer = RiskScorer()

    // Performance Tracking
    private var lastInferenceTime = 0L

    init {
        try {
            // Load model file
            val modelFile = loadModelFile(context, modelName)

            // Standard Interpreter Options
            val options = Interpreter.Options().apply {
                setNumThreads(4)
                try {
                    val compatList = CompatibilityList()
                    if (compatList.isDelegateSupportedOnThisDevice) {
                        val delegateOptions = compatList.bestOptionsForThisDevice
                        val gpuDelegate = GpuDelegate(delegateOptions)
                        addDelegate(gpuDelegate)
                        Log.d("IRIS_YOLO", "✅ GPU delegate enabled (Best Options)")
                    } else {
                        Log.w("IRIS_YOLO", "⚠️ GPU delegate not supported on this device, falling back to NNAPI")
                        setUseNNAPI(true)
                    }
                } catch (e: Exception) {
                    Log.w("IRIS_YOLO", "⚠️ GPU initialization failed: ${e.message}")
                    setUseNNAPI(true)
                }
                Log.d("IRIS_YOLO", "⚡ Initializing TensorFlow Lite Interpreter")
            }

            interpreter = Interpreter(modelFile, options)
            labels = loadLabels(context, labelName)

            // --- UNIVERSAL SHAPE DETECTION ---
            val outputTensor = interpreter!!.getOutputTensor(0)
            val shape = outputTensor.shape()

            Log.d("IRIS_YOLO", "Output tensor shape: ${shape.contentToString()}")

            if (shape[1] > shape[2]) {
                OUTPUT_ANCHORS = shape[1]
                OUTPUT_CHANNELS = shape[2]
            } else {
                OUTPUT_CHANNELS = shape[1]
                OUTPUT_ANCHORS = shape[2]
            }

            NUM_CLASSES = OUTPUT_CHANNELS - 4
            Log.d("IRIS_YOLO", "✅ Model Loaded. Shape: [1, $OUTPUT_CHANNELS, $OUTPUT_ANCHORS]. Classes: $NUM_CLASSES")

            // Allocate Buffers
            inputBuffer = ByteBuffer.allocateDirect(1 * INPUT_SIZE * INPUT_SIZE * 3 * 4)
            inputBuffer?.order(ByteOrder.nativeOrder())

            outputBuffer = ByteBuffer.allocateDirect(1 * OUTPUT_CHANNELS * OUTPUT_ANCHORS * 4)
            outputBuffer?.order(ByteOrder.nativeOrder())

        } catch (e: Exception) {
            Log.e("IRIS_YOLO", "❌ Init Error: Could not load $modelName", e)
            e.printStackTrace()
        }
    }

    private fun loadModelFile(context: Context, fileName: String): MappedByteBuffer {
        val assetFileDescriptor = context.assets.openFd(fileName)
        val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = assetFileDescriptor.startOffset
        val declaredLength = assetFileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    private fun loadLabels(context: Context, fileName: String): List<String> {
        return try {
            context.assets.open(fileName).bufferedReader().use { it.readLines() }
        } catch (e: Exception) {
            Log.e("IRIS_YOLO", "Error loading labels", e)
            emptyList()
        }
    }

    fun detect(bitmap: Bitmap): List<DetectionResult> {
        val startTime = System.currentTimeMillis()
        val tflite = interpreter ?: return emptyList()
        val inBuf = inputBuffer ?: return emptyList()
        val outBuf = outputBuffer ?: return emptyList()

        try {
            // 1. Resize & Normalize
            val letterboxed = createLetterboxedBitmap(bitmap)
            bitmapToByteBuffer(letterboxed, inBuf)

            // 2. Run Inference
            outBuf.rewind()
            tflite.run(inBuf, outBuf)

            // 3. Parse Output
            val detections = parseYoloOutput(outBuf, bitmap.width, bitmap.height)

            // Track inference time
            lastInferenceTime = System.currentTimeMillis() - startTime

            // Warn if exceeding 20ms budget (per rules)
            if (lastInferenceTime > 20) {
                Log.w("IRIS_YOLO", "⚠️ Inference slow: ${lastInferenceTime}ms (target: <20ms)")
            }

            return detections

        } catch (e: Exception) {
            Log.e("IRIS_YOLO", "Detection Error", e)
            e.printStackTrace()
            return emptyList()
        }
    }

    private fun parseYoloOutput(byteBuffer: ByteBuffer, originalWidth: Int, originalHeight: Int): List<DetectionResult> {
        byteBuffer.rewind()
        val floatBuffer = byteBuffer.asFloatBuffer()
        val detections = ArrayList<DetectionResult>()

        for (i in 0 until OUTPUT_ANCHORS) {
            var maxScore = 0f
            var maxClassIndex = -1

            for (c in 0 until NUM_CLASSES) {
                val score = floatBuffer.get((4 + c) * OUTPUT_ANCHORS + i)
                if (score > maxScore) {
                    maxScore = score
                    maxClassIndex = c
                }
            }

            if (maxScore > CONFIDENCE_THRESHOLD) {
                val x = floatBuffer.get(0 * OUTPUT_ANCHORS + i)
                val y = floatBuffer.get(1 * OUTPUT_ANCHORS + i)
                val w = floatBuffer.get(2 * OUTPUT_ANCHORS + i)
                val h = floatBuffer.get(3 * OUTPUT_ANCHORS + i)

                val cxPixel = if (x > 1) x else x * INPUT_SIZE
                val cyPixel = if (y > 1) y else y * INPUT_SIZE
                val wPixel  = if (w > 1) w else w * INPUT_SIZE
                val hPixel  = if (h > 1) h else h * INPUT_SIZE

                val origCx = (cxPixel - offsetX) / scaleX
                val origCy = (cyPixel - offsetY) / scaleY
                val origW = wPixel / scaleX
                val origH = hPixel / scaleY

                val finalCx = origCx / originalWidth
                val finalCy = origCy / originalHeight
                val finalW = origW / originalWidth
                val finalH = origH / originalHeight

                val label = labels.getOrElse(maxClassIndex) { "Unknown" }

                // FILTER: Ignore "Train" class (false positives)
                if (label.equals("Train", ignoreCase = true)) {
                    continue
                }

                // NEW: Calculate distance using bbox dimensions
                val distance = distanceCalculator.estimateDistance(
                    label,
                    finalW,
                    finalH,
                    originalWidth,
                    originalHeight
                )

                // NEW: Calculate risk score
                val risk = riskScorer.calculateRisk(
                    distance = distance,
                    objectClass = label,
                    velocity = 0.0f, // Will be updated by velocity estimator
                    position = finalCx
                )

                detections.add(
                    DetectionResult(
                        label = label,
                        confidence = maxScore,
                        centerX = finalCx.coerceIn(0f, 1f),
                        centerY = finalCy.coerceIn(0f, 1f),
                        width = finalW.coerceIn(0f, 1f),
                        height = finalH.coerceIn(0f, 1f),
                        distance = distance,
                        riskScore = risk.score,
                        riskLevel = risk.level,
                        announcementDelay = risk.announcementDelayMs
                    )
                )
            }
        }
        return applyNMS(detections)
    }

    private fun createLetterboxedBitmap(source: Bitmap): Bitmap {
        val srcWidth = source.width.toFloat()
        val srcHeight = source.height.toFloat()
        val scale = min(INPUT_SIZE / srcWidth, INPUT_SIZE / srcHeight)
        val scaledWidth = (srcWidth * scale).toInt()
        val scaledHeight = (srcHeight * scale).toInt()

        offsetX = (INPUT_SIZE - scaledWidth) / 2f
        offsetY = (INPUT_SIZE - scaledHeight) / 2f
        scaleX = scaledWidth / srcWidth
        scaleY = scaledHeight / srcHeight

        val letterboxed = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(letterboxed)
        canvas.drawColor(Color.BLACK)

        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        val scaledBitmap = Bitmap.createScaledBitmap(source, scaledWidth, scaledHeight, true)
        canvas.drawBitmap(scaledBitmap, offsetX, offsetY, paint)

        if (scaledBitmap != source) scaledBitmap.recycle()
        return letterboxed
    }

    private fun bitmapToByteBuffer(bitmap: Bitmap, buffer: ByteBuffer) {
        buffer.rewind()
        bitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = ((pixel shr 16) and 0xFF) / 255.0f
            val g = ((pixel shr 8) and 0xFF) / 255.0f
            val b = (pixel and 0xFF) / 255.0f

            buffer.putFloat(r)
            buffer.putFloat(g)
            buffer.putFloat(b)
        }
        buffer.rewind()
    }

    private fun applyNMS(list: List<DetectionResult>): List<DetectionResult> {
        val sorted = list.sortedByDescending { it.confidence }
        val selected = ArrayList<DetectionResult>()

        for (item in sorted) {
            var overlap = false
            for (sel in selected) {
                if (calculateIoU(item, sel) > 0.45f) {
                    overlap = true
                    break
                }
            }
            if (!overlap) selected.add(item)
        }
        return selected
    }

    private fun calculateIoU(a: DetectionResult, b: DetectionResult): Float {
        val x1 = max(a.centerX - a.width / 2, b.centerX - b.width / 2)
        val y1 = max(a.centerY - a.height / 2, b.centerY - b.height / 2)
        val x2 = min(a.centerX + a.width / 2, b.centerX + b.width / 2)
        val y2 = min(a.centerY + a.height / 2, b.centerY + b.height / 2)

        if (x1 >= x2 || y1 >= y2) return 0f
        val intersection = (x2 - x1) * (y2 - y1)
        val areaA = a.width * a.height
        val areaB = b.width * b.height
        return intersection / (areaA + areaB - intersection)
    }

    fun getLastInferenceTime(): Long = lastInferenceTime

    fun close() {
        interpreter?.close()
    }

    // ================================================================
    // VELOCITY INTEGRATION - Recalculate risk with velocity data
    // ================================================================

    /**
     * Recalculates risk scores using velocity data from tracking
     * @param originalResults Original detection results
     * @param trackedResults Velocity-tracked detections
     * @return Detection results with velocity-adjusted risk scores
     */
    fun recalculateRiskWithVelocity(
        originalResults: List<DetectionResult>,
        trackedResults: List<VelocityTracker.TrackedDetection>
    ): List<DetectionResult> {
        return originalResults.mapIndexed { index, detection ->
            // Find matching tracked result
            val tracked = trackedResults.getOrNull(index)

            if (tracked != null && tracked.speed > 0.05f) {
                // Recalculate risk with actual velocity
                val newRisk = riskScorer.calculateRisk(
                    distance = detection.distance,
                    objectClass = detection.label,
                    velocity = tracked.speed,
                    position = detection.centerX
                )

                // Apply additional risk boost for approaching objects
                val approachingBoost = if (tracked.isApproaching) 0.15f else 0f
                val pathBoost = if (tracked.isMovingIntoPath) 0.1f else 0f
                val totalBoost = approachingBoost + pathBoost

                val adjustedScore = (newRisk.score + totalBoost).coerceIn(0f, 1f)
                val adjustedLevel = when {
                    adjustedScore > 0.75f -> RiskLevel.CRITICAL
                    adjustedScore > 0.5f -> RiskLevel.WARNING
                    adjustedScore > 0.3f -> RiskLevel.CAUTION
                    else -> RiskLevel.INFO
                }
                val adjustedDelay = when (adjustedLevel) {
                    RiskLevel.CRITICAL -> 400
                    RiskLevel.WARNING -> 800
                    RiskLevel.CAUTION -> 1200
                    RiskLevel.INFO -> 1500
                }

                // Return updated detection with velocity data
                detection.copy(
                    riskScore = adjustedScore,
                    riskLevel = adjustedLevel,
                    announcementDelay = adjustedDelay,
                    velocity = tracked.speed,
                    isApproaching = tracked.isApproaching
                )
            } else {
                // No velocity data, return original
                detection
            }
        }
    }

    data class DetectionResult(
        val label: String,
        val confidence: Float,
        val centerX: Float,
        val centerY: Float,
        val width: Float,
        val height: Float,
        val distance: Float,
        val riskScore: Float,
        val riskLevel: RiskLevel,
        val announcementDelay: Int,
        val velocity: Float = 0f,           // NEW: Object velocity (normalized units/sec)
        val isApproaching: Boolean = false  // NEW: Is object moving towards camera?
    )
}