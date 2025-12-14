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
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.common.ops.NormalizeOp
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.min

class PathSegmentor(context: Context) {

    private val modelName = "path_seg.tflite"
    private val INPUT_SIZE = 640
    private val CONFIDENCE_THRESHOLD = 0.5f

    private var OUTPUT_CHANNELS = 0
    private var OUTPUT_ANCHORS = 0
    private var NUM_CLASSES = 0

    private var interpreter: Interpreter? = null
    private var inputBuffer: ByteBuffer? = null
    private var outputBuffer: ByteBuffer? = null
    
    private val imageProcessor = ImageProcessor.Builder()
        .add(ResizeOp(INPUT_SIZE, INPUT_SIZE, ResizeOp.ResizeMethod.BILINEAR))
        .add(NormalizeOp(0f, 255f))
        .build()

    private var scaleX = 1.0f
    private var scaleY = 1.0f
    private var offsetX = 0f
    private var offsetY = 0f
    private var lastInferenceTime = 0L

    init {
        try {
            val modelFile = loadModelFile(context, modelName)
            val options = Interpreter.Options().apply {
                setNumThreads(4)
                try {
                    val compatList = CompatibilityList()
                    if (compatList.isDelegateSupportedOnThisDevice) {
                        val delegateOptions = compatList.bestOptionsForThisDevice
                        val gpuDelegate = GpuDelegate(delegateOptions)
                        addDelegate(gpuDelegate)
                        Log.d("IRIS_SEG", "✅ GPU delegate enabled for segmentation (Best Options)")
                    } else {
                        Log.w("IRIS_SEG", "⚠️ GPU delegate not supported, using NNAPI")
                        setUseNNAPI(true)
                    }
                } catch (e: Exception) {
                    Log.w("IRIS_SEG", "⚠️ GPU initialization failed: ${e.message}")
                    setUseNNAPI(true)  // Fallback to NNAPI
                }
                Log.d("IRIS_SEG", "⚡ Initializing Path Segmentation Model")
            }

            interpreter = Interpreter(modelFile, options)
            val outputTensor = interpreter!!.getOutputTensor(0)
            val shape = outputTensor.shape()

            Log.d("IRIS_SEG", "Output tensor shape: ${shape.contentToString()}")

            if (shape[1] > shape[2]) {
                OUTPUT_ANCHORS = shape[1]
                OUTPUT_CHANNELS = shape[2]
            } else {
                OUTPUT_CHANNELS = shape[1]
                OUTPUT_ANCHORS = shape[2]
            }

            NUM_CLASSES = OUTPUT_CHANNELS - 4
            Log.d("IRIS_SEG", "✅ Model Loaded. Shape: [1, $OUTPUT_CHANNELS, $OUTPUT_ANCHORS]. Classes: $NUM_CLASSES")

            inputBuffer = ByteBuffer.allocateDirect(1 * INPUT_SIZE * INPUT_SIZE * 3 * 4)
            inputBuffer?.order(ByteOrder.nativeOrder())
            outputBuffer = ByteBuffer.allocateDirect(1 * OUTPUT_CHANNELS * OUTPUT_ANCHORS * 4)
            outputBuffer?.order(ByteOrder.nativeOrder())

        } catch (e: Exception) {
            Log.e("IRIS_SEG", "❌ Init Error: Could not load $modelName", e)
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

    // MAIN FUNCTION - Integrates YOLO detections for danger zone analysis
    fun analyzePathWithYolo(bitmap: Bitmap, yoloDetections: List<YoloDetector.DetectionResult> = emptyList()): PathAnalysisResult {
        val startTime = System.currentTimeMillis()
        val defaultCommand = NavigationCommand("Unknown", 3, 1500, "STOP")
        val tflite = interpreter ?: return PathAnalysisResult("Unknown", emptyList(), 0f, 0f, 0f, defaultCommand)
        val outBuf = outputBuffer ?: return PathAnalysisResult("Unknown", emptyList(), 0f, 0f, 0f, defaultCommand)

        try {
            // Optimized Preprocessing using TensorImage
            var tensorImage = TensorImage(org.tensorflow.lite.DataType.FLOAT32)
            tensorImage.load(bitmap)
            tensorImage = imageProcessor.process(tensorImage)
            val inBuf = tensorImage.buffer

            outBuf.rewind()
            tflite.run(inBuf, outBuf)

            // CRITICAL FIX: Pass YOLO detections to path analysis
            val result = parseSegmentationOutput(outBuf, bitmap.width, bitmap.height, yoloDetections)

            lastInferenceTime = System.currentTimeMillis() - startTime

            if (lastInferenceTime > 50) {
                Log.w("IRIS_SEG", "⚠️ Segmentation slow: ${lastInferenceTime}ms (target: <50ms)")
            } else {
                Log.d("IRIS_SEG", "⚡ Segmentation time: ${lastInferenceTime}ms")
            }

            return result

        } catch (e: Exception) {
            Log.e("IRIS_SEG", "Path Analysis Error", e)
            e.printStackTrace()
            return PathAnalysisResult("Unknown", emptyList(), 0f, 0f, 0f, defaultCommand)
        }
    }

    private fun parseSegmentationOutput(
        byteBuffer: ByteBuffer,
        originalWidth: Int,
        originalHeight: Int,
        yoloDetections: List<YoloDetector.DetectionResult>
    ): PathAnalysisResult {
        byteBuffer.rewind()
        val floatBuffer = byteBuffer.asFloatBuffer()
        val detections = ArrayList<SegmentationDetection>()

        var leftPathScore = 0f
        var centerPathScore = 0f
        var rightPathScore = 0f
        var totalPathPixels = 0
        var topPathQuality = 0f
        var bottomPathQuality = 0f
        var pathConfidence = 0f

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
                val wPixel = if (w > 1) w else w * INPUT_SIZE
                val hPixel = if (h > 1) h else h * INPUT_SIZE

                val origCx = (cxPixel - offsetX) / scaleX
                val origCy = (cyPixel - offsetY) / scaleY
                val origW = wPixel / scaleX
                val origH = hPixel / scaleY

                val finalCx = origCx / originalWidth
                val finalCy = origCy / originalHeight
                val finalW = origW / originalWidth
                val finalH = origH / originalHeight

                detections.add(
                    SegmentationDetection(
                        classIndex = maxClassIndex,
                        confidence = maxScore,
                        centerX = finalCx.coerceIn(0f, 1f),
                        centerY = finalCy.coerceIn(0f, 1f),
                        width = finalW.coerceIn(0f, 1f),
                        height = finalH.coerceIn(0f, 1f)
                    )
                )

                if (finalCy > 0.5f) {
                    val weight = maxScore * (finalCy - 0.5f) * 2.0f
                    totalPathPixels++
                    pathConfidence += maxScore
                    bottomPathQuality += weight

                    when {
                        finalCx < 0.33f -> leftPathScore += weight
                        finalCx > 0.67f -> rightPathScore += weight
                        else -> centerPathScore += weight
                    }
                } else {
                    topPathQuality += maxScore * 0.5f
                }
            }
        }

        pathConfidence = if (totalPathPixels > 0) pathConfidence / totalPathPixels else 0f

        // CRITICAL FIX: Enhanced danger zone detection with YOLO integration
        val navigationCmd = determineNavigationCommand(
            leftScore = leftPathScore,
            centerScore = centerPathScore,
            rightScore = rightPathScore,
            totalPixels = totalPathPixels,
            pathConfidence = pathConfidence,
            topQuality = topPathQuality,
            bottomQuality = bottomPathQuality,
            yoloDetections = yoloDetections
        )

        Log.d("IRIS_SEG", "🎯 Path Analysis - L:%.2f C:%.2f R:%.2f Conf:%.2f → %s".format(
            leftPathScore, centerPathScore, rightPathScore, pathConfidence, navigationCmd.instruction))

        return PathAnalysisResult(
            direction = navigationCmd.instruction,
            detections = detections,
            leftScore = leftPathScore,
            centerScore = centerPathScore,
            rightScore = rightPathScore,
            navigationCommand = navigationCmd
        )
    }

    // CRITICAL FIX: Enhanced navigation logic with YOLO danger zone detection
    private fun determineNavigationCommand(
        leftScore: Float,
        centerScore: Float,
        rightScore: Float,
        totalPixels: Int,
        pathConfidence: Float,
        topQuality: Float,
        bottomQuality: Float,
        yoloDetections: List<YoloDetector.DetectionResult>
    ): NavigationCommand {

        // CRITICAL CHECK 1: Objects in danger zone (tunnel center, bottom 60%)
        val dangerZoneObjects = yoloDetections.filter { detection ->
            val inTunnel = detection.centerX in 0.30f..0.70f
            val inDangerZone = detection.centerY > 0.40f
            inTunnel && inDangerZone
        }

        // IMMEDIATE STOP if critical objects detected in danger zone
        if (dangerZoneObjects.isNotEmpty()) {
            val closestObject = dangerZoneObjects.minByOrNull { it.distance }
            val objectList = dangerZoneObjects.take(2).joinToString(", ") { it.label }

            Log.e("IRIS_SEG", "🚨 DANGER ZONE BREACH: $objectList at ${closestObject?.distance}m")

            return NavigationCommand(
                instruction = "STOP - ${objectList} in path",
                priority = 0, // CRITICAL
                delayMs = 0, // IMMEDIATE announcement
                action = "STOP"
            )
        }

        // CRITICAL CHECK 2: Very close objects (any position)
        val criticalProximity = yoloDetections.filter { it.distance < 1.5f && it.riskLevel == RiskLevel.CRITICAL }
        if (criticalProximity.isNotEmpty()) {
            val closest = criticalProximity.minByOrNull { it.distance }
            Log.w("IRIS_SEG", "⚠️ CRITICAL PROXIMITY: ${closest?.label} at ${closest?.distance}m")

            return NavigationCommand(
                instruction = "STOP - ${closest?.label} ahead",
                priority = 0,
                delayMs = 200,
                action = "STOP"
            )
        }

        // RULE 1: No clear path detected
        if (totalPixels < 5 || pathConfidence < 0.3f) {
            return NavigationCommand(
                instruction = "STOP - No Clear Path",
                priority = 0,
                delayMs = 400,
                action = "STOP"
            )
        }

        // WARNING CHECK: Objects approaching tunnel (near edges)
        val approachingObjects = yoloDetections.filter { detection ->
            val nearTunnel = detection.centerX in 0.25f..0.75f
            val closeEnough = detection.distance < 3.0f
            val inLowerHalf = detection.centerY > 0.3f
            nearTunnel && closeEnough && inLowerHalf
        }

        if (approachingObjects.isNotEmpty()) {
            val closest = approachingObjects.minByOrNull { it.distance }
            Log.w("IRIS_SEG", "⚠️ APPROACHING: ${closest?.label} at ${closest?.distance}m")

            return NavigationCommand(
                instruction = "Caution - ${closest?.label} ahead",
                priority = 1,
                delayMs = 600,
                action = "CAUTION"
            )
        }

        // RULE 2: Path quality analysis
        if (bottomQuality < 0.3f && topQuality > 0.5f) {
            return NavigationCommand(
                instruction = "Obstacles Ahead - Slow Down",
                priority = 1,
                delayMs = 800,
                action = "CAUTION"
            )
        }

        // RULE 3: Strong directional bias (normal navigation)
        val threshold = 0.15f

        return when {
            leftScore > rightScore + threshold && leftScore > centerScore -> {
                NavigationCommand(
                    instruction = "Curve Left",
                    priority = 2,
                    delayMs = 1200,
                    action = "TURN_LEFT"
                )
            }

            rightScore > leftScore + threshold && rightScore > centerScore -> {
                NavigationCommand(
                    instruction = "Curve Right",
                    priority = 2,
                    delayMs = 1200,
                    action = "TURN_RIGHT"
                )
            }

            centerScore > leftScore && centerScore > rightScore -> {
                val confidence = if (pathConfidence > 0.7f) "Clear" else ""
                NavigationCommand(
                    instruction = "$confidence Straight Ahead".trim(),
                    priority = 3,
                    delayMs = 1500,
                    action = "FORWARD"
                )
            }

            leftScore > 0.2f && rightScore > 0.2f -> {
                NavigationCommand(
                    instruction = "Multiple Paths Available",
                    priority = 3,
                    delayMs = 1500,
                    action = "FORWARD"
                )
            }

            else -> {
                NavigationCommand(
                    instruction = "Path Ahead",
                    priority = 3,
                    delayMs = 1500,
                    action = "FORWARD"
                )
            }
        }
    }

    fun getLastInferenceTime(): Long = lastInferenceTime

    fun close() {
        interpreter?.close()
    }

    data class SegmentationDetection(
        val classIndex: Int,
        val confidence: Float,
        val centerX: Float,
        val centerY: Float,
        val width: Float,
        val height: Float
    )

    data class NavigationCommand(
        val instruction: String,
        val priority: Int,
        val delayMs: Int,
        val action: String
    )

    data class PathAnalysisResult(
        val direction: String,
        val detections: List<SegmentationDetection>,
        val leftScore: Float,
        val centerScore: Float,
        val rightScore: Float,
        val navigationCommand: NavigationCommand
    )
}