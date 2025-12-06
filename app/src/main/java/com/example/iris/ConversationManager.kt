package com.example.iris

import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Message(
    val role: String, // "user" or "assistant"
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

class ConversationManager(private val groqBrain: GroqBrain) {

    private val tag = "IRIS_CONV"
    private val conversationHistory = mutableListOf<Message>()
    private var currentImage: Bitmap? = null
    private val maxHistoryLength = 20 // Keep last 20 messages for context

    /**
     * Set the image context for the conversation
     * This clears previous conversation history
     */
    fun setContextImage(image: Bitmap) {
        currentImage = image
        conversationHistory.clear()
        Log.d(tag, "Context image set, conversation reset")
    }

    /**
     * Add a user message to the conversation history
     */
    private fun addUserMessage(text: String) {
        conversationHistory.add(Message("user", text))
        trimHistoryIfNeeded()
        Log.d(tag, "User said: $text")
    }

    /**
     * Add an assistant response to the conversation history
     */
    private fun addAssistantMessage(text: String) {
        conversationHistory.add(Message("assistant", text))
        trimHistoryIfNeeded()
        Log.d(tag, "IRIS responded: ${text.take(100)}...")
    }

    /**
     * Trim conversation history to prevent token limit issues
     */
    private fun trimHistoryIfNeeded() {
        if (conversationHistory.size > maxHistoryLength) {
            // Keep first message (usually the initial scene description)
            // and the most recent messages
            val firstMessage = conversationHistory.first()
            val recentMessages = conversationHistory.takeLast(maxHistoryLength - 1)
            conversationHistory.clear()
            conversationHistory.add(firstMessage)
            conversationHistory.addAll(recentMessages)
            Log.d(tag, "Trimmed conversation history to $maxHistoryLength messages")
        }
    }

    /**
     * Get a response from Groq with full conversation context
     */
    suspend fun getResponse(userMessage: String): String {
        addUserMessage(userMessage)

        return withContext(Dispatchers.IO) {
            try {
                if (currentImage == null) {
                    return@withContext "Error: No image context available. Please restart voice mode."
                }

                // Build comprehensive prompt with conversation history
                val conversationPrompt = buildConversationPrompt(userMessage)

                Log.d(tag, "Sending request to Groq with context...")

                // Call GroqBrain's analyze method (the correct method name)
                val response = groqBrain.analyze(currentImage!!, conversationPrompt)

                // Only add successful responses to history
                if (!response.startsWith("Error:")) {
                    addAssistantMessage(response)
                    Log.d(tag, "Successfully got response from Groq")
                } else {
                    Log.e(tag, "Error from Groq: $response")
                }

                response
            } catch (e: Exception) {
                Log.e(tag, "Error getting response from Groq", e)
                "Error: ${e.message ?: "Failed to get response"}"
            }
        }
    }

    /**
     * Build a prompt that includes conversation history for context
     */
    private fun buildConversationPrompt(currentUserMessage: String): String {
        val prompt = StringBuilder()

        // System instructions
        prompt.append("You are IRIS, a helpful AI vision assistant integrated into smart glasses. ")
        prompt.append("You can see what the user sees through their camera. ")
        prompt.append("Provide clear, concise, and helpful responses. ")

        // First message gets special instructions
        if (conversationHistory.size == 1) {
            prompt.append("\n\nThe user just activated voice mode. ")
            prompt.append("Analyze the image carefully and answer: $currentUserMessage\n")
            prompt.append("Keep your response under 3 sentences unless asked for more detail.")
        } else {
            // Include conversation history for context
            prompt.append("\n\nPrevious conversation about this image:\n")
            prompt.append("─────────────────────────────────\n")

            // Include recent conversation (last 10 exchanges)
            conversationHistory.takeLast(10).forEach { msg ->
                when (msg.role) {
                    "user" -> prompt.append("👤 User: ${msg.content}\n")
                    "assistant" -> prompt.append("🤖 IRIS: ${msg.content}\n")
                }
            }

            prompt.append("─────────────────────────────────\n")
            prompt.append("\nNow the user asks: $currentUserMessage\n")
            prompt.append("Respond naturally based on the conversation context and the image you see.")
        }

        return prompt.toString()
    }

    /**
     * Get formatted conversation history for display in UI
     */
    fun getConversationText(): String {
        if (conversationHistory.isEmpty()) {
            return "No conversation yet."
        }

        return conversationHistory.joinToString("\n\n") { msg ->
            val speaker = if (msg.role == "user") "You" else "IRIS"
            val icon = if (msg.role == "user") "👤" else "🤖"
            "$icon $speaker: ${msg.content}"
        }
    }

    /**
     * Clear conversation history and release image resources
     */
    fun clearConversation() {
        conversationHistory.clear()
        currentImage?.let {
            if (!it.isRecycled) {
                it.recycle()
            }
        }
        currentImage = null
        Log.d(tag, "Conversation history cleared")
    }
}