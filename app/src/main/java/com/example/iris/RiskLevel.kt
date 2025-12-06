package com.example.iris

// ================================================================
// RISK LEVEL ENUM - Shared across all modules
// ================================================================
enum class RiskLevel {
    CRITICAL,   // Vehicles, immediate threats (400ms announcement)
    WARNING,    // Obstacles in path (800ms announcement)
    CAUTION,    // Nearby objects (1200ms announcement)
    INFO        // Distant/safe objects (1500ms announcement)
}

// ================================================================
// RISK ASSESSMENT DATA CLASS
// ================================================================
data class RiskAssessment(
    val score: Float,           // 0.0 - 1.0
    val level: RiskLevel,
    val announcementDelayMs: Int
)

// ================================================================
// DISTANCE CALCULATOR (Rule Book Part 3)
// ================================================================
class DistanceCalculator {
    // Class-specific calibration coefficients (from rule book)
    private val formulas = mapOf(
        "person" to Triple(0.15f, 0.0082f, 0.0085f),
        "car" to Triple(0.25f, 0.0095f, 0.0092f),
        "vehicle" to Triple(0.25f, 0.0095f, 0.0092f),
        "pothole" to Triple(0.10f, 0.0075f, 0.0078f),
        "pole" to Triple(0.08f, 0.0065f, 0.0070f),
        "bicycle" to Triple(0.12f, 0.0088f, 0.0080f),
        "motorcycle" to Triple(0.12f, 0.0088f, 0.0080f)
    )

    fun estimateDistance(
        objectClass: String,
        widthNorm: Float,
        heightNorm: Float,
        imageWidth: Int,
        imageHeight: Int
    ): Float {
        // Convert normalized to pixels
        val widthPixels = widthNorm * imageWidth
        val heightPixels = heightNorm * imageHeight

        // Get formula coefficients for this class
        val (w0, w1, w2) = formulas[objectClass.lowercase()]
            ?: Triple(0.15f, 0.0082f, 0.0085f) // Default to person

        // Linear regression formula: D = w0 + w1*W + w2*H
        val distance = w0 + (w1 * widthPixels) + (w2 * heightPixels)

        return kotlin.math.max(0.1f, distance) // Minimum 10cm
    }
}

// ================================================================
// RISK SCORER (Rule Book Part 5)
// ================================================================
class RiskScorer {
    // Class danger factors (from rule book)
    private val classDangerFactors = mapOf(
        "car" to 1.0f,
        "truck" to 1.0f,
        "bus" to 0.95f,
        "vehicle" to 1.0f,
        "bicycle" to 0.7f,
        "motorcycle" to 0.9f,
        "person" to 0.7f,
        "pothole" to 0.8f,
        "wall" to 0.6f,
        "pole" to 0.6f,
        "tree" to 0.5f
    )

    fun calculateRisk(
        distance: Float,
        objectClass: String,
        velocity: Float,
        position: Float
    ): RiskAssessment {
        // 1. Distance Factor (40% weight)
        val distanceFactor = when {
            distance > 5.0f -> 0.1f
            distance > 2.0f -> 0.5f
            distance > 0.5f -> 0.9f
            else -> 1.0f
        }

        // 2. Motion Factor (30% weight)
        val motionFactor = when {
            velocity < 0.2f -> 0.2f  // Static
            velocity < 0.5f -> 0.5f  // Slow
            velocity < 2.0f -> 0.8f  // Fast
            else -> 1.0f             // Very fast
        }

        // 3. Class Factor (30% weight)
        val classFactor = classDangerFactors[objectClass.lowercase()] ?: 0.5f

        // 4. Position bonus (in tunnel = more dangerous)
        val positionBonus = if (position in 0.30f..0.70f) 0.1f else 0.0f

        // Combined risk score
        val riskScore = (distanceFactor * 0.4f) +
                (motionFactor * 0.3f) +
                (classFactor * 0.3f) +
                positionBonus

        // Determine level and timing (from rule book Part 2.2)
        return when {
            riskScore > 0.75f -> RiskAssessment(riskScore, RiskLevel.CRITICAL, 400)
            riskScore > 0.5f -> RiskAssessment(riskScore, RiskLevel.WARNING, 800)
            riskScore > 0.3f -> RiskAssessment(riskScore, RiskLevel.CAUTION, 1200)
            else -> RiskAssessment(riskScore, RiskLevel.INFO, 1500)
        }
    }
}