package com.example.iris

import android.util.Log
import kotlin.math.sqrt

// ================================================================
// VELOCITY TRACKER - Tracks object movement across frames
// ================================================================
/**
 * Tracks detected objects across frames to calculate velocity.
 * Uses simple position-based tracking with IoU matching.
 *
 * Key concepts:
 * - Each tracked object has a unique ID
 * - Objects are matched between frames using IoU (Intersection over Union)
 * - Velocity is calculated as position change / time delta
 */
class VelocityTracker {

    private val TAG = "IRIS_VELOCITY"

    // ================================================================
    // CONFIGURATION
    // ================================================================

    // Minimum IoU to consider same object across frames
    private val IOU_THRESHOLD = 0.3f

    // Maximum frames to keep tracking an object without seeing it
    private val MAX_MISSING_FRAMES = 5

    // Velocity smoothing factor (0-1, higher = more responsive, lower = smoother)
    private val VELOCITY_SMOOTHING = 0.4f

    // ================================================================
    // TRACKED OBJECT DATA
    // ================================================================

    /**
     * Represents an object being tracked across frames
     */
    data class TrackedObject(
        val id: Int,
        val label: String,
        var centerX: Float,
        var centerY: Float,
        var width: Float,
        var height: Float,
        var velocityX: Float = 0f,      // Normalized units per second
        var velocityY: Float = 0f,      // Normalized units per second
        var lastUpdateTime: Long = System.currentTimeMillis(),
        var missingFrames: Int = 0,
        var confidence: Float = 0f
    ) {
        /**
         * Calculate total velocity magnitude (speed)
         * @return Speed in normalized units per second
         */
        fun getSpeed(): Float = sqrt(velocityX * velocityX + velocityY * velocityY)

        /**
         * Check if object is approaching camera (moving down in frame)
         * @return true if object appears to be getting closer
         */
        fun isApproaching(): Boolean = velocityY > 0.05f

        /**
         * Check if object is moving into the safety tunnel
         */
        fun isMovingIntoPath(tunnelLeft: Float = 0.30f, tunnelRight: Float = 0.70f): Boolean {
            val inTunnel = centerX in tunnelLeft..tunnelRight
            val movingTowardsTunnel = when {
                centerX < tunnelLeft -> velocityX > 0.02f  // Left of tunnel, moving right
                centerX > tunnelRight -> velocityX < -0.02f // Right of tunnel, moving left
                else -> false
            }
            return inTunnel || movingTowardsTunnel
        }
    }

    // ================================================================
    // STATE
    // ================================================================

    private var nextObjectId = 1
    private val trackedObjects = mutableMapOf<Int, TrackedObject>()

    // ================================================================
    // MAIN TRACKING FUNCTION
    // ================================================================

