package com.example.iris

import android.util.Log

// ================================================================
// OFFLINE SCENE DESCRIBER - Generates scene descriptions without internet
// ================================================================
/**
 * Provides offline scene description using on-device YOLO detections.
 * Used as a fallback when Groq API is unavailable (no internet).
 *
 * This class generates natural language descriptions based on:
 * - Detected objects and their positions
 * - Distance estimates
 * - Risk levels
 * - Spatial relationships
 */
class OfflineSceneDescriber {

    private val TAG = "IRIS_OFFLINE"

    // ================================================================
    // MAIN DESCRIPTION FUNCTIONS
    // ================================================================

    /**
     * Generate a comprehensive scene description from YOLO detections
     * @param detections List of detected objects
     * @return Natural language description of the scene
     */
    fun describeScene(detections: List<YoloDetector.DetectionResult>): String {
        if (detections.isEmpty()) {
            return "I don't see any recognizable objects in front of you. The path appears clear."
        }

        val description = StringBuilder()

        // Group detections by category
        val people = detections.filter { it.label.lowercase() == "person" }
        val vehicles = detections.filter { it.label.lowercase() in listOf("car", "vehicle", "truck", "bus", "motorcycle", "bicycle") }
        val obstacles = detections.filter { it.label.lowercase() in listOf("pothole", "pole", "tree", "obstacle", "road-barrier") }
        val navigation = detections.filter { it.label.lowercase() in listOf("crosswalk", "stairs", "sidewalk", "traffic-light", "traffic-sign") }
        val others = detections - people.toSet() - vehicles.toSet() - obstacles.toSet() - navigation.toSet()

        // Start with overall summary
        description.append(generateOverallSummary(detections))

        // Describe people
        if (people.isNotEmpty()) {
            description.append(" ")
            description.append(describePeople(people))
        }

        // Describe vehicles (important for safety)
        if (vehicles.isNotEmpty()) {
            description.append(" ")
            description.append(describeVehicles(vehicles))
        }

        // Describe obstacles
        if (obstacles.isNotEmpty()) {
            description.append(" ")
            description.append(describeObstacles(obstacles))
        }

        // Describe navigation elements
        if (navigation.isNotEmpty()) {
            description.append(" ")
            description.append(describeNavigation(navigation))
        }

        // Describe other objects
        if (others.isNotEmpty()) {
            description.append(" ")
            description.append(describeOthers(others))
        }

        // Add safety recommendation
        description.append(" ")
        description.append(generateSafetyAdvice(detections))

        Log.d(TAG, "Generated offline description: ${description.toString().take(100)}...")
        return description.toString()
    }

    /**
     * Answer a specific question using detection data (limited capability)
     * @param question The user's question
     * @param detections List of detected objects
     * @return Answer based on available detection data
     */
    fun answerQuestion(question: String, detections: List<YoloDetector.DetectionResult>): String {
        val questionLower = question.lowercase()

        return when {
            // Questions about presence of objects
            questionLower.contains("how many") -> answerCountQuestion(question, detections)
            questionLower.contains("is there") || questionLower.contains("are there") ->
                answerPresenceQuestion(question, detections)
            questionLower.contains("where") -> answerLocationQuestion(question, detections)
            questionLower.contains("safe") || questionLower.contains("cross") ->
                answerSafetyQuestion(detections)
            questionLower.contains("close") || questionLower.contains("near") ||
                    questionLower.contains("far") || questionLower.contains("distance") ->
                answerDistanceQuestion(question, detections)
            questionLower.contains("what") && (questionLower.contains("see") ||
                    questionLower.contains("front") || questionLower.contains("around")) ->
                describeScene(detections)
            questionLower.contains("moving") || questionLower.contains("coming") ->
                answerMovementQuestion(detections)

            // Default fallback
            else -> {
                "I'm currently offline and can only answer basic questions about what I detect. " +
                        "I can see: ${detections.map { it.label }.distinct().joinToString(", ")}. " +
                        "Try asking 'What do you see?' or 'Is there a person nearby?'"
            }
        }
    }

    // ================================================================
    // DESCRIPTION HELPERS
    // ================================================================

    private fun generateOverallSummary(detections: List<YoloDetector.DetectionResult>): String {
        val count = detections.size
        val criticalCount = detections.count { it.riskLevel == RiskLevel.CRITICAL }
        val warningCount = detections.count { it.riskLevel == RiskLevel.WARNING }

        return when {
            criticalCount > 0 -> "I detect $count objects, with $criticalCount requiring immediate attention."
            warningCount > 0 -> "I see $count objects around you, some need caution."
            count > 5 -> "This is a busy area with $count detected objects."
            count > 0 -> "I can see $count objects in your surroundings."
            else -> "The area appears clear."
        }
    }

    private fun describePeople(people: List<YoloDetector.DetectionResult>): String {
        if (people.isEmpty()) return ""

        val closest = people.minByOrNull { it.distance }!!
        val count = people.size

        val position = getPositionDescription(closest.centerX)
        val distance = formatDistance(closest.distance)

        return when (count) {
            1 -> "There is a person $distance $position."
            2 -> "There are 2 people nearby, the closest is $distance $position."
            else -> "There are $count people around you, the closest is $distance $position."
        }
    }

    private fun describeVehicles(vehicles: List<YoloDetector.DetectionResult>): String {
        if (vehicles.isEmpty()) return ""

        val descriptions = vehicles.take(3).map { vehicle ->
            val position = getPositionDescription(vehicle.centerX)
            val distance = formatDistance(vehicle.distance)
            val moving = if (vehicle.isApproaching) ", and it's moving" else ""
            "${vehicle.label} $distance $position$moving"
        }

        return when (vehicles.size) {
            1 -> "There is a ${descriptions[0]}."
            else -> "Vehicles detected: ${descriptions.joinToString("; ")}."
        }
    }

    private fun describeObstacles(obstacles: List<YoloDetector.DetectionResult>): String {
        if (obstacles.isEmpty()) return ""

        val inPath = obstacles.filter { it.centerX in 0.30f..0.70f }
        val closest = obstacles.minByOrNull { it.distance }!!

        return when {
            inPath.isNotEmpty() -> {
                val obs = inPath.first()
                "Warning: ${obs.label} in your path at ${formatDistance(obs.distance)}."
            }
            else -> {
                "There is a ${closest.label} ${formatDistance(closest.distance)} ${getPositionDescription(closest.centerX)}."
            }
        }
    }

    private fun describeNavigation(navigation: List<YoloDetector.DetectionResult>): String {
        if (navigation.isEmpty()) return ""

        val items = navigation.map { "${it.label} ${getPositionDescription(it.centerX)}" }
        return "Navigation elements: ${items.joinToString(", ")}."
    }

    private fun describeOthers(others: List<YoloDetector.DetectionResult>): String {
        if (others.isEmpty()) return ""
        if (others.size > 3) {
            val labels = others.map { it.label }.distinct().take(3)
            return "I also see: ${labels.joinToString(", ")}."
        }
        return "I also see: ${others.map { it.label }.distinct().joinToString(", ")}."
    }

    private fun generateSafetyAdvice(detections: List<YoloDetector.DetectionResult>): String {
        val criticalInPath = detections.filter {
            it.riskLevel == RiskLevel.CRITICAL && it.centerX in 0.30f..0.70f
        }
        val approachingObjects = detections.filter { it.isApproaching }

        return when {
            criticalInPath.isNotEmpty() -> "Please stop, there's an obstacle directly in your path."
            approachingObjects.isNotEmpty() -> "Be cautious, something is moving towards you."
            detections.any { it.riskLevel == RiskLevel.WARNING } -> "Proceed with caution."
            else -> "The path ahead seems clear."
        }
    }

    // ================================================================
    // QUESTION ANSWERING HELPERS
    // ================================================================

    private fun answerCountQuestion(question: String, detections: List<YoloDetector.DetectionResult>): String {
        val questionLower = question.lowercase()

        // Find what object they're asking about
        val objectTypes = listOf("person", "people", "car", "vehicle", "obstacle", "tree", "pole")
        val askedObject = objectTypes.find { questionLower.contains(it) }

        return if (askedObject != null) {
            val searchTerm = if (askedObject == "people") "person" else askedObject
            val count = detections.count { it.label.lowercase().contains(searchTerm) }
            when (count) {
                0 -> "I don't see any ${askedObject}s."
                1 -> "I see 1 $askedObject."
                else -> "I count $count ${askedObject}s."
            }
        } else {
            "I can see ${detections.size} objects in total."
        }
    }

