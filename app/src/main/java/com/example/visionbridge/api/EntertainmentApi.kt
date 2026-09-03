package com.example.visionbridge.api

import android.content.Context
import android.net.Uri
import com.example.visionbridge.data.*
import com.example.visionbridge.supabase.SupabaseClient
import com.example.visionbridge.supabase.SupabaseConfig
import org.json.JSONArray
import org.json.JSONObject

class EntertainmentApi(private val context: Context) {

    private val supabaseClient = SupabaseClient.getInstance(context)

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
        val uriBuilder = Uri.parse("").buildUpon()
        if (latitude != null && longitude != null) {
            uriBuilder.appendQueryParameter("latitude", latitude.toString())
            uriBuilder.appendQueryParameter("longitude", longitude.toString())
        }
        if (city.isNotBlank()) uriBuilder.appendQueryParameter("city", city)
        if (state.isNotBlank()) uriBuilder.appendQueryParameter("state", state)
        if (countryCode.isNotBlank()) uriBuilder.appendQueryParameter("countryCode", countryCode)
        if (language.isNotBlank() && language != "local" && language != "all") {
            uriBuilder.appendQueryParameter("language", language)
        }
        if (search.isNotBlank()) uriBuilder.appendQueryParameter("q", search)
        uriBuilder.appendQueryParameter("limit", limit.toString())

        val queryString = uriBuilder.build().toString().trimStart('?')
        val funcPath = if (queryString.isNotBlank()) "${SupabaseConfig.FUNCTION_RADIO_STATIONS}?$queryString" else SupabaseConfig.FUNCTION_RADIO_STATIONS

        val body = JSONObject()
            .put("city", city)
            .put("state", state)
            .put("countryCode", countryCode)
            .put("language", language)
            .put("search", search)
            .put("limit", limit)