    /**
     * Update tracking with new detections and return velocity-enriched results
     *
     * @param detections Current frame's detection results
     * @return List of detections with velocity information
     */
    fun updateAndGetVelocities(
        detections: List<YoloDetector.DetectionResult>
    ): List<TrackedDetection> {
        val currentTime = System.currentTimeMillis()
        val results = mutableListOf<TrackedDetection>()

        // Mark all tracked objects as potentially missing
        trackedObjects.values.forEach { it.missingFrames++ }

        // Match detections to tracked objects
        val matchedDetections = mutableSetOf<Int>()
        val matchedTrackedIds = mutableSetOf<Int>()

        for ((detectionIndex, detection) in detections.withIndex()) {
            var bestMatch: TrackedObject? = null
            var bestIoU = IOU_THRESHOLD

            // Find best matching tracked object
            for (tracked in trackedObjects.values) {
                if (tracked.id in matchedTrackedIds) continue
                if (tracked.label != detection.label) continue

                val iou = calculateIoU(
                    tracked.centerX, tracked.centerY, tracked.width, tracked.height,
                    detection.centerX, detection.centerY, detection.width, detection.height
                )

                if (iou > bestIoU) {
                    bestIoU = iou
                    bestMatch = tracked
                }
            }

            if (bestMatch != null) {
                // Update existing tracked object
                val timeDelta = (currentTime - bestMatch.lastUpdateTime) / 1000f // seconds

                if (timeDelta > 0.01f) { // Avoid division by very small numbers
                    // Calculate raw velocity
                    val rawVelX = (detection.centerX - bestMatch.centerX) / timeDelta
                    val rawVelY = (detection.centerY - bestMatch.centerY) / timeDelta

                    // Apply exponential smoothing
                    bestMatch.velocityX = VELOCITY_SMOOTHING * rawVelX +
                            (1 - VELOCITY_SMOOTHING) * bestMatch.velocityX
                    bestMatch.velocityY = VELOCITY_SMOOTHING * rawVelY +
                            (1 - VELOCITY_SMOOTHING) * bestMatch.velocityY
                }

                // Update position
                bestMatch.centerX = detection.centerX
                bestMatch.centerY = detection.centerY
                bestMatch.width = detection.width
                bestMatch.height = detection.height
                bestMatch.confidence = detection.confidence
                bestMatch.lastUpdateTime = currentTime
                bestMatch.missingFrames = 0

                matchedDetections.add(detectionIndex)
                matchedTrackedIds.add(bestMatch.id)

                // Create result with velocity
                results.add(TrackedDetection(
                    detection = detection,
                    trackId = bestMatch.id,
                    velocityX = bestMatch.velocityX,
                    velocityY = bestMatch.velocityY,
                    speed = bestMatch.getSpeed(),
                    isApproaching = bestMatch.isApproaching(),
                    isMovingIntoPath = bestMatch.isMovingIntoPath()
                ))

                // Debug: Log velocity for verification
                if (bestMatch.getSpeed() > 0.01f) {
                    Log.d(TAG, "🚀 ${detection.label} #${bestMatch.id}: speed=${String.format("%.3f", bestMatch.getSpeed())} approaching=${bestMatch.isApproaching()}")
                }

            } else {
                // Create new tracked object
                val newId = nextObjectId++
                val newTracked = TrackedObject(
                    id = newId,
                    label = detection.label,
                    centerX = detection.centerX,
                    centerY = detection.centerY,
                    width = detection.width,
                    height = detection.height,
                    confidence = detection.confidence,
                    lastUpdateTime = currentTime
                )
                trackedObjects[newId] = newTracked
                matchedDetections.add(detectionIndex)

                // New object - no velocity yet
                results.add(TrackedDetection(
                    detection = detection,
                    trackId = newId,
                    velocityX = 0f,
                    velocityY = 0f,
                    speed = 0f,
                    isApproaching = false,
                    isMovingIntoPath = detection.centerX in 0.30f..0.70f
                ))

                Log.d(TAG, "📌 New tracked object #$newId: ${detection.label}")
            }
        }

        // Remove stale tracked objects
        val staleIds = trackedObjects.filter { it.value.missingFrames > MAX_MISSING_FRAMES }.keys
        staleIds.forEach { id ->
            Log.d(TAG, "🗑️ Removing stale object #$id")
            trackedObjects.remove(id)
        }

        // Log velocity info for debugging (only if significant motion detected)
        results.filter { it.speed > 0.1f }.forEach { tracked ->
            Log.v(TAG, "🏃 Object #${tracked.trackId} ${tracked.detection.label}: " +
                    "vel=(${String.format("%.2f", tracked.velocityX)}, ${String.format("%.2f", tracked.velocityY)}) " +
                    "speed=${String.format("%.2f", tracked.speed)} " +
                    "approaching=${tracked.isApproaching}")
        }

        return results
    }

    // ================================================================
    // UTILITY FUNCTIONS
    // ================================================================

    /**
     * Calculate Intersection over Union between two bounding boxes
     */
    private fun calculateIoU(
        cx1: Float, cy1: Float, w1: Float, h1: Float,
        cx2: Float, cy2: Float, w2: Float, h2: Float
    ): Float {
        val x1Left = cx1 - w1 / 2
        val y1Top = cy1 - h1 / 2
        val x1Right = cx1 + w1 / 2
        val y1Bottom = cy1 + h1 / 2

        val x2Left = cx2 - w2 / 2
        val y2Top = cy2 - h2 / 2
        val x2Right = cx2 + w2 / 2
        val y2Bottom = cy2 + h2 / 2

        val interLeft = maxOf(x1Left, x2Left)
        val interTop = maxOf(y1Top, y2Top)
        val interRight = minOf(x1Right, x2Right)
        val interBottom = minOf(y1Bottom, y2Bottom)

        if (interLeft >= interRight || interTop >= interBottom) return 0f

        val interArea = (interRight - interLeft) * (interBottom - interTop)
        val area1 = w1 * h1
        val area2 = w2 * h2
        val unionArea = area1 + area2 - interArea

        return if (unionArea > 0) interArea / unionArea else 0f
    }

    /**
     * Reset all tracking data
     */
    fun reset() {
        trackedObjects.clear()
        nextObjectId = 1
        Log.d(TAG, "🔄 Tracking reset")
    }

    /**
     * Get number of currently tracked objects
     */
    fun getTrackedCount(): Int = trackedObjects.size

    // ================================================================
    // DATA CLASSES
    // ================================================================

    /**
     * Detection result enriched with velocity tracking information
     */
    data class TrackedDetection(
        val detection: YoloDetector.DetectionResult,
        val trackId: Int,
        val velocityX: Float,           // Normalized units per second (positive = moving right)
        val velocityY: Float,           // Normalized units per second (positive = moving down/closer)
        val speed: Float,               // Total speed magnitude
        val isApproaching: Boolean,     // Is the object getting closer?
        val isMovingIntoPath: Boolean   // Is the object moving into the walking path?
    )
}