    private fun answerPresenceQuestion(question: String, detections: List<YoloDetector.DetectionResult>): String {
        val questionLower = question.lowercase()
        val allLabels = detections.map { it.label.lowercase() }

        // Common objects to check for
        val objectsToCheck = listOf(
            "person" to listOf("person", "people"),
            "car" to listOf("car", "vehicle"),
            "crosswalk" to listOf("crosswalk", "crossing"),
            "stairs" to listOf("stairs", "steps"),
            "obstacle" to listOf("obstacle", "barrier"),
            "pothole" to listOf("pothole", "hole"),
            "traffic light" to listOf("traffic-light", "traffic light", "signal")
        )

        for ((name, variants) in objectsToCheck) {
            if (variants.any { questionLower.contains(it) }) {
                val found = allLabels.any { label -> variants.any { label.contains(it) } }
                return if (found) {
                    val detection = detections.find { d -> variants.any { d.label.lowercase().contains(it) } }
                    "Yes, there is a $name ${formatDistance(detection?.distance ?: 0f)} ${getPositionDescription(detection?.centerX ?: 0.5f)}."
                } else {
                    "No, I don't see any $name nearby."
                }
            }
        }

        return "I can see: ${allLabels.distinct().joinToString(", ")}."
    }

    private fun answerLocationQuestion(question: String, detections: List<YoloDetector.DetectionResult>): String {
        val questionLower = question.lowercase()

        // Find what they're asking about
        for (detection in detections) {
            if (questionLower.contains(detection.label.lowercase())) {
                val position = getPositionDescription(detection.centerX)
                val distance = formatDistance(detection.distance)
                return "The ${detection.label} is $distance $position."
            }
        }

        return "I'm not sure what you're looking for. I can see: ${detections.map { it.label }.distinct().take(5).joinToString(", ")}."
    }

    private fun answerSafetyQuestion(detections: List<YoloDetector.DetectionResult>): String {
        val vehiclesNearby = detections.filter {
            it.label.lowercase() in listOf("car", "vehicle", "truck", "bus", "motorcycle") &&
                    it.distance < 5f
        }
        val crosswalk = detections.find { it.label.lowercase() == "crosswalk" }
        val trafficLight = detections.find { it.label.lowercase().contains("traffic") }

        return when {
            vehiclesNearby.any { it.isApproaching } ->
                "No, it's not safe. There are vehicles approaching."
            vehiclesNearby.any { it.distance < 3f } ->
                "Be very careful. There are vehicles close by at ${formatDistance(vehiclesNearby.minOf { it.distance })}."
            crosswalk != null && vehiclesNearby.isEmpty() ->
                "There's a crosswalk ahead and no vehicles nearby. It seems safe to cross, but please be careful."
            trafficLight != null ->
                "There's a traffic light ahead. I cannot tell the color, so please listen for audio signals or ask someone nearby."
            vehiclesNearby.isEmpty() ->
                "I don't see any vehicles nearby. It appears safe, but always be cautious."
            else ->
                "There are vehicles in the area. Please proceed with caution."
        }
    }

    private fun answerDistanceQuestion(question: String, detections: List<YoloDetector.DetectionResult>): String {
        if (detections.isEmpty()) {
            return "I don't see any objects to measure distance to."
        }

        val questionLower = question.lowercase()

        // Check if asking about specific object
        for (detection in detections) {
            if (questionLower.contains(detection.label.lowercase())) {
                return "The ${detection.label} is approximately ${formatDistance(detection.distance)} away, ${getPositionDescription(detection.centerX)}."
            }
        }

        // General closest object
        val closest = detections.minByOrNull { it.distance }!!
        return "The closest object is a ${closest.label} at ${formatDistance(closest.distance)} ${getPositionDescription(closest.centerX)}."
    }

    private fun answerMovementQuestion(detections: List<YoloDetector.DetectionResult>): String {
        val approaching = detections.filter { it.isApproaching }

        return when {
            approaching.isEmpty() -> "I don't detect any objects moving towards you."
            approaching.size == 1 -> {
                val obj = approaching.first()
                "Yes, there's a ${obj.label} moving towards you from ${getPositionDescription(obj.centerX)}."
            }
            else -> {
                val labels = approaching.map { it.label }.distinct().joinToString(", ")
                "There are ${approaching.size} objects moving towards you: $labels."
            }
        }
    }

    // ================================================================
    // UTILITY FUNCTIONS
    // ================================================================

    private fun getPositionDescription(centerX: Float): String {
        return when {
            centerX < 0.25f -> "on your far left"
            centerX < 0.40f -> "on your left"
            centerX < 0.60f -> "directly ahead"
            centerX < 0.75f -> "on your right"
            else -> "on your far right"
        }
    }

    private fun formatDistance(distance: Float): String {
        return when {
            distance < 0.5f -> "very close, less than half a meter"
            distance < 1f -> "about ${String.format("%.1f", distance)} meters"
            distance < 2f -> "about ${distance.toInt()} to ${distance.toInt() + 1} meters"
            else -> "approximately ${distance.toInt()} meters"
        }
    }
}
