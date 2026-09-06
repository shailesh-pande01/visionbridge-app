package com.example.visionbridge.api

import android.content.Context
import android.util.Log
import com.example.visionbridge.data.AssistantAction
import com.example.visionbridge.data.ContextMemoryManager
import com.example.visionbridge.supabase.SupabaseClient
import com.example.visionbridge.supabase.SupabaseConfig
import com.example.visionbridge.voice.VoiceActionRouter
import com.example.visionbridge.voice.VoiceActions
import org.json.JSONObject

class AssistantApi(private val context: Context) {

    companion object {
        private const val TAG = "VB_VOICE_GEMINI"

        fun isAcknowledgment(text: String?): Boolean {
            if (text.isNullOrBlank()) return true
            val regex = Regex("(?i)^(?:i'?ll check|let me check|checking|one moment|i can check|i will check|main check|मैं देखता|मैं चेक|मी तपासतो|मी चेक).*")
            return regex.containsMatchIn(text.trim())
        }
    }

    private val supabaseClient = SupabaseClient.getInstance(context)
    private val apiClient = ApiClient.getInstance(context)

    fun sendCommand(
        command: String,
        language: String = "en"
    ): ApiResult<AssistantAction> {
        val trimmedCommand = command.trim()
        val contextJson = ContextMemoryManager.buildContextPayloadForCommand(trimmedCommand)

        val body = JSONObject()
            .put("command", trimmedCommand)
            .put("context", contextJson)
            .put("language", language)

        Log.d(TAG, "Sending voice command to Gemini: '$trimmedCommand' (language=$language, activeFeature=${contextJson.optString("activeFeature")})")

        // 1. Try Supabase Edge Function first
        val supabaseResult = supabaseClient.callFunction(SupabaseConfig.FUNCTION_ASSISTANT_COMMAND, body)
        if (supabaseResult is ApiResult.Success) {
            val action = parseAssistantResponse(trimmedCommand, supabaseResult.value, language)
            Log.d(TAG, "Gemini intent resolved via Supabase Edge Function: action=${action.action}, target=${action.target}")
            return ApiResult.Success(action)
        }

        Log.w(TAG, "Supabase edge function failed, falling back to MERN backend locator: ${(supabaseResult as? ApiResult.Failure)?.error?.userMessage}")

        // 2. Fallback to MERN Backend API
        val backendResult = apiClient.post("/api/assistant/command", body)
        if (backendResult is ApiResult.Success) {
            val action = parseAssistantResponse(trimmedCommand, backendResult.value, language)
            Log.d(TAG, "Gemini intent resolved via MERN backend: action=${action.action}, target=${action.target}")
            return ApiResult.Success(action)
        }

        // 3. Graceful Offline / Unreachable Fallback
        val offlineSpeech = getLocalizedErrorMessage(language)
        Log.e(TAG, "Both Supabase and MERN backend failed for voice command. Returning graceful offline action.")
        return ApiResult.Success(
            AssistantAction(
                action = VoiceActions.UNKNOWN,
                speech = offlineSpeech,
                type = "error",
                confidence = 0.0
            )
        )
    }

    fun askQuestion(
        question: String,
        language: String = "en"
    ): ApiResult<AssistantAction> {
        val trimmedQuestion = question.trim()
        val contextJson = ContextMemoryManager.toJson()
        val readingSession = ContextMemoryManager.getActiveReadingSession()
        val readingText = readingSession?.extractedText ?: contextJson.optString("readingText", contextJson.optString("contextSummary"))

        // Fast path: Try local grounded QA first for immediate zero-latency answer
        val localQa = com.example.visionbridge.voice.SmartReadingGroundedQa.answer(readingText, trimmedQuestion, language)
        if (localQa.answered) {
            Log.d("VB_VOICE_FOLLOWUP", "Local Grounded Q&A answered: '${localQa.answer}'")
            val action = AssistantAction(
                action = VoiceActions.ASK_CONTEXTUAL_QUESTION,
                question = trimmedQuestion,
                answer = localQa.answer,
                speech = localQa.answer,
                confidence = localQa.confidence,
                type = "answer"
            )
            ContextMemoryManager.addTurn(trimmedQuestion, localQa.answer)
            return ApiResult.Success(action)
        }

        val body = JSONObject()
            .put("question", trimmedQuestion)
            .put("readingText", readingText)
            .put("context", contextJson)
            .put("language", language)

        Log.d("VB_VOICE_FOLLOWUP", "Sending follow-up question: '$trimmedQuestion' (language=$language, textLength=${readingText.length})")

        // 1. Try Supabase Edge Function first
        val supabaseResult = supabaseClient.callFunction(SupabaseConfig.FUNCTION_ASSISTANT_ASK, body)
        if (supabaseResult is ApiResult.Success) {
            val action = parseAskResponse(trimmedQuestion, supabaseResult.value, language)
            if (!isAcknowledgment(action.speech) && !action.speech.isNullOrBlank()) {
                return ApiResult.Success(action)
            }
        }

        // 2. Fallback to MERN Backend API
        val backendResult = apiClient.post("/api/assistant/ask", body)
        if (backendResult is ApiResult.Success) {
            val action = parseAskResponse(trimmedQuestion, backendResult.value, language)
            if (!isAcknowledgment(action.speech) && !action.speech.isNullOrBlank()) {
                return ApiResult.Success(action)
            }
        }

        // 3. Graceful Offline / Fallback
        val fallbackText = if (readingText.isNotBlank()) {
            when (language) {
                "hi" -> "मुझे कैप्चर किए गए दस्तावेज़ में यह जानकारी नहीं मिली।"
                "mr" -> "मला कॅप्चर केलेल्या दस्तऐवजात ही माहिती सापडली नाही."
                else -> "I couldn't find that in the text I captured."
            }
        } else {
            getLocalizedErrorMessage(language)
        }

        return ApiResult.Success(
            AssistantAction(
                action = VoiceActions.ASK_CONTEXTUAL_QUESTION,
                question = trimmedQuestion,
                speech = fallbackText,
                answer = fallbackText,
                type = "answer",
                confidence = 0.5
            )
        )
    }

