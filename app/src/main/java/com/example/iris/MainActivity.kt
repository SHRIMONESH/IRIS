package com.example.iris

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    private val TAG = "IRIS_MAIN"

    // ================================================================
    // UI COMPONENTS
    // ================================================================
    private lateinit var viewFinder: PreviewView
    private lateinit var overlayView: OverlayView
    private lateinit var assistantTextView: TextView
    private lateinit var vibrator: Vibrator
    private lateinit var tts: TextToSpeech

    // ================================================================
    // AI ENGINES
    // ================================================================
    private lateinit var yoloDetector: YoloDetector
    private lateinit var pathSegmentor: PathSegmentor
    private lateinit var groqBrain: GroqBrain
    private lateinit var voiceManager: VoiceManager
    private lateinit var conversationManager: ConversationManager

    // ================================================================
    // STATE MANAGEMENT
    // ================================================================
    @Volatile
    private var isNavigationMode = true

    @Volatile
    private var isFrozen = false
    private var frozenBitmap: Bitmap? = null
    private val bitmapLock = Any()

    private var lastAnalysisTime = 0L
    private val ANALYSIS_INTERVAL = 200L  // 5 FPS

    // ================================================================
    // CRITICAL FIX: DYNAMIC ANNOUNCEMENT TRACKING
    // ================================================================
    private var lastAnnouncedInstruction = ""
    private var lastAnnouncementTime = 0L
    private var consecutiveStopCount = 0

    // Track last announcement per risk level
    private var lastCriticalAnnouncement = 0L
    private var lastWarningAnnouncement = 0L
    private var lastCautionAnnouncement = 0L

    // CRITICAL: Track objects in danger zone
    private var lastDangerZoneWarning = 0L
    private val DANGER_ZONE_REPEAT_INTERVAL = 500L  // Repeat every 500ms for critical

    // ================================================================
    // CAMERA
    // ================================================================
    private lateinit var cameraExecutor: ExecutorService
    private var cameraProvider: ProcessCameraProvider? = null

    // ================================================================
    // PERMISSION LAUNCHER
    // ================================================================
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
            val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false

            if (cameraGranted && audioGranted) {
                startCamera()
            } else {
                assistantTextView.text = "Camera and Microphone permissions required"
                speak("Permissions denied", true)
            }
        }

    // ================================================================
    // LIFECYCLE: onCreate
    // ================================================================
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // SETUP UI
        val container = FrameLayout(this)
        viewFinder = PreviewView(this)
        container.addView(viewFinder)
        overlayView = OverlayView(this)
        container.addView(overlayView)

        assistantTextView = TextView(this).apply {
            text = "System Initializing..."
            textSize = 24f
            setTextColor(Color.YELLOW)
            setBackgroundColor(Color.parseColor("#99000000"))
            setPadding(40, 40, 40, 40)
            gravity = Gravity.CENTER
        }
        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
        params.gravity = Gravity.BOTTOM
        container.addView(assistantTextView, params)
        setContentView(container)

        // SETUP HARDWARE
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        initTTS()
        cameraExecutor = Executors.newSingleThreadExecutor()

        try {
            // LOAD AI MODULES
            yoloDetector = YoloDetector(this)
            pathSegmentor = PathSegmentor(this)
            groqBrain = GroqBrain()
            conversationManager = ConversationManager(groqBrain)

            voiceManager = VoiceManager(
                context = this,
                onListeningStateChanged = { isListening ->
                    runOnUiThread {
                        overlayView.setListening(isListening)
                        if (isListening) {
                            assistantTextView.text = "🎤 Listening..."
                        } else {
                            assistantTextView.text = "Processing..."
                        }
                    }
                },
                onSpeechResult = { spokenText ->
                    handleUserSpeech(spokenText)
                },
                onError = { error ->
                    Log.e(TAG, "Voice error: $error")
                    runOnUiThread {
                        assistantTextView.text = "Error: $error"
                    }
                }
            )

            Log.d(TAG, "✅ All AI modules initialized")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error initializing AI modules", e)
            assistantTextView.text = "Error: AI models failed to load"
            speak("System initialization failed", true)
            return
        }

        // REQUEST PERMISSIONS & START CAMERA
        val hasCamera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        val hasAudio = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED

        if (hasCamera && hasAudio) {
            startCamera()
            assistantTextView.text = "Navigation Active - Vol Down for Voice"
            speak("IRIS navigation system ready. Press volume down for voice mode.", true)
        } else {
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO
                )
            )
        }
    }

    // ================================================================
    // LIFECYCLE METHODS
    // ================================================================
    override fun onPause() {
        super.onPause()
        cameraProvider?.unbindAll()
        if (!isNavigationMode) {
            exitVoiceMode()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdownNow()
        yoloDetector.close()
        pathSegmentor.close()
        voiceManager.cleanup()

        synchronized(bitmapLock) {
            frozenBitmap?.let {
                if (!it.isRecycled) it.recycle()
            }
            frozenBitmap = null
        }

        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        Log.d(TAG, "🔒 App destroyed, resources released")
    }

    // ================================================================
    // VOLUME DOWN KEY: TOGGLE VOICE MODE
    // ================================================================
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (isNavigationMode) {
                enterVoiceMode()
            } else {
                exitVoiceMode()
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    // ================================================================
    // VOICE MODE CONTROL
    // ================================================================
    private fun enterVoiceMode() {
        Log.d(TAG, "🎤 Entering Voice Mode")
        isNavigationMode = false
        isFrozen = true
        vibrate(100)

        val capturedBitmap = captureBitmapSafely()
        if (capturedBitmap == null) {
            assistantTextView.text = "Failed to capture image"
            speak("Unable to capture image", true)
            isNavigationMode = true
            isFrozen = false
            return
        }

        synchronized(bitmapLock) {
            frozenBitmap?.let { if (!it.isRecycled) it.recycle() }
            frozenBitmap = capturedBitmap
        }

        overlayView.enterVoiceMode()
        assistantTextView.text = "🎤 Voice Mode - Analyzing scene..."
        conversationManager.setContextImage(capturedBitmap)
        speak("Voice mode activated. Analyzing what you're looking at.", false)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val initialPrompt = "Describe what you see in this image briefly in 2-3 sentences."
                val description = conversationManager.getResponse(initialPrompt)

                runOnUiThread {
                    if (!description.startsWith("Error:")) {
                        assistantTextView.text = description
                        overlayView.updateConversation(conversationManager.getConversationText())
                        voiceManager.speak(description, autoListenAfter = true)
                    } else {
                        assistantTextView.text = description
                        speak("I couldn't analyze the scene. Please try again.", true)

                        CoroutineScope(Dispatchers.Main).launch {
                            delay(1500)
                            voiceManager.startListening()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting initial description", e)
                runOnUiThread {
                    assistantTextView.text = "Connection error"
                    speak("Connection error. Please try again.", true)
                }
            }
        }
    }

    private fun exitVoiceMode() {
        Log.d(TAG, "🚶 Exiting Voice Mode")
        isNavigationMode = true
        isFrozen = false
        vibrate(50)

        voiceManager.stopListening()
        voiceManager.stopSpeaking()
        conversationManager.clearConversation()

        overlayView.exitVoiceMode()
        assistantTextView.text = "Navigation Active - Vol Down for Voice"

        synchronized(bitmapLock) {
            frozenBitmap?.let { if (!it.isRecycled) it.recycle() }
            frozenBitmap = null
        }

        speak("Returning to navigation mode", true)

        // Reset announcement tracking
        lastAnnouncedInstruction = ""
        lastAnnouncementTime = 0L
        consecutiveStopCount = 0
    }

    private fun handleUserSpeech(spokenText: String) {
        Log.d(TAG, "User said: $spokenText")
        assistantTextView.text = "You: $spokenText"
        overlayView.showVoiceStatus("You: $spokenText")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = conversationManager.getResponse(spokenText)

                runOnUiThread {
                    if (!response.startsWith("Error:")) {
                        assistantTextView.text = response
                        overlayView.updateConversation(conversationManager.getConversationText())
                        voiceManager.speak(response, autoListenAfter = true)
                    } else {
                        assistantTextView.text = response
                        overlayView.showVoiceStatus(response)

                        CoroutineScope(Dispatchers.Main).launch {
                            delay(1000)
                            voiceManager.startListening()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing speech", e)
                runOnUiThread {
                    assistantTextView.text = "Error: ${e.message}"
                    speak("Sorry, I encountered an error.", true)

                    CoroutineScope(Dispatchers.Main).launch {
                        delay(1000)
                        voiceManager.startListening()
                    }
                }
            }
        }
    }

    // ================================================================
    // CAMERA & NAVIGATION MODE
    // ================================================================
    private fun captureBitmapSafely(): Bitmap? {
        return try {
            val originalBitmap = viewFinder.bitmap ?: return null
            if (originalBitmap.isRecycled) return null
            originalBitmap.copy(Bitmap.Config.ARGB_8888, false)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to capture bitmap", e)
            null
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also { it.setSurfaceProvider(viewFinder.surfaceProvider) }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(cameraExecutor) { imageProxy: ImageProxy ->

                        if (!isNavigationMode || isFrozen || cameraExecutor.isShutdown) {
                            imageProxy.close()
                            return@setAnalyzer
                        }

                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastAnalysisTime < ANALYSIS_INTERVAL) {
                            imageProxy.close()
                            return@setAnalyzer
                        }
                        lastAnalysisTime = currentTime

                        var bitmap: Bitmap? = null
                        var rotatedBitmap: Bitmap? = null

                        try {
                            bitmap = imageProxy.toBitmap()
                            val rotation = imageProxy.imageInfo.rotationDegrees.toFloat()

                            rotatedBitmap = if (rotation != 0f) {
                                val matrix = Matrix().apply { postRotate(rotation) }
                                Bitmap.createBitmap(
                                    bitmap,
                                    0,
                                    0,
                                    bitmap.width,
                                    bitmap.height,
                                    matrix,
                                    true
                                )
                            } else null

                            val bitmapToAnalyze = rotatedBitmap ?: bitmap

                            // ========================================
                            // CRITICAL FIX: YOLO FIRST, THEN PATH WITH YOLO DATA
                            // ========================================

                            // 1. YOLO Detection
                            val yoloResults = yoloDetector.detect(bitmapToAnalyze)

                            // 2. CRITICAL: Pass YOLO detections to path segmentor
                            val pathAnalysis = pathSegmentor.analyzePathWithYolo(
                                bitmapToAnalyze,
                                yoloResults  // CRITICAL: Provide YOLO data for danger zone analysis
                            )

                            val imgWidth = bitmapToAnalyze.width
                            val imgHeight = bitmapToAnalyze.height

                            runOnUiThread {
                                if (isNavigationMode && !isFrozen) {
                                    // Update overlay
                                    overlayView.updateAll(
                                        yolo = yoloResults,
                                        direction = pathAnalysis.navigationCommand.instruction,
                                        imgWidth = imgWidth,
                                        imgHeight = imgHeight
                                    )

                                    // CRITICAL: Process announcements with dynamic updates
                                    processDynamicAnnouncements(
                                        yoloResults,
                                        pathAnalysis.navigationCommand
                                    )
                                }
                            }

                        } catch (e: Exception) {
                            Log.e(TAG, "Frame error", e)
                        } finally {
                            rotatedBitmap?.recycle()
                            bitmap?.recycle()
                            imageProxy.close()
                        }
                    }
                }

            try {
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalyzer
                )
            } catch (e: Exception) {
                Log.e(TAG, "Camera bind failed", e)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    // ================================================================
    // CRITICAL FIX: DYNAMIC ANNOUNCEMENT SYSTEM
    // ================================================================
    private fun processDynamicAnnouncements(
        yoloResults: List<YoloDetector.DetectionResult>,
        navigationCommand: PathSegmentor.NavigationCommand
    ) {
        if (!isNavigationMode) return

        val currentTime = System.currentTimeMillis()
        val timeSinceLastAnnouncement = currentTime - lastAnnouncementTime

        val instruction = navigationCommand.instruction
        val priority = navigationCommand.priority
        val action = navigationCommand.action

        // ================================================================
        // CRITICAL CHECK: Objects in danger zone (immediate, repeated warnings)
        // ================================================================
        val dangerZoneObjects = yoloResults.filter { detection ->
            val inTunnel = detection.centerX in 0.30f..0.70f
            val inDangerZone = detection.centerY > 0.40f
            inTunnel && inDangerZone
        }

        if (dangerZoneObjects.isNotEmpty()) {
            val timeSinceDangerWarning = currentTime - lastDangerZoneWarning

            // CRITICAL: Repeat danger zone warnings frequently (every 500ms)
            if (timeSinceDangerWarning >= DANGER_ZONE_REPEAT_INTERVAL) {
                val closest = dangerZoneObjects.minByOrNull { it.distance }
                val dangerMessage = "STOP! ${closest?.label} in collision path at ${String.format("%.1f", closest?.distance)} meters"

                speak(dangerMessage, urgent = true)
                vibrate(200) // Strong vibration
                assistantTextView.text = dangerMessage

                lastDangerZoneWarning = currentTime
                lastAnnouncementTime = currentTime
                lastAnnouncedInstruction = dangerMessage
                consecutiveStopCount++

                Log.e(TAG, "🚨 DANGER ZONE: $dangerMessage")
                return  // Skip other announcements
            }
        } else {
            // Reset danger zone tracking when clear
            lastDangerZoneWarning = 0L
            consecutiveStopCount = 0
        }

        // ================================================================
        // PRIORITY-BASED ANNOUNCEMENT LOGIC
        // ================================================================

        // CRITICAL/WARNING: Always re-announce if changed OR time interval passed
        val shouldAnnounce = when {
            // Priority 0 (CRITICAL): Repeat every 400ms if instruction changes
            priority == 0 -> {
                instruction != lastAnnouncedInstruction ||
                        timeSinceLastAnnouncement >= 400
            }

            // Priority 1 (WARNING): Repeat every 800ms if instruction changes
            priority == 1 -> {
                instruction != lastAnnouncedInstruction ||
                        timeSinceLastAnnouncement >= 800
            }

            // Priority 2 (CAUTION): Announce on change or after 1200ms
            priority == 2 -> {
                instruction != lastAnnouncedInstruction ||
                        timeSinceLastAnnouncement >= 1200
            }

            // Priority 3 (INFO): Only announce on significant change
            else -> {
                instruction != lastAnnouncedInstruction &&
                        timeSinceLastAnnouncement >= 1500
            }
        }

        if (shouldAnnounce) {
            // Generate announcement based on action
            val announcement = when (action) {
                "STOP" -> {
                    vibrate(100)
                    instruction
                }
                "CAUTION" -> {
                    vibrate(50)
                    instruction
                }
                "TURN_LEFT", "TURN_RIGHT" -> {
                    instruction
                }
                else -> instruction
            }

            speak(announcement, urgent = priority <= 1)
            assistantTextView.text = announcement

            lastAnnouncedInstruction = instruction
            lastAnnouncementTime = currentTime

            Log.d(TAG, "📢 Announced (P$priority): $announcement")
        }

        // ================================================================
        // ADDITIONAL OBJECT ANNOUNCEMENTS (for nearby objects not in danger zone)
        // ================================================================
        val nearbyObjects = yoloResults.filter { detection ->
            detection.distance < 3.0f &&
                    detection.riskLevel in listOf(RiskLevel.CRITICAL, RiskLevel.WARNING)
        }.sortedBy { it.distance }

        // Announce closest nearby object (if not in danger zone)
        if (nearbyObjects.isNotEmpty() && dangerZoneObjects.isEmpty()) {
            val closest = nearbyObjects.first()
            val timeSinceRiskAnnouncement = when (closest.riskLevel) {
                RiskLevel.CRITICAL -> currentTime - lastCriticalAnnouncement
                RiskLevel.WARNING -> currentTime - lastWarningAnnouncement
                else -> currentTime - lastCautionAnnouncement
            }

            val minInterval = when (closest.riskLevel) {
                RiskLevel.CRITICAL -> 600L
                RiskLevel.WARNING -> 1000L
                else -> 1500L
            }

            if (timeSinceRiskAnnouncement >= minInterval) {
                val spatialAnnouncement = generateSpatialAnnouncement(closest)

                // Queue this announcement (don't interrupt navigation commands)
                CoroutineScope(Dispatchers.Main).launch {
                    delay(400)
                    if (isNavigationMode) {
                        speak(spatialAnnouncement, urgent = false)
                    }
                }

                // Update timing
                when (closest.riskLevel) {
                    RiskLevel.CRITICAL -> lastCriticalAnnouncement = currentTime
                    RiskLevel.WARNING -> lastWarningAnnouncement = currentTime
                    else -> lastCautionAnnouncement = currentTime
                }

                Log.d(TAG, "📍 Spatial: $spatialAnnouncement")
            }
        }
    }

    // ================================================================
    // SPATIAL ANNOUNCEMENT GENERATION
    // ================================================================
    private fun generateSpatialAnnouncement(detection: YoloDetector.DetectionResult): String {
        val position = when {
            detection.centerX < 0.33f -> "on your left"
            detection.centerX > 0.67f -> "on your right"
            else -> "ahead"
        }

        val distanceText = when {
            detection.distance < 1.0f -> "${String.format("%.1f", detection.distance)} meter"
            else -> "${String.format("%.1f", detection.distance)} meters"
        }

        val objectName = detection.label.lowercase()

        return when (detection.riskLevel) {
            RiskLevel.CRITICAL -> "Warning. $objectName $distanceText $position"
            RiskLevel.WARNING -> "Caution. $objectName $distanceText $position"
            else -> "$objectName $distanceText $position"
        }
    }

    // ================================================================
    // UTILITY FUNCTIONS
    // ================================================================
    private fun initTTS() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts.language = Locale.US
                tts.setSpeechRate(0.9f)
                Log.d(TAG, "TTS initialized")
            }
        }
    }

    private fun speak(text: String, urgent: Boolean) {
        if (!::tts.isInitialized) return
        val mode = if (urgent) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        tts.speak(text, mode, null, null)
    }

    private fun vibrate(ms: Long) {
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            vibrator.vibrate(
                VibrationEffect.createOneShot(
                    ms,
                    VibrationEffect.DEFAULT_AMPLITUDE
                )
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(ms)
        }
    }

    private fun ImageProxy.toBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(planes[0].buffer)
        return bitmap
    }
}