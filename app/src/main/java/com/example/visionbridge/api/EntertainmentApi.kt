package com.example.visionbridge.api

import android.content.Context
import android.net.Uri
import com.example.visionbridge.data.*
import org.json.JSONArray
import org.json.JSONObject

class EntertainmentApi(context: Context) {

    private val apiClient = ApiClient.getInstance(context)

    // ── 1. AI Radio & Live Radio ─────────────────────────────────────

    fun getLocalRadioStations(
        latitude: Double? = null,
        longitude: Double? = null,
        city: String = "",
        state: String = "",
        country: String = "",
        countryCode: String = "IN",
        language: String = "",
        search: String = "",
        limit: Int = 10
    ): ApiResult<Pair<RadioLocation, List<RadioStation>>> {
        val uriBuilder = Uri.parse("/api/entertainment/radio/stations/local").buildUpon()
        if (latitude != null && longitude != null) {
            uriBuilder.appendQueryParameter("latitude", latitude.toString())
            uriBuilder.appendQueryParameter("longitude", longitude.toString())
        }
        if (city.isNotBlank()) uriBuilder.appendQueryParameter("city", city)
        if (state.isNotBlank()) uriBuilder.appendQueryParameter("state", state)
        if (country.isNotBlank()) uriBuilder.appendQueryParameter("country", country)
        if (countryCode.isNotBlank()) uriBuilder.appendQueryParameter("countryCode", countryCode)
        if (language.isNotBlank() && language != "local" && language != "all") {
            uriBuilder.appendQueryParameter("language", language)
        }
        if (search.isNotBlank()) uriBuilder.appendQueryParameter("search", search)
        uriBuilder.appendQueryParameter("limit", limit.toString())

        val path = uriBuilder.build().toString()
        return when (val res = apiClient.get(path, authRequired = false)) {
            is ApiResult.Failure -> res
            is ApiResult.Success -> {
                try {
                    val data = res.value.getJSONObject("data")
                    val locJson = data.optJSONObject("location")
                    val location = RadioLocation(
                        city = locJson?.optString("city", "") ?: "",
                        state = locJson?.optString("state", "") ?: "",
                        country = locJson?.optString("country", "") ?: "",
                        countryCode = locJson?.optString("countryCode", "IN") ?: "IN"
                    )
                    val stationsArray = data.optJSONArray("stations") ?: JSONArray()
                    val stations = parseRadioStations(stationsArray)
                    ApiResult.Success(Pair(location, stations))
                } catch (e: Exception) {
                    ApiResult.Failure(ApiError(ApiError.Kind.MALFORMED_RESPONSE, "Failed to parse radio stations", e.message.orEmpty()))
                }
            }
        }
    }

    fun searchRadioStations(
        query: String,
        language: String = "",
        limit: Int = 10
    ): ApiResult<List<RadioStation>> {
        val uriBuilder = Uri.parse("/api/entertainment/radio/stations/search").buildUpon()
        uriBuilder.appendQueryParameter("q", query)
        if (language.isNotBlank() && language != "all") {
            uriBuilder.appendQueryParameter("language", language)
        }
        uriBuilder.appendQueryParameter("limit", limit.toString())

        val path = uriBuilder.build().toString()
        return when (val res = apiClient.get(path, authRequired = false)) {
            is ApiResult.Failure -> res
            is ApiResult.Success -> {
                try {
                    val data = res.value.getJSONObject("data")
                    val stationsArray = data.optJSONArray("stations") ?: JSONArray()
                    val stations = parseRadioStations(stationsArray)
                    ApiResult.Success(stations)
                } catch (e: Exception) {
                    ApiResult.Failure(ApiError(ApiError.Kind.MALFORMED_RESPONSE, "Failed to parse radio stations", e.message.orEmpty()))
                }
            }
        }
    }

    fun getRadioSegment(
        topic: String = "facts",
        previousSummary: String = "",
        language: String = "en"
    ): ApiResult<RadioSegment> {
        val body = JSONObject()
            .put("topic", topic)
            .put("previousSummary", previousSummary)
            .put("language", language)

        return when (val res = apiClient.post("/api/entertainment/radio/segment", body, authRequired = false)) {
            is ApiResult.Failure -> res
            is ApiResult.Success -> {
                try {
                    val data = res.value.getJSONObject("data")
                    val segment = RadioSegment(
                        title = data.optString("title", "AI Radio Segment"),
                        topic = data.optString("topic", topic),
                        content = data.optString("content", ""),
                        speech = data.optString("speech", data.optString("content", ""))
                    )
                    ApiResult.Success(segment)
                } catch (e: Exception) {
                    ApiResult.Failure(ApiError(ApiError.Kind.MALFORMED_RESPONSE, "Failed to parse radio segment", e.message.orEmpty()))
                }
            }
        }
    }

    // ── 2. Stories & Audiobooks ──────────────────────────────────────

    fun getStoriesCatalog(
        genre: String = "all",
        search: String = "",
        language: String = "all"
    ): ApiResult<List<StorySummary>> {
        val uriBuilder = Uri.parse("/api/entertainment/stories/catalog").buildUpon()
        if (genre.isNotBlank() && genre != "all") uriBuilder.appendQueryParameter("genre", genre)
        if (search.isNotBlank()) uriBuilder.appendQueryParameter("search", search)
        if (language.isNotBlank() && language != "all") uriBuilder.appendQueryParameter("language", language)

        val path = uriBuilder.build().toString()
        return when (val res = apiClient.get(path, authRequired = false)) {
            is ApiResult.Failure -> res
            is ApiResult.Success -> {
                try {
                    val data = res.value.getJSONObject("data")
                    val storiesArray = data.optJSONArray("stories") ?: JSONArray()
                    val list = mutableListOf<StorySummary>()
                    for (i in 0 until storiesArray.length()) {
                        val item = storiesArray.getJSONObject(i)
                        list.add(parseStorySummary(item))
                    }
                    ApiResult.Success(list)
                } catch (e: Exception) {
                    ApiResult.Failure(ApiError(ApiError.Kind.MALFORMED_RESPONSE, "Failed to parse stories catalog", e.message.orEmpty()))
                }
            }
        }
    }

    fun getStoryById(storyId: String): ApiResult<StoryDetail> {
        val path = "/api/entertainment/stories/${Uri.encode(storyId)}"
        return when (val res = apiClient.get(path, authRequired = false)) {
            is ApiResult.Failure -> res
            is ApiResult.Success -> {
                try {
                    val data = res.value.getJSONObject("data")
                    val chaptersArray = data.optJSONArray("chapters") ?: JSONArray()
                    val chapters = mutableListOf<StoryChapter>()
                    for (i in 0 until chaptersArray.length()) {
                        val ch = chaptersArray.getJSONObject(i)
                        chapters.add(
                            StoryChapter(
                                chapterNumber = ch.optInt("chapterNumber", i + 1),
                                title = ch.optString("title", "Chapter ${i + 1}"),
                                audioUrl = ch.optString("audioUrl", ""),
                                duration = ch.optString("duration", ""),
                                summary = ch.optString("summary", ""),
                                text = ch.optString("text", "")
                            )
                        )
                    }

                    val summary = parseStorySummary(data)
                    val detail = StoryDetail(
                        id = summary.id,
                        title = summary.title,
                        author = summary.author,
                        publicationYear = summary.publicationYear,
                        genre = summary.genre,
                        language = summary.language,
                        audioLanguageNotice = summary.audioLanguageNotice,
                        source = summary.source,
                        sourceUrl = summary.sourceUrl,
                        librivoxUrl = summary.librivoxUrl,
                        rightsStatus = summary.rightsStatus,
                        audioAvailable = summary.audioAvailable,
                        textAvailable = summary.textAvailable,
                        approxDuration = summary.approxDuration,
                        totalChapters = summary.totalChapters,
                        description = summary.description,
                        coverTheme = summary.coverTheme,
                        chapters = chapters
                    )
                    ApiResult.Success(detail)
                } catch (e: Exception) {
                    ApiResult.Failure(ApiError(ApiError.Kind.MALFORMED_RESPONSE, "Failed to parse story detail", e.message.orEmpty()))
                }
            }
        }
    }