        return when (val res = supabaseClient.callFunction(funcPath, body, authRequired = false)) {
            is ApiResult.Failure -> res
            is ApiResult.Success -> {
                try {
                    val data = res.value.optJSONObject("data") ?: res.value
                    val locJson = data.optJSONObject("location")
                    val location = RadioLocation(
                        city = locJson?.optString("city", city) ?: city,
                        state = locJson?.optString("state", state) ?: state,
                        country = locJson?.optString("country", country) ?: country,
                        countryCode = locJson?.optString("countryCode", countryCode) ?: countryCode
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
        val body = JSONObject()
            .put("q", query)
            .put("language", language)
            .put("limit", limit)

        val funcPath = "${SupabaseConfig.FUNCTION_RADIO_STATIONS}?q=${Uri.encode(query)}&limit=$limit"

        return when (val res = supabaseClient.callFunction(funcPath, body, authRequired = false)) {
            is ApiResult.Failure -> res
            is ApiResult.Success -> {
                try {
                    val data = res.value.optJSONObject("data") ?: res.value
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

        return when (val res = supabaseClient.callFunction(SupabaseConfig.FUNCTION_RADIO_SEGMENT, body, authRequired = false)) {
            is ApiResult.Failure -> res
            is ApiResult.Success -> {
                try {
                    val data = res.value.optJSONObject("data") ?: res.value
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
        var list = BUILTIN_PUBLIC_DOMAIN_STORIES.map { it.toSummary() }

        if (language.isNotBlank() && language != "all") {
            list = list.filter { it.language.equals(language, ignoreCase = true) }
        }

        if (genre.isNotBlank() && genre != "all") {
            list = list.filter { it.genre.equals(genre, ignoreCase = true) }
        }

        if (search.isNotBlank()) {
            val q = search.lowercase()
            list = list.filter {
                it.title.lowercase().contains(q) ||
                it.author.lowercase().contains(q) ||
                it.description.lowercase().contains(q) ||
                it.genre.lowercase().contains(q)
            }
        }

        return ApiResult.Success(list)
    }

    fun getStoryById(storyId: String): ApiResult<StoryDetail> {
        val story = BUILTIN_PUBLIC_DOMAIN_STORIES.find { it.id.equals(storyId, ignoreCase = true) }
        return if (story != null) {
            ApiResult.Success(story)
        } else {
            ApiResult.Failure(ApiError(ApiError.Kind.HTTP_ERROR, "Story not found", "No story with id $storyId"))
        }
    }

    fun getStoryProgress(storyId: String? = null): ApiResult<StoryProgressData?> {
        val query = if (!storyId.isNullOrBlank()) {
            "story_progress?story_id=eq.$storyId&select=*&limit=1"
        } else {
            "story_progress?select=*&order=updated_at.desc&limit=1"
        }

        return when (val res = supabaseClient.restGet(query, authRequired = true)) {
            is ApiResult.Failure -> res
            is ApiResult.Success -> {
                try {
                    val arr = JSONArray(res.value)
                    if (arr.length() == 0) {
                        ApiResult.Success(null)
                    } else {
                        val data = arr.getJSONObject(0)
                        val progress = StoryProgressData(
                            id = if (data.has("id") && !data.isNull("id")) data.optString("id") else null,
                            userId = if (data.has("user_id") && !data.isNull("user_id")) data.optString("user_id") else null,
                            storyId = data.optString("story_id", ""),
                            chapterIndex = data.optInt("chapter_index", 0),
                            positionSeconds = data.optInt("position_seconds", 0),
                            storyTitle = data.optString("story_title", ""),
                            chapterTitle = data.optString("chapter_title", ""),
                            author = data.optString("author", ""),
                            genre = data.optString("genre", "classics"),
                            lastPlayedAt = if (data.has("last_played_at") && !data.isNull("last_played_at")) data.optString("last_played_at") else null,
                            updatedAt = if (data.has("updated_at") && !data.isNull("updated_at")) data.optString("updated_at") else null
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
            .put("story_id", progress.storyId)
            .put("chapter_index", progress.chapterIndex)
            .put("position_seconds", progress.positionSeconds)
            .put("story_title", progress.storyTitle)
            .put("chapter_title", progress.chapterTitle)
            .put("author", progress.author)
            .put("genre", progress.genre)
            .put("last_played_at", java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }.format(java.util.Date()))

        // Use PostgREST upsert with on_conflict
        val path = "story_progress?on_conflict=user_id,story_id"
        return when (val res = supabaseClient.restPost(path, body, preferReturn = false, authRequired = true)) {
            is ApiResult.Failure -> res
            is ApiResult.Success -> ApiResult.Success(true)
        }
    }

    // ── 3. Audio Games ────────────────────────────────────────────────

    fun getTriviaQuestion(category: String = "", language: String = "en"): ApiResult<TriviaQuestion> {
        val body = JSONObject()
            .put("category", category)
            .put("language", language)

        return when (val res = supabaseClient.callFunction(SupabaseConfig.FUNCTION_GAMES_TRIVIA, body, authRequired = false)) {
            is ApiResult.Failure -> res
            is ApiResult.Success -> {
                try {
                    val data = res.value.optJSONObject("data") ?: res.value
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
        return when (val res = supabaseClient.callFunction(SupabaseConfig.FUNCTION_GAMES_RIDDLE, body, authRequired = false)) {
            is ApiResult.Failure -> res
            is ApiResult.Success -> {
                try {
                    val data = res.value.optJSONObject("data") ?: res.value
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
        return when (val res = supabaseClient.callFunction(SupabaseConfig.FUNCTION_GAMES_MEMORY, body, authRequired = false)) {
            is ApiResult.Failure -> res
            is ApiResult.Success -> {
                try {
                    val data = res.value.optJSONObject("data") ?: res.value
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
        return when (val res = supabaseClient.callFunction(SupabaseConfig.FUNCTION_GAMES_TWENTY_QUESTIONS, body, authRequired = false)) {
            is ApiResult.Failure -> res
            is ApiResult.Success -> {
                try {
                    val data = res.value.optJSONObject("data") ?: res.value
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

        return when (val res = supabaseClient.callFunction(SupabaseConfig.FUNCTION_GAMES_TWENTY_QUESTIONS, body, authRequired = false)) {
            is ApiResult.Failure -> res
            is ApiResult.Success -> {
                try {
                    val data = res.value.optJSONObject("data") ?: res.value
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

        return when (val res = supabaseClient.callFunction(SupabaseConfig.FUNCTION_GAMES_EVALUATE, body, authRequired = false)) {
            is ApiResult.Failure -> res
            is ApiResult.Success -> {
                try {
                    val data = res.value.optJSONObject("data") ?: res.value
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
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        val challenges = listOf(
            DailyChallengeItem("trivia", "What is the closest star to planet Earth?", "The Sun", 25),
            DailyChallengeItem("riddle", "The more you take, the more you leave behind. What are they?", "Footsteps", 25),
            DailyChallengeItem("trivia", "What is the chemical symbol for water?", "H2O", 25),
            DailyChallengeItem("riddle", "What has to be broken before you can use it?", "An egg", 25),
            DailyChallengeItem("trivia", "What is the hardest natural substance on Earth?", "Diamond", 25)
        )
        val dayOfMonth = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_MONTH)
        val challenge = challenges[dayOfMonth % challenges.size]

        var isCompleted = false
        val progressRes = getProgress()
        if (progressRes is ApiResult.Success) {
            isCompleted = progressRes.value.dailyChallengeCompleted
        }

        return ApiResult.Success(DailyChallengeData(today, challenge, isCompleted))
    }

    fun completeDailyChallenge(userAnswer: String, expectedAnswer: String, language: String = "en"): ApiResult<DailyChallengeResult> {
        val normExp = expectedAnswer.trim().lowercase()
        val normUser = userAnswer.trim().lowercase()
        val isCorrect = normUser.contains(normExp) || normExp.contains(normUser)

        if (!isCorrect) {
            return ApiResult.Success(
                DailyChallengeResult(
                    completed = false,
                    isCorrect = false,
                    message = "Incorrect answer. Try again!"
                )
            )
        }

        val addXpRes = addXp(25, "first_challenge")
        val totalXp = if (addXpRes is ApiResult.Success) addXpRes.value.xp else 25

        return ApiResult.Success(
            DailyChallengeResult(
                completed = true,
                isCorrect = true,
                earnedXp = 25,
                streak = 1,
                totalXp = totalXp,
                achievements = listOf("first_challenge"),
                message = "Congratulations! Daily challenge completed."
            )
        )
    }

    fun getProgress(): ApiResult<UserProgressData> {
        return when (val res = supabaseClient.restGet("game_progress?select=*&limit=1", authRequired = true)) {
            is ApiResult.Failure -> res
            is ApiResult.Success -> {
                try {
                    val arr = JSONArray(res.value)
                    if (arr.length() == 0) {
                        ApiResult.Success(UserProgressData())
                    } else {
                        val data = arr.getJSONObject(0)
                        val achArray = data.optJSONArray("achievements") ?: JSONArray()
                        val achievements = mutableListOf<String>()
                        for (i in 0 until achArray.length()) achievements.add(achArray.getString(i))

                        val progress = UserProgressData(
                            xp = data.optInt("xp", 0),
                            score = data.optInt("score", 0),
                            streak = data.optInt("streak", 0),
                            achievements = achievements,
                            dailyChallengeCompleted = data.optBoolean("daily_challenge_completed", false),
                            gamesPlayed = data.optInt("games_played", 0),
                            correctAnswers = data.optInt("correct_answers", 0)
                        )
                        ApiResult.Success(progress)
                    }
                } catch (e: Exception) {
                    ApiResult.Failure(ApiError(ApiError.Kind.MALFORMED_RESPONSE, "Failed to parse user progress", e.message.orEmpty()))
                }
            }
        }
    }

    fun addXp(amount: Int = 10, achievement: String = ""): ApiResult<UserProgressData> {
        val params = JSONObject()
            .put("p_amount", amount)
            .put("p_achievement", achievement.takeIf { it.isNotBlank() })

        return when (val res = supabaseClient.rpc("add_user_xp", params)) {
            is ApiResult.Failure -> res
            is ApiResult.Success -> {
                try {
                    val data = JSONObject(res.value)
                    val achArray = data.optJSONArray("achievements") ?: JSONArray()
                    val achievements = mutableListOf<String>()
                    for (i in 0 until achArray.length()) achievements.add(achArray.getString(i))

                    val progress = UserProgressData(
                        xp = data.optInt("xp", 0),
                        score = data.optInt("score", 0),
                        streak = data.optInt("streak", 0),
                        achievements = achievements,
                        gamesPlayed = data.optInt("games_played", 0),
                        correctAnswers = data.optInt("correct_answers", 0)
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
        return when (val res = supabaseClient.restGet("entertainment_preferences?select=*&limit=1", authRequired = true)) {
            is ApiResult.Failure -> res
            is ApiResult.Success -> {
                try {
                    val arr = JSONArray(res.value)
                    if (arr.length() == 0) {
                        ApiResult.Success(EntertainmentPreferences())
                    } else {
                        val data = arr.getJSONObject(0)
                        val topicsArray = data.optJSONArray("radio_topics") ?: JSONArray()
                        val topics = mutableListOf<String>()
                        for (i in 0 until topicsArray.length()) topics.add(topicsArray.getString(i))

                        val genresArray = data.optJSONArray("favorite_genres") ?: JSONArray()
                        val genres = mutableListOf<String>()
                        for (i in 0 until genresArray.length()) genres.add(genresArray.getString(i))

                        val prefs = EntertainmentPreferences(
                            radioTopics = if (topics.isNotEmpty()) topics else listOf("facts", "technology", "science", "motivation", "humor"),
                            favoriteGenres = if (genres.isNotEmpty()) genres else listOf("adventure", "mystery"),
                            preferredLanguage = data.optString("preferred_language", "en")
                        )
                        ApiResult.Success(prefs)
                    }
                } catch (e: Exception) {
                    ApiResult.Failure(ApiError(ApiError.Kind.MALFORMED_RESPONSE, "Failed to parse preferences", e.message.orEmpty()))
                }
            }
        }
    }

    fun updatePreferences(prefs: EntertainmentPreferences): ApiResult<EntertainmentPreferences> {
        val body = JSONObject()
            .put("radio_topics", JSONArray(prefs.radioTopics))
            .put("favorite_genres", JSONArray(prefs.favoriteGenres))
            .put("preferred_language", prefs.preferredLanguage)

        return when (val res = supabaseClient.restPost("entertainment_preferences?on_conflict=user_id", body, preferReturn = false, authRequired = true)) {
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

    companion object {
        val BUILTIN_PUBLIC_DOMAIN_STORIES = listOf(
            StoryDetail(
                id = "alice-in-wonderland",
                title = "Alice's Adventures in Wonderland",
                author = "Lewis Carroll",
                publicationYear = 1865,
                genre = "fantasy",
                language = "en",
                audioLanguageNotice = "English Audio (LibriVox Public Domain)",
                source = "LibriVox / Project Gutenberg",
                sourceUrl = "https://www.gutenberg.org/ebooks/11",
                librivoxUrl = "https://librivox.org/alices-adventures-in-wonderland-by-lewis-carroll-5/",
                rightsStatus = "Public Domain (Published 1865)",
                audioAvailable = true,
                textAvailable = true,
                approxDuration = "1 hr 35 min",
                totalChapters = 4,
                description = "A young girl named Alice falls down a rabbit hole into a subterranean fantasy realm of strange creatures.",
                coverTheme = StoryCoverTheme("#00D4FF", "#0A2540", "🐇", "alice-blue"),
                chapters = listOf(
                    StoryChapter(
                        chapterNumber = 1,
                        title = "Chapter 1: Down the Rabbit-Hole",
                        audioUrl = "https://ia800301.us.archive.org/19/items/alices_adventures_1005_librivox/alicesadventuresinwonderland_01_carroll_64kb.mp3",
                        duration = "11 min",
                        summary = "Alice follows a White Rabbit down a deep hole.",
                        text = "Alice was beginning to get very tired of sitting by her sister on the bank..."
                    ),
                    StoryChapter(
                        chapterNumber = 2,
                        title = "Chapter 2: The Pool of Tears",
                        audioUrl = "https://ia800301.us.archive.org/19/items/alices_adventures_1005_librivox/alicesadventuresinwonderland_02_carroll_64kb.mp3",
                        duration = "12 min",
                        summary = "Alice grows to nine feet tall and cries a pool of tears.",
                        text = "Curiouser and curiouser! cried Alice..."
                    )
                )
            ),
            StoryDetail(
                id = "sherlock-holmes-scandal",
                title = "A Scandal in Bohemia",
                author = "Arthur Conan Doyle",
                publicationYear = 1891,
                genre = "mystery",
                language = "en",
                audioLanguageNotice = "English Audio (LibriVox Public Domain)",
                source = "LibriVox / Project Gutenberg",
                sourceUrl = "https://www.gutenberg.org/ebooks/1661",
                librivoxUrl = "https://librivox.org/the-adventures-of-sherlock-holmes-by-sir-arthur-conan-doyle-2/",
                rightsStatus = "Public Domain (Published 1891)",
                audioAvailable = true,
                textAvailable = true,
                approxDuration = "45 min",
                totalChapters = 3,
                description = "Sherlock Holmes is hired by the King of Bohemia to recover an incriminating photograph.",
                coverTheme = StoryCoverTheme("#F59E0B", "#1E1B18", "🕵️", "gold"),
                chapters = listOf(
                    StoryChapter(
                        chapterNumber = 1,
                        title = "Part 1: The Bohemian King",
                        audioUrl = "https://ia800301.us.archive.org/29/items/adventures_holmes_0711_librivox/adventuresofsherlockholmes_01_doyle_64kb.mp3",
                        duration = "15 min",
                        summary = "Sherlock Holmes meets his royal client.",
                        text = "To Sherlock Holmes she is always the woman..."
                    )
                )
            )
        )

        private fun StoryDetail.toSummary(): StorySummary {
            return StorySummary(
                id = id,
                title = title,
                author = author,
                publicationYear = publicationYear,
                genre = genre,
                language = language,
                audioLanguageNotice = audioLanguageNotice,
                source = source,
                sourceUrl = sourceUrl,
                librivoxUrl = librivoxUrl,
                rightsStatus = rightsStatus,
                audioAvailable = audioAvailable,
                textAvailable = textAvailable,
                approxDuration = approxDuration,
                totalChapters = totalChapters,
                chapterCount = chapters.size,
                description = description,
                coverTheme = coverTheme
            )
        }
    }
}
