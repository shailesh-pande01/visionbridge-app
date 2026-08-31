package com.example.visionbridge.api

import android.content.Context
import com.example.visionbridge.data.User
import org.json.JSONObject

class AuthApi(private val context: Context) {

    private val apiClient = ApiClient.getInstance(context)

    fun register(
        name: String,
        username: String,
        password: String,
        role: String
    ): ApiResult<User> {
        val body = JSONObject()
            .put("name", name.trim())
            .put("username", username.trim())
            .put("password", password)
            .put("role", role)

        return when (val res = apiClient.post("/api/auth/register", body)) {
            is ApiResult.Failure -> res
            is ApiResult.Success -> {
                val data = res.value.optJSONObject("data")
                if (data != null) {
                    val user = User(
                        id = data.optString("_id", ""),
                        name = data.optString("name", name),
                        username = data.optString("username", username),
                        role = data.optString("role", role),
                        token = data.optString("token", "")
                    )
                    ApiResult.Success(user)
                } else {
                    ApiResult.Failure(
                        ApiError(
                            kind = ApiError.Kind.MALFORMED_RESPONSE,
                            userMessage = "Registration failed. Invalid server response.",
                            technicalDetail = "Missing data object in register response"
                        )
                    )
                }
            }
        }
    }

    fun login(
        username: String,
        password: String
    ): ApiResult<User> {
        val body = JSONObject()
            .put("username", username.trim())
            .put("password", password)

        return when (val res = apiClient.post("/api/auth/login", body)) {
            is ApiResult.Failure -> res
            is ApiResult.Success -> {
                val data = res.value.optJSONObject("data")
                if (data != null) {
                    val user = User(
                        id = data.optString("_id", ""),
                        name = data.optString("name", username),
                        username = data.optString("username", username),
                        role = data.optString("role", "lowVisionUser"),
                        token = data.optString("token", "")
                    )
                    ApiResult.Success(user)
                } else {
                    ApiResult.Failure(
                        ApiError(
                            kind = ApiError.Kind.MALFORMED_RESPONSE,
                            userMessage = "Login failed. Invalid server response.",
                            technicalDetail = "Missing data object in login response"
                        )
                    )
                }
            }
        }
    }

    fun getMe(): ApiResult<User> {
        return when (val res = apiClient.get("/api/auth/me", authRequired = true)) {
            is ApiResult.Failure -> res
            is ApiResult.Success -> {
                val data = res.value.optJSONObject("data")
                if (data != null) {
                    val user = User(
                        id = data.optString("_id", ""),
                        name = data.optString("name", ""),
                        username = data.optString("username", ""),
                        role = data.optString("role", "lowVisionUser")
                    )
                    ApiResult.Success(user)
                } else {
                    ApiResult.Failure(
                        ApiError(
                            kind = ApiError.Kind.MALFORMED_RESPONSE,
                            userMessage = "Could not fetch user profile.",
                            technicalDetail = "Missing data object in getMe response"
                        )
                    )
                }
            }
        }
    }
}