    fun getStoryProgress(storyId: String? = null): ApiResult<StoryProgressData?> {
        val uriBuilder = Uri.parse("/api/entertainment/story/progress").buildUpon()
        if (!storyId.isNullOrBlank()) {
            uriBuilder.appendQueryParameter("storyId", storyId)
        }
        val path = uriBuilder.build().toString()
        return when (val res = apiClient.get(path, authRequired = true)) {
            is ApiResult.Failure -> res
            is ApiResult.Success -> {
                try {
                    val data = res.value.optJSONObject("data")
                    if (data == null) {
                        ApiResult.Success(null)
                    } else {
                        val progress = StoryProgressData(
                            id = if (data.has("_id")) data.getString("_id") else null,
                            userId = if (data.has("userId")) data.getString("userId") else null,
                            storyId = data.optString("storyId", ""),
                            chapterIndex = data.optInt("chapterIndex", 0),
                            positionSeconds = data.optInt("positionSeconds", 0),
                            storyTitle = data.optString("storyTitle", ""),
                            chapterTitle = data.optString("chapterTitle", ""),
                            author = data.optString("author", ""),
                            genre = data.optString("genre", "classics"),
                            lastPlayedAt = if (data.has("lastPlayedAt")) data.getString("lastPlayedAt") else null,
                            updatedAt = if (data.has("updatedAt")) data.getString("updatedAt") else null
                        )
                        ApiResult.Success(progress)
                    }
                } catch (e: Exception) {
                    ApiResult.Failure(ApiError(ApiError.Kind.MALFORMED_RESPONSE, "Failed to parse story progress", e.message.orEmpty()))
                }
            }
        }
    }

    fun saveStoryProgress(progress: StoryProgressData): ApiResult<Boolean> {
        val body = JSONObject()
            .put("storyId", progress.storyId)
            .put("chapterIndex", progress.chapterIndex)
            .put("positionSeconds", progress.positionSeconds)
            .put("storyTitle", progress.storyTitle)
            .put("chapterTitle", progress.chapterTitle)
            .put("author", progress.author)
            .put("genre", progress.genre)

        return when (val res = apiClient.post("/api/entertainment/story/progress", body, authRequired = true)) {
            is ApiResult.Failure -> res
            is ApiResult.Success -> ApiResult.Success(true)
        }
    }

    // ── 3. Audio Games ────────────────────────────────────────────────

    fun getTriviaQuestion(category: String = "", language: String = "en"): ApiResult<TriviaQuestion> {
        val body = JSONObject()
            .put("category", category)
            .put("language", language)

        return when (val res = apiClient.post("/api/entertainment/games/trivia", body, authRequired = false)) {
            is ApiResult.Failure -> res
            is ApiResult.Success -> {
                try {
                    val data = res.value.getJSONObject("data")
                    val optsArray = data.optJSONArray("options") ?: JSONArray()
                    val options = mutableListOf<String>()
                    for (i in 0 until optsArray.length()) options.add(optsArray.getString(i))

                    val question = TriviaQuestion(
                        category = data.optString("category", "General"),
                        question = data.getString("question"),
                        answer = data.getString("answer"),
                        options = options
                    )
                    ApiResult.Success(question)
                } catch (e: Exception) {
                    ApiResult.Failure(ApiError(ApiError.Kind.MALFORMED_RESPONSE, "Failed to parse trivia question", e.message.orEmpty()))
                }
            }
        }
    }

    fun getRiddle(language: String = "en"): ApiResult<RiddleItem> {
        val body = JSONObject().put("language", language)
        return when (val res = apiClient.post("/api/entertainment/games/riddle", body, authRequired = false)) {
            is ApiResult.Failure -> res
            is ApiResult.Success -> {
                try {
                    val data = res.value.getJSONObject("data")
                    val riddle = RiddleItem(
                        riddle = data.getString("riddle"),
                        answer = data.getString("answer"),
                        clue = data.optString("clue", "")
                    )
                    ApiResult.Success(riddle)
                } catch (e: Exception) {
                    ApiResult.Failure(ApiError(ApiError.Kind.MALFORMED_RESPONSE, "Failed to parse riddle", e.message.orEmpty()))
                }
            }
        }
    }

    fun getMemoryChallenge(level: Int = 1, language: String = "en"): ApiResult<MemoryChallengeData> {
        val body = JSONObject().put("level", level).put("language", language)
        return when (val res = apiClient.post("/api/entertainment/games/memory", body, authRequired = false)) {
            is ApiResult.Failure -> res
            is ApiResult.Success -> {
                try {
                    val data = res.value.getJSONObject("data")
                    val wordsArray = data.optJSONArray("words") ?: JSONArray()
                    val words = mutableListOf<String>()
                    for (i in 0 until wordsArray.length()) words.add(wordsArray.getString(i))

                    val challenge = MemoryChallengeData(
                        level = data.optInt("level", level),
                        words = words,
                        prompt = data.optString("prompt", words.joinToString(", "))
                    )
                    ApiResult.Success(challenge)
                } catch (e: Exception) {
                    ApiResult.Failure(ApiError(ApiError.Kind.MALFORMED_RESPONSE, "Failed to parse memory challenge", e.message.orEmpty()))
                }
            }
        }
    }

    fun startTwentyQuestions(language: String = "en"): ApiResult<TwentyQuestionsSession> {
        val body = JSONObject().put("language", language)
        return when (val res = apiClient.post("/api/entertainment/games/twenty-questions", body, authRequired = false)) {
            is ApiResult.Failure -> res
            is ApiResult.Success -> {
                try {
                    val data = res.value.getJSONObject("data")
                    val session = TwentyQuestionsSession(
                        secretObject = data.getString("secretObject"),
                        maxQuestions = data.optInt("maxQuestions", 20)
                    )
                    ApiResult.Success(session)
                } catch (e: Exception) {
                    ApiResult.Failure(ApiError(ApiError.Kind.MALFORMED_RESPONSE, "Failed to parse 20 Questions session", e.message.orEmpty()))
                }
            }
        }
    }

    fun askTwentyQuestions(secretObject: String, question: String, language: String = "en"): ApiResult<TwentyQuestionsAnswer> {
        val body = JSONObject()
            .put("secretObject", secretObject)
            .put("question", question)
            .put("language", language)

        return when (val res = apiClient.post("/api/entertainment/games/twenty-questions/ask", body, authRequired = false)) {
            is ApiResult.Failure -> res
            is ApiResult.Success -> {
                try {
                    val data = res.value.getJSONObject("data")
                    val answer = TwentyQuestionsAnswer(
                        isGuess = data.optBoolean("isGuess", false),
                        isCorrect = data.optBoolean("isCorrect", false),
                        answer = data.optString("answer", "")
                    )
                    ApiResult.Success(answer)
                } catch (e: Exception) {
                    ApiResult.Failure(ApiError(ApiError.Kind.MALFORMED_RESPONSE, "Failed to parse 20 questions answer", e.message.orEmpty()))
                }
            }
        }
    }

    fun evaluateAnswer(expectedAnswer: String, userAnswer: String, gameType: String = "trivia", language: String = "en"): ApiResult<GameEvaluationResult> {
        val body = JSONObject()
            .put("expectedAnswer", expectedAnswer)
            .put("userAnswer", userAnswer)
            .put("gameType", gameType)
            .put("language", language)

        return when (val res = apiClient.post("/api/entertainment/games/evaluate", body, authRequired = false)) {
            is ApiResult.Failure -> res
            is ApiResult.Success -> {
                try {
                    val data = res.value.getJSONObject("data")
                    val eval = GameEvaluationResult(
                        isCorrect = data.optBoolean("isCorrect", false),
                        expectedAnswer = data.optString("expectedAnswer", expectedAnswer),
                        userAnswer = data.optString("userAnswer", userAnswer),
                        xpEarned = data.optInt("xpEarned", 0)
                    )
                    ApiResult.Success(eval)
                } catch (e: Exception) {
                    ApiResult.Failure(ApiError(ApiError.Kind.MALFORMED_RESPONSE, "Failed to evaluate answer", e.message.orEmpty()))
                }
            }
        }
    }

    // ── 4. Daily Challenge & Progress ─────────────────────────────────

    fun getDailyChallenge(language: String = "en"): ApiResult<DailyChallengeData> {
        val path = "/api/entertainment/daily-challenge?language=${Uri.encode(language)}"
        return when (val res = apiClient.get(path, authRequired = true)) {
            is ApiResult.Failure -> res
            is ApiResult.Success -> {
                try {
                    val data = res.value.getJSONObject("data")
                    val chJson = data.getJSONObject("challenge")
                    val challenge = DailyChallengeItem(
                        type = chJson.optString("type", "trivia"),
                        question = chJson.getString("question"),
                        answer = chJson.getString("answer"),
                        xp = chJson.optInt("xp", 25)
                    )
                    val result = DailyChallengeData(
                        date = data.optString("date", ""),
                        challenge = challenge,
                        isCompleted = data.optBoolean("isCompleted", false)
                    )
                    ApiResult.Success(result)
                } catch (e: Exception) {
                    ApiResult.Failure(ApiError(ApiError.Kind.MALFORMED_RESPONSE, "Failed to parse daily challenge", e.message.orEmpty()))
                }
            }
        }
    }

