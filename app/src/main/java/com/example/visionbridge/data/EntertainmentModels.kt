package com.example.visionbridge.data

// ── Radio Models ──────────────────────────────────────────────────

data class RadioStation(
    val id: String,
    val name: String,
    val streamUrl: String,
    val city: String = "",
    val state: String = "",
    val country: String = "",
    val countryCode: String = "IN",
    val language: String = "",
    val tags: List<String> = emptyList(),
    val codec: String = "MP3",
    val bitrate: Int = 128,
    val isHttps: Boolean = true,
    val votes: Int = 0,
    val clickcount: Int = 0,
    val favicon: String = "",
    val homepage: String = ""
)

data class RadioLocation(
    val city: String = "",
    val state: String = "",
    val country: String = "",
    val countryCode: String = "IN"
)

data class RadioSegment(
    val title: String,
    val topic: String,
    val content: String,
    val speech: String
)

// ── Story Models ──────────────────────────────────────────────────

data class StoryCoverTheme(
    val primaryColor: String = "#00D4FF",
    val secondaryColor: String = "#0A2540",
    val icon: String = "📖",
    val accent: String = "blue"
)

data class StoryChapter(
    val chapterNumber: Int,
    val title: String,
    val audioUrl: String = "",
    val duration: String = "",
    val summary: String = "",
    val text: String = ""
) {
    val isAudiobook: Boolean
        get() = audioUrl.isNotBlank() && audioUrl.startsWith("http")
}

data class StorySummary(
    val id: String,
    val title: String,
    val author: String,
    val publicationYear: Int = 1900,
    val genre: String,
    val language: String,
    val audioLanguageNotice: String = "",
    val source: String = "",
    val sourceUrl: String = "",
    val librivoxUrl: String = "",
    val rightsStatus: String = "Public Domain",
    val audioAvailable: Boolean = false,
    val textAvailable: Boolean = true,
    val approxDuration: String = "",
    val totalChapters: Int = 1,
    val chapterCount: Int = 1,
    val description: String = "",
    val coverTheme: StoryCoverTheme = StoryCoverTheme()
)

data class StoryDetail(
    val id: String,
    val title: String,
    val author: String,
    val publicationYear: Int = 1900,
    val genre: String,
    val language: String,
    val audioLanguageNotice: String = "",
    val source: String = "",
    val sourceUrl: String = "",
    val librivoxUrl: String = "",
    val rightsStatus: String = "Public Domain",
    val audioAvailable: Boolean = false,
    val textAvailable: Boolean = true,
    val approxDuration: String = "",
    val totalChapters: Int = 1,
    val description: String = "",
    val coverTheme: StoryCoverTheme = StoryCoverTheme(),
    val chapters: List<StoryChapter> = emptyList()
)

data class StoryProgressData(
    val id: String? = null,
    val userId: String? = null,
    val storyId: String,
    val chapterIndex: Int = 0,
    val positionSeconds: Int = 0,
    val storyTitle: String = "",
    val chapterTitle: String = "",
    val author: String = "",
    val genre: String = "classics",
    val lastPlayedAt: String? = null,
    val updatedAt: String? = null
)

// ── Audio Games Models ─────────────────────────────────────────────

data class TriviaQuestion(
    val category: String = "General",
    val question: String,
    val answer: String,
    val options: List<String> = emptyList()
)

data class RiddleItem(
    val riddle: String,
    val answer: String,
    val clue: String = ""
)

data class MemoryChallengeData(
    val level: Int = 1,
    val words: List<String> = emptyList(),
    val prompt: String = ""
)

data class TwentyQuestionsSession(
    val secretObject: String,
    val maxQuestions: Int = 20
)

data class TwentyQuestionsAnswer(
    val isGuess: Boolean = false,
    val isCorrect: Boolean = false,
    val answer: String
)

data class GameEvaluationResult(
    val isCorrect: Boolean,
    val expectedAnswer: String,
    val userAnswer: String,
    val xpEarned: Int = 0
)

data class DailyChallengeItem(
    val type: String = "trivia",
    val question: String,
    val answer: String,
    val xp: Int = 25
)

data class DailyChallengeData(
    val date: String,
    val challenge: DailyChallengeItem,
    val isCompleted: Boolean = false
)

data class DailyChallengeResult(
    val completed: Boolean,
    val isCorrect: Boolean,
    val alreadyCompletedToday: Boolean = false,
    val earnedXp: Int = 0,
    val streak: Int = 1,
    val totalXp: Int = 0,
    val achievements: List<String> = emptyList(),
    val message: String = ""
)

// ── Gamification & Progress ─────────────────────────────────────────

data class UserProgressData(
    val xp: Int = 0,
    val score: Int = 0,
    val streak: Int = 0,
    val achievements: List<String> = emptyList(),
    val dailyChallengeCompleted: Boolean = false,
    val gamesPlayed: Int = 0,
    val correctAnswers: Int = 0
)

data class AchievementItem(
    val id: String,
    val title: String,
    val icon: String,
    val desc: String,
    val isUnlocked: Boolean = false
)

val STATIC_ACHIEVEMENTS = listOf(
    AchievementItem("first_challenge", "First Challenge", "🌟", "Completed your first daily challenge"),
    AchievementItem("streak_3", "3-Day Streak", "🔥", "Engaged for 3 days in a row"),
    AchievementItem("streak_7", "7-Day Champion", "👑", "Maintained a 7-day engagement streak"),
    AchievementItem("trivia_master", "Trivia Master", "🧠", "Answered 10 trivia questions correctly"),
    AchievementItem("riddle_solver", "Riddle Solver", "🧩", "Cracked challenging riddles"),
    AchievementItem("game_champion", "Game Champion", "🏆", "Won 20 Questions or Memory Challenge")
)

// ── Preferences ─────────────────────────────────────────────────────

data class EntertainmentPreferences(
    val radioTopics: List<String> = listOf("facts", "technology", "science", "motivation", "humor"),
    val favoriteGenres: List<String> = listOf("adventure", "mystery"),
    val preferredLanguage: String = "en"
)
