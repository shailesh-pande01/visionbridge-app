package com.example.visionbridge.data

data class NewsArticle(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val snippet: String = "",
    val source: String = "",
    val url: String = "",
    val publishedAt: String = "",
    val category: String = "general",
    val language: String = "en"
)

data class NewsLocation(
    val city: String = "Pune",
    val state: String = "Maharashtra",
    val country: String = "India"
)

data class NewsBriefing(
    val category: String = "general",
    val location: NewsLocation = NewsLocation(),
    val articles: List<NewsArticle> = emptyList(),
    val generatedAt: String = ""
)

enum class NewsPlaybackState {
    IDLE,
    LOADING,
    PREPARING,
    SPEAKING,
    PAUSED,
    COMPLETED,
    ERROR,
    STOPPED
}
