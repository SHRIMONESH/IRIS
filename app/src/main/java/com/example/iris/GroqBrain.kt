package com.example.iris

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

class GroqBrain {

    private val TAG = "IRIS_GROQ"

    // --- CONFIGURATION ---
    // API Key: Your Groq API key (⚠️ Move to secure storage in production)
    private val apiKey = "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"

    // Primary Model: Llama 4 Scout (Active as of Dec 2025)
    private val PRIMARY_MODEL = "meta-llama/llama-4-scout-17b-16e-instruct"

    // Fallback Model: Llama 4 Maverick (in case Scout fails)
    private val FALLBACK_MODEL = "meta-llama/llama-4-maverick-17b-128e-instruct"

    // Groq API Endpoint (OpenAI-compatible)
    private val API_URL = "https://api.groq.com/openai/v1/chat/completions"

    // HTTP Client with optimized timeouts for voice interactions
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS) // Increased for complex prompts
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true) // Auto-retry on network hiccups
        .build()

    // Cache for encoded image to avoid re-encoding on every request
    private var cachedImageData: String? = null
    private var cachedImageHash: Int? = null

    /**
     * Main analyze function - processes image with user prompt
     * Automatically falls back to secondary model if primary fails
     */
    suspend fun analyze(image: Bitmap, userPrompt: String): String {
        return withContext(Dispatchers.IO) {
            Log.d(TAG, "Starting analysis with prompt: ${userPrompt.take(50)}...")

            // Try primary model first
            var result = tryAnalyze(image, userPrompt, PRIMARY_MODEL)

            // If primary fails due to model issues, try fallback
            if (result.startsWith("Error:") &&
                (result.contains("model", ignoreCase = true) ||
                        result.contains("decommission", ignoreCase = true))) {
                Log.w(TAG, "Primary model ($PRIMARY_MODEL) failed, trying fallback...")
                result = tryAnalyze(image, userPrompt, FALLBACK_MODEL)
            }

            return@withContext result
        }
    }

    /**
     * Analyze with vision - single call with image and prompt
     * This is an alias for the analyze() method for better naming clarity
     */
    suspend fun analyzeWithVision(image: Bitmap, userPrompt: String): String {
        return analyze(image, userPrompt)
    }

    /**
     * Analyze with conversation history for multi-turn conversations
     * More efficient for voice mode - reuses encoded image
     */
    suspend fun analyzeWithHistory(
        image: Bitmap,
        conversationHistory: List<Pair<String, String>>, // List of (role, content)
        currentPrompt: String
    ): String {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Analyzing with ${conversationHistory.size} messages in history")

                // Encode image (with caching)
                val base64Image = encodeImageCached(image)
                val imageDataUrl = "data:image/jpeg;base64,$base64Image"

                // Build messages array with history
                val messagesArray = JSONArray()

                // Add conversation history (text-only)
                conversationHistory.forEach { (role, content) ->
                    messagesArray.put(JSONObject().apply {
                        put("role", role)
                        put("content", content)
                    })
                }

                // Add current message with image
                messagesArray.put(JSONObject().apply {
                    put("role", "user")
                    put("content", JSONArray().apply {
                        // Text part
                        put(JSONObject().apply {
                            put("type", "text")
                            put("text", currentPrompt)
                        })
                        // Image part (only in current message)
                        put(JSONObject().apply {
                            put("type", "image_url")
                            put("image_url", JSONObject().apply {
                                put("url", imageDataUrl)
                            })
                        })
                    })
                })

                // Build request payload
                val jsonBody = JSONObject().apply {
                    put("model", PRIMARY_MODEL)
                    put("messages", messagesArray)
                    put("max_tokens", 512) // Optimized for voice responses
                    put("temperature", 0.7)
                    put("top_p", 0.9)
                }

                // Send request
                val response = sendRequest(jsonBody)
                return@withContext response

            } catch (e: Exception) {
                Log.e(TAG, "Error in analyzeWithHistory", e)
                "Error: ${e.message ?: "Failed to process request"}"
            }
        }
    }

    /**
     * Internal function to try analysis with a specific model
     */
    private suspend fun tryAnalyze(image: Bitmap, userPrompt: String, modelId: String): String {
        return try {
            Log.d(TAG, "Encoding image for Groq ($modelId)...")

            // 1. Compress & Encode Image
            val base64Image = encodeImage(image)
            val imageDataUrl = "data:image/jpeg;base64,$base64Image"

            // 2. Build JSON Payload (OpenAI Vision API Format)
            val jsonBody = JSONObject().apply {
                put("model", modelId)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", JSONArray().apply {
                            // Text Part
                            put(JSONObject().apply {
                                put("type", "text")
                                put("text", userPrompt)
                            })
                            // Image Part
                            put(JSONObject().apply {
                                put("type", "image_url")
                                put("image_url", JSONObject().apply {
                                    put("url", imageDataUrl)
                                })
                            })
                        })
                    })
                })
                put("max_tokens", 512) // Optimized for quick voice responses
                put("temperature", 0.7) // Balanced creativity
                put("top_p", 0.9) // Focused sampling
            }

            // 3. Send Request and Parse Response
            return sendRequest(jsonBody)

        } catch (e: java.net.SocketTimeoutException) {
            Log.e(TAG, "Request timeout", e)
            "Error: Request timed out. Please try again."
        } catch (e: java.net.UnknownHostException) {
            Log.e(TAG, "Network error", e)
            "Error: No internet connection. Please check your network."
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error in tryAnalyze", e)
            "Error: ${e.message ?: "Something went wrong"}"
        }
    }

    /**
     * Send HTTP request to Groq API and handle response
     */
    private fun sendRequest(jsonBody: JSONObject): String {
        val request = Request.Builder()
            .url(API_URL)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        Log.d(TAG, "Sending request to Groq API...")
        val response = client.newCall(request).execute()
        val responseBody = response.body?.string()

        // Parse successful response
        if (response.isSuccessful && responseBody != null) {
            try {
                val jsonResponse = JSONObject(responseBody)
                val content = jsonResponse.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")

                Log.d(TAG, "✓ Success: ${content.take(100)}...")
                return content
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse response", e)
                return "Error: Invalid response format from server"
            }
        }

        // Handle error responses
        Log.e(TAG, "Error Response (${response.code}): $responseBody")
        return parseErrorResponse(response.code, responseBody)
    }

    /**
     * Parse and format error messages from Groq API
     */
    private fun parseErrorResponse(code: Int, responseBody: String?): String {
        val errorMsg = try {
            val errorJson = JSONObject(responseBody ?: "{}")
            if (errorJson.has("error")) {
                val error = errorJson.getJSONObject("error")
                error.optString("message", "Unknown error")
            } else {
                "HTTP $code"
            }
        } catch (e: Exception) {
            "HTTP $code"
        }

        return when (code) {
            401 -> "Error: Invalid API key. Please check your Groq credentials."
            404 -> "Error: Model not found. The model may have been deprecated."
            429 -> "Error: Rate limit exceeded. Please wait a moment."
            400 -> "Error: Bad request - $errorMsg"
            413 -> "Error: Image too large. Maximum size is 4MB."
            500 -> "Error: Server error. Please try again later."
            503 -> "Error: Service temporarily unavailable."
            else -> "Error: $errorMsg"
        }
    }

    /**
     * Encode image to base64 with caching for efficiency
     * Reuses cached data if same image is analyzed multiple times
     */
    private fun encodeImageCached(bitmap: Bitmap): String {
        val imageHash = bitmap.hashCode()

        // Return cached data if same image
        if (imageHash == cachedImageHash && cachedImageData != null) {
            Log.d(TAG, "Using cached image data")
            return cachedImageData!!
        }

        // Encode new image
        val encoded = encodeImage(bitmap)
        cachedImageData = encoded
        cachedImageHash = imageHash

        return encoded
    }

    /**
     * Encode bitmap to base64 with optimal compression
     */
    private fun encodeImage(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()

        // Resize to max 800x800 to stay under 4MB base64 limit
        val maxDimension = 800
        val scale = minOf(
            maxDimension.toFloat() / bitmap.width,
            maxDimension.toFloat() / bitmap.height,
            1f // Don't upscale small images
        )

        val scaledWidth = (bitmap.width * scale).toInt()
        val scaledHeight = (bitmap.height * scale).toInt()

        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
        } else {
            bitmap
        }

        // Compress with quality 75 for optimal balance
        scaled.compress(Bitmap.CompressFormat.JPEG, 75, outputStream)

        val bytes = outputStream.toByteArray()
        val sizeKB = bytes.size / 1024
        Log.d(TAG, "Image encoded: ${sizeKB}KB (${scaledWidth}x${scaledHeight})")

        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    /**
     * Clear cached image data
     */
    fun clearCache() {
        cachedImageData = null
        cachedImageHash = null
        Log.d(TAG, "Image cache cleared")
    }

    /**
     * Test API connectivity
     */
    suspend fun testConnection(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val testBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
                val result = analyze(testBitmap, "Test connection")
                testBitmap.recycle()
                !result.startsWith("Error:")
            } catch (e: Exception) {
                Log.e(TAG, "Connection test failed", e)
                false
            }
        }
    }

    /**
     * Get current model info
     */
    fun getModelInfo(): String {
        return "Primary: $PRIMARY_MODEL\nFallback: $FALLBACK_MODEL"
    }
}