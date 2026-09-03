package com.example.visionbridge.api

import android.content.Context
import com.example.visionbridge.data.NewsArticle
import com.example.visionbridge.data.NewsBriefing
import com.example.visionbridge.data.NewsLocation
import com.example.visionbridge.supabase.SupabaseClient
import com.example.visionbridge.supabase.SupabaseConfig
import org.json.JSONArray
import org.json.JSONObject

class NewsApi(private val context: Context) {

    private val supabaseClient = SupabaseClient.getInstance(context)

    fun getNewsBriefing(
        language: String = "en",
        city: String = "Pune",
        state: String = "Maharashtra",
        country: String = "India",
        category: String = "general"
    ): ApiResult<NewsBriefing> {
        val body = JSONObject()
            .put("language", language)
            .put("city", city)
            .put("state", state)
            .put("country", country)
            .put("category", category)

        return when (val res = supabaseClient.callFunction(SupabaseConfig.FUNCTION_NEWS_BRIEFING, body)) {
            is ApiResult.Failure -> res
            is ApiResult.Success -> {
                try {
                    val data = res.value.optJSONObject("data") ?: res.value
                    val cat = data.optString("category", category)
                    val locObj = data.optJSONObject("location")
                    val loc = NewsLocation(
                        city = locObj?.optString("city", city) ?: city,
                        state = locObj?.optString("state", state) ?: state,
                        country = locObj?.optString("country", country) ?: country
                    )
                    val genAt = data.optString("generatedAt", "")
                    val articlesList = mutableListOf<NewsArticle>()

                    val arr = data.optJSONArray("articles") ?: JSONArray()
                    for (i in 0 until arr.length()) {
                        val item = arr.getJSONObject(i)
                        articlesList.add(
                            NewsArticle(
                                id = item.optString("id", "news-$i"),
                                title = item.optString("title", ""),
                                description = item.optString("description", item.optString("title", "")),
                                snippet = item.optString("snippet", ""),
                                source = item.optString("source", "VisionBridge News"),
                                url = item.optString("url", ""),
                                publishedAt = item.optString("publishedAt", ""),
                                category = item.optString("category", cat),
                                language = item.optString("language", language)
                            )
                        )
                    }

                    ApiResult.Success(
                        NewsBriefing(
                            category = cat,
                            location = loc,
                            articles = articlesList,
                            generatedAt = genAt
                        )
                    )
                } catch (e: Exception) {
                    ApiResult.Failure(
                        ApiError(
                            kind = ApiError.Kind.MALFORMED_RESPONSE,
                            userMessage = "Could not format news briefing.",
                            technicalDetail = "${e.javaClass.simpleName}: ${e.message}"
                        )
                    )
                }
            }
        }
    }
}