    fun completeDailyChallenge(userAnswer: String, expectedAnswer: String, language: String = "en"): ApiResult<DailyChallengeResult> {
        val body = JSONObject()
            .put("userAnswer", userAnswer)
            .put("expectedAnswer", expectedAnswer)
            .put("language", language)

        return when (val res = apiClient.post("/api/entertainment/daily-challenge/complete", body, authRequired = true)) {
            is ApiResult.Failure -> res
            is ApiResult.Success -> {
                try {
                    val data = res.value.getJSONObject("data")
                    val achArray = data.optJSONArray("achievements") ?: JSONArray()
                    val achievements = mutableListOf<String>()
                    for (i in 0 until achArray.length()) achievements.add(achArray.getString(i))

                    val result = DailyChallengeResult(
                        completed = data.optBoolean("completed", false),
                        isCorrect = data.optBoolean("isCorrect", false),
                        alreadyCompletedToday = data.optBoolean("alreadyCompletedToday", false),
                        earnedXp = data.optInt("earnedXp", 0),
                        streak = data.optInt("streak", 1),
                        totalXp = data.optInt("totalXp", 0),
                        achievements = achievements,
                        message = data.optString("message", "")
                    )
                    ApiResult.Success(result)
                } catch (e: Exception) {
                    ApiResult.Failure(ApiError(ApiError.Kind.MALFORMED_RESPONSE, "Failed to parse challenge completion", e.message.orEmpty()))
                }
            }
        }
    }

    fun getProgress(): ApiResult<UserProgressData> {
        return when (val res = apiClient.get("/api/entertainment/progress", authRequired = true)) {
            is ApiResult.Failure -> res
            is ApiResult.Success -> {
                try {
                    val data = res.value.getJSONObject("data")
                    val achArray = data.optJSONArray("achievements") ?: JSONArray()
                    val achievements = mutableListOf<String>()
                    for (i in 0 until achArray.length()) achievements.add(achArray.getString(i))

                    val progress = UserProgressData(
                        xp = data.optInt("xp", 0),
                        score = data.optInt("score", 0),
                        streak = data.optInt("streak", 0),
                        achievements = achievements,
                        dailyChallengeCompleted = data.optBoolean("dailyChallengeCompleted", false),
                        gamesPlayed = data.optInt("gamesPlayed", 0),
                        correctAnswers = data.optInt("correctAnswers", 0)
                    )
                    ApiResult.Success(progress)
                } catch (e: Exception) {
                    ApiResult.Failure(ApiError(ApiError.Kind.MALFORMED_RESPONSE, "Failed to parse user progress", e.message.orEmpty()))
                }
            }
        }
    }

    fun addXp(amount: Int = 10, achievement: String = ""): ApiResult<UserProgressData> {
        val body = JSONObject().put("amount", amount)
        if (achievement.isNotBlank()) body.put("achievement", achievement)

        return when (val res = apiClient.post("/api/entertainment/progress/add-xp", body, authRequired = true)) {
            is ApiResult.Failure -> res
            is ApiResult.Success -> {
                try {
                    val data = res.value.getJSONObject("data")
                    val achArray = data.optJSONArray("achievements") ?: JSONArray()
                    val achievements = mutableListOf<String>()
                    for (i in 0 until achArray.length()) achievements.add(achArray.getString(i))

                    val progress = UserProgressData(
                        xp = data.optInt("xp", 0),
                        score = data.optInt("score", 0),
                        achievements = achievements
                    )
                    ApiResult.Success(progress)
                } catch (e: Exception) {
                    ApiResult.Failure(ApiError(ApiError.Kind.MALFORMED_RESPONSE, "Failed to parse add-xp response", e.message.orEmpty()))
                }
            }
        }
    }

    // ── 5. Preferences ────────────────────────────────────────────────

