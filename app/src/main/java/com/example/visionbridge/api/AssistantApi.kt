package com.example.visionbridge.api

import android.content.Context
import com.example.visionbridge.data.AssistantAction
import com.example.visionbridge.data.ContextMemoryManager
import org.json.JSONObject

class AssistantApi(private val context: Context) {

    private val apiClient = ApiClient.getInstance(context)

    fun sendCommand(
        command: String,
        language: String = "en"
    ): ApiResult<AssistantAction> {
        val body = JSONObject()
            .put("command", command.trim())
            .put("context", ContextMemoryManager.toJson())
            .put("language", language)

        return when (val res = apiClient.post("/api/assistant/command", body)) {
            is ApiResult.Failure -> res
            is ApiResult.Success -> {
                val data = res.value.optJSONObject("data")
                if (data != null) {
                    val action = data.optString("action", "UNKNOWN")
                    val target = if (data.isNull("target")) null else data.optString("target")
                    val question = if (data.isNull("question")) null else data.optString("question")
                    val objectName = if (data.isNull("objectName")) null else data.optString("objectName")
                    val message = if (data.isNull("message")) null else data.optString("message")
                    val speech = if (data.isNull("speech")) null else data.optString("speech")
                    val confidence = data.optDouble("confidence", 0.8)
                    val type = if (data.isNull("type")) null else data.optString("type")

                    val assistantAction = AssistantAction(
                        action = action,
                        target = target,
                        question = question,
                        objectName = objectName,
                        message = message,
                        speech = speech,
                        confidence = confidence,
                        type = type
                    )

                    if (!speech.isNullOrBlank()) {
                        ContextMemoryManager.addTurn(command, speech)
                    }

                    ApiResult.Success(assistantAction)
                } else {
                    ApiResult.Failure(
                        ApiError(
                            kind = ApiError.Kind.MALFORMED_RESPONSE,
                            userMessage = "Could not parse assistant command response.",
                            technicalDetail = "Missing data object in assistant command response"
                        )
                    )
                }
            }
        }
    }

    fun askQuestion(
        question: String,
        language: String = "en"
    ): ApiResult<AssistantAction> {
        val body = JSONObject()
            .put("question", question.trim())
            .put("context", ContextMemoryManager.toJson())
            .put("language", language)

        return when (val res = apiClient.post("/api/assistant/ask", body)) {
            is ApiResult.Failure -> res
            is ApiResult.Success -> {
                val data = res.value.optJSONObject("data")
                if (data != null) {
                    val action = data.optString("action", "ASK_CONTEXTUAL_QUESTION")
                    val answer = data.optString("answer", data.optString("speech", ""))
                    val speech = data.optString("speech", answer)
                    val confidence = data.optDouble("confidence", 0.8)
                    val type = data.optString("type", "answer")

                    val assistantAction = AssistantAction(
                        action = action,
                        question = question,
                        speech = speech,
                        confidence = confidence,
                        type = type
                    )

                    ContextMemoryManager.addTurn(question, speech)

                    ApiResult.Success(assistantAction)
                } else {
                    ApiResult.Failure(
                        ApiError(
                            kind = ApiError.Kind.MALFORMED_RESPONSE,
                            userMessage = "Could not answer follow-up question.",
                            technicalDetail = "Missing data object in assistant ask response"
                        )
                    )
                }
            }
        }
    }
}