    private fun parseAssistantResponse(userUtterance: String, json: JSONObject, language: String): AssistantAction {
        val data = json.optJSONObject("data") ?: json
        val rawAction = data.optString("action", VoiceActions.UNKNOWN)
        val target = if (data.isNull("target")) null else data.optString("target")
        val question = if (data.isNull("question")) null else data.optString("question")
        val answer = if (data.isNull("answer")) null else data.optString("answer")
        val objectName = if (data.isNull("objectName")) null else data.optString("objectName")
        val contactName = if (data.isNull("contactName")) null else data.optString("contactName")
        val phoneNumber = if (data.isNull("phoneNumber")) null else data.optString("phoneNumber")
        val message = if (data.isNull("message")) null else data.optString("message")
        val speech = if (data.isNull("speech")) (answer ?: message) else data.optString("speech")
        val storyQuery = if (data.isNull("storyQuery")) null else data.optString("storyQuery")
        val genre = if (data.isNull("genre")) null else data.optString("genre")
        val newsCategory = if (data.isNull("newsCategory")) null else data.optString("newsCategory")
        val speed = if (data.isNull("speed")) null else data.optString("speed")
        val newsLang = if (data.isNull("newsLang")) null else data.optString("newsLang")
        val needsNewImage = data.optBoolean("needsNewImage", false)
        val confidence = data.optDouble("confidence", 0.85)
        val type = if (data.isNull("type")) null else data.optString("type")

        val rawAssistantAction = AssistantAction(
            action = rawAction,
            target = target,
            question = question,
            answer = answer,
            objectName = objectName,
            contactName = contactName,
            phoneNumber = phoneNumber,
            message = message,
            speech = speech,
            storyQuery = storyQuery,
            genre = genre,
            newsCategory = newsCategory,
            speed = speed,
            newsLang = newsLang,
            needsNewImage = needsNewImage,
            confidence = confidence,
            type = type
        )

        // Strict allowlist validation
        val validatedAction = VoiceActionRouter.validateAction(rawAssistantAction)

        // If Gemini classified as ASK_CONTEXTUAL_QUESTION but speech is a generic acknowledgment,
        // intercept and resolve the true grounded answer immediately.
        if (validatedAction.action == VoiceActions.ASK_CONTEXTUAL_QUESTION &&
            (isAcknowledgment(validatedAction.speech) || validatedAction.speech.isNullOrBlank())
        ) {
            val readingSession = ContextMemoryManager.getActiveReadingSession()
            val text = readingSession?.extractedText ?: ""
            val localQa = com.example.visionbridge.voice.SmartReadingGroundedQa.answer(text, question ?: userUtterance, language)
            if (localQa.answered) {
                return validatedAction.copy(
                    speech = localQa.answer,
                    answer = localQa.answer,
                    confidence = localQa.confidence
                )
            }
        }

        if (!validatedAction.speech.isNullOrBlank()) {
            ContextMemoryManager.addTurn(userUtterance, validatedAction.speech)
        }

        return validatedAction
    }

    private fun parseAskResponse(question: String, json: JSONObject, language: String): AssistantAction {
        val data = json.optJSONObject("data") ?: json
        val rawAction = data.optString("action", VoiceActions.ASK_CONTEXTUAL_QUESTION)
        val answer = data.optString("answer", data.optString("speech", ""))
        val speech = data.optString("speech", answer)
        val needsNewImage = data.optBoolean("needsNewImage", false)
        val confidence = data.optDouble("confidence", 0.85)
        val type = data.optString("type", "answer")

        val action = AssistantAction(
            action = rawAction,
            question = question,
            answer = answer,
            speech = speech,
            needsNewImage = needsNewImage,
            confidence = confidence,
            type = type
        )

        if (speech.isNotBlank()) {
            ContextMemoryManager.addTurn(question, speech)
        }

        return action
    }


    private fun getLocalizedErrorMessage(language: String): String {
        return when (language.lowercase()) {
            "hi" -> "मैं अभी इस कमांड को संसाधित नहीं कर सका। कृपया अपना कनेक्शन जांचें।"
            "mr" -> "मी ही आज्ञा प्रक्रिया करू शकलो नाही. कृपया तुमचे कनेक्शन तपासा."
            else -> "I couldn't process that command right now. Please check your connection."
        }
    }
}