    fun getPreferences(): ApiResult<EntertainmentPreferences> {
        return when (val res = apiClient.get("/api/entertainment/preferences", authRequired = true)) {
            is ApiResult.Failure -> res
            is ApiResult.Success -> {
                try {
                    val data = res.value.getJSONObject("data")
                    val topicsArray = data.optJSONArray("radioTopics") ?: JSONArray()
                    val topics = mutableListOf<String>()
                    for (i in 0 until topicsArray.length()) topics.add(topicsArray.getString(i))

                    val genresArray = data.optJSONArray("favoriteGenres") ?: JSONArray()
                    val genres = mutableListOf<String>()
                    for (i in 0 until genresArray.length()) genres.add(genresArray.getString(i))

                    val prefs = EntertainmentPreferences(
                        radioTopics = if (topics.isNotEmpty()) topics else listOf("facts", "technology", "science", "motivation", "humor"),
                        favoriteGenres = if (genres.isNotEmpty()) genres else listOf("adventure", "mystery"),
                        preferredLanguage = data.optString("preferredLanguage", "en")
                    )
                    ApiResult.Success(prefs)
                } catch (e: Exception) {
                    ApiResult.Failure(ApiError(ApiError.Kind.MALFORMED_RESPONSE, "Failed to parse preferences", e.message.orEmpty()))
                }
            }
        }
    }

    fun updatePreferences(prefs: EntertainmentPreferences): ApiResult<EntertainmentPreferences> {
        val body = JSONObject()
            .put("radioTopics", JSONArray(prefs.radioTopics))
            .put("favoriteGenres", JSONArray(prefs.favoriteGenres))
            .put("preferredLanguage", prefs.preferredLanguage)

        return when (val res = apiClient.put("/api/entertainment/preferences", body, authRequired = true)) {
            is ApiResult.Failure -> res
            is ApiResult.Success -> ApiResult.Success(prefs)
        }
    }

    // ── JSON Helpers ──────────────────────────────────────────────────

    private fun parseRadioStations(array: JSONArray): List<RadioStation> {
        val list = mutableListOf<RadioStation>()
        for (i in 0 until array.length()) {
            val s = array.getJSONObject(i)
            val tagsArray = s.optJSONArray("tags") ?: JSONArray()
            val tags = mutableListOf<String>()
            for (j in 0 until tagsArray.length()) tags.add(tagsArray.getString(j))

            list.add(
                RadioStation(
                    id = s.optString("id", s.optString("stationuuid", "")),
                    name = s.optString("name", "Local Radio"),
                    streamUrl = s.optString("streamUrl", s.optString("url_resolved", s.optString("url", ""))),
                    city = s.optString("city", ""),
                    state = s.optString("state", ""),
                    country = s.optString("country", ""),
                    countryCode = s.optString("countryCode", "IN"),
                    language = s.optString("language", ""),
                    tags = tags,
                    codec = s.optString("codec", "MP3"),
                    bitrate = s.optInt("bitrate", 128),
                    isHttps = s.optBoolean("isHttps", true),
                    votes = s.optInt("votes", 0),
                    clickcount = s.optInt("clickcount", 0),
                    favicon = s.optString("favicon", ""),
                    homepage = s.optString("homepage", "")
                )
            )
        }
        return list
    }

    private fun parseStorySummary(json: JSONObject): StorySummary {
        val coverJson = json.optJSONObject("coverTheme")
        val coverTheme = if (coverJson != null) {
            StoryCoverTheme(
                primaryColor = coverJson.optString("primaryColor", "#00D4FF"),
                secondaryColor = coverJson.optString("secondaryColor", "#0A2540"),
                icon = coverJson.optString("icon", "📖"),
                accent = coverJson.optString("accent", "blue")
            )
        } else {
            StoryCoverTheme()
        }

        return StorySummary(
            id = json.getString("id"),
            title = json.getString("title"),
            author = json.optString("author", "Unknown"),
            publicationYear = json.optInt("publicationYear", 1900),
            genre = json.optString("genre", "classics"),
            language = json.optString("language", "en"),
            audioLanguageNotice = json.optString("audioLanguageNotice", ""),
            source = json.optString("source", ""),
            sourceUrl = json.optString("sourceUrl", ""),
            librivoxUrl = json.optString("librivoxUrl", ""),
            rightsStatus = json.optString("rightsStatus", "Public Domain"),
            audioAvailable = json.optBoolean("audioAvailable", false),
            textAvailable = json.optBoolean("textAvailable", true),
            approxDuration = json.optString("approxDuration", ""),
            totalChapters = json.optInt("totalChapters", json.optInt("chapterCount", 1)),
            chapterCount = json.optInt("chapterCount", json.optInt("totalChapters", 1)),
            description = json.optString("description", ""),
            coverTheme = coverTheme
        )
    }
}
