package com.example.visionbridge.data

import android.content.Context
import android.util.Log
import com.example.visionbridge.api.ApiResult
import com.example.visionbridge.api.EntertainmentApi
import com.example.visionbridge.utils.LocationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class EntertainmentRepository(private val context: Context) {

    private val api = EntertainmentApi(context)
    private val sessionManager = SessionManager.getInstance(context)

    // Local cached stations
    private var cachedStations: List<RadioStation> = emptyList()
    private var cachedLocation: RadioLocation? = null

    // Local cached story progress fallback
    private var lastLocalStoryProgress: StoryProgressData? = null

    suspend fun getLocalStations(language: String? = null, forceRefresh: Boolean = false): ApiResult<Pair<RadioLocation, List<RadioStation>>> {
        return withContext(Dispatchers.IO) {
            if (!forceRefresh && cachedStations.isNotEmpty() && cachedLocation != null) {
                return@withContext ApiResult.Success(Pair(cachedLocation!!, cachedStations))
            }

            val coords = try {
                LocationHelper.getCurrentLocation(context)
            } catch (e: Exception) {
                Pair(LocationHelper.DEFAULT_LATITUDE, LocationHelper.DEFAULT_LONGITUDE)
            }

            val activeLang = language ?: sessionManager.getLanguage()
            val result = api.getLocalRadioStations(
                latitude = coords.first,
                longitude = coords.second,
                countryCode = "IN",
                language = if (activeLang == "local") "" else activeLang,
                limit = 20
            )

            if (result is ApiResult.Success) {
                cachedLocation = result.value.first
                cachedStations = result.value.second
            }
            result
        }
    }

    suspend fun searchStations(query: String, language: String? = null): ApiResult<List<RadioStation>> {
        return withContext(Dispatchers.IO) {
            val activeLang = language ?: sessionManager.getLanguage()
            api.searchRadioStations(query, activeLang, limit = 20)
        }
    }

    suspend fun getStoriesCatalog(genre: String = "all", search: String = "", language: String = "all"): ApiResult<List<StorySummary>> {
        return withContext(Dispatchers.IO) {
            api.getStoriesCatalog(genre, search, language)
        }
    }

    suspend fun getStoryDetail(storyId: String): ApiResult<StoryDetail> {
        return withContext(Dispatchers.IO) {
            api.getStoryById(storyId)
        }
    }

    suspend fun getStoryProgress(storyId: String? = null): StoryProgressData? {
        return withContext(Dispatchers.IO) {
            when (val res = api.getStoryProgress(storyId)) {
                is ApiResult.Success -> {
                    val remote = res.value
                    if (remote != null) {
                        lastLocalStoryProgress = remote
                        remote
                    } else {
                        lastLocalStoryProgress
                    }
                }
                is ApiResult.Failure -> lastLocalStoryProgress
            }
        }
    }

    suspend fun saveStoryProgress(progress: StoryProgressData): Boolean {
        lastLocalStoryProgress = progress
        return withContext(Dispatchers.IO) {
            when (val res = api.saveStoryProgress(progress)) {
                is ApiResult.Success -> true
                is ApiResult.Failure -> {
                    Log.w("EntertainmentRepo", "Failed to save remote story progress: ${res.error.userMessage}")
                    false
                }
            }
        }
    }

    suspend fun getTriviaQuestion(category: String = ""): ApiResult<TriviaQuestion> {
        return withContext(Dispatchers.IO) {
            val lang = sessionManager.getLanguage()
            api.getTriviaQuestion(category, lang)
        }
    }

    suspend fun getRiddle(): ApiResult<RiddleItem> {
        return withContext(Dispatchers.IO) {
            val lang = sessionManager.getLanguage()
            api.getRiddle(lang)
        }
    }

    suspend fun getMemoryChallenge(level: Int = 1): ApiResult<MemoryChallengeData> {
        return withContext(Dispatchers.IO) {
            val lang = sessionManager.getLanguage()
            api.getMemoryChallenge(level, lang)
        }
    }

    suspend fun startTwentyQuestions(): ApiResult<TwentyQuestionsSession> {
        return withContext(Dispatchers.IO) {
            val lang = sessionManager.getLanguage()
            api.startTwentyQuestions(lang)
        }
    }

    suspend fun askTwentyQuestions(secretObject: String, question: String): ApiResult<TwentyQuestionsAnswer> {
        return withContext(Dispatchers.IO) {
            val lang = sessionManager.getLanguage()
            api.askTwentyQuestions(secretObject, question, lang)
        }
    }

    suspend fun evaluateGameAnswer(expected: String, userAns: String, gameType: String): ApiResult<GameEvaluationResult> {
        return withContext(Dispatchers.IO) {
            val lang = sessionManager.getLanguage()
            api.evaluateAnswer(expected, userAns, gameType, lang)
        }
    }

    suspend fun getDailyChallenge(): ApiResult<DailyChallengeData> {
        return withContext(Dispatchers.IO) {
            val lang = sessionManager.getLanguage()
            api.getDailyChallenge(lang)
        }
    }

    suspend fun completeDailyChallenge(userAnswer: String, expectedAnswer: String): ApiResult<DailyChallengeResult> {
        return withContext(Dispatchers.IO) {
            val lang = sessionManager.getLanguage()
            api.completeDailyChallenge(userAnswer, expectedAnswer, lang)
        }
    }

    suspend fun getUserProgress(): ApiResult<UserProgressData> {
        return withContext(Dispatchers.IO) {
            api.getProgress()
        }
    }

    suspend fun addXp(amount: Int = 10, achievement: String = ""): ApiResult<UserProgressData> {
        return withContext(Dispatchers.IO) {
            api.addXp(amount, achievement)
        }
    }

    suspend fun getPreferences(): ApiResult<EntertainmentPreferences> {
        return withContext(Dispatchers.IO) {
            api.getPreferences()
        }
    }

    suspend fun updatePreferences(prefs: EntertainmentPreferences): ApiResult<EntertainmentPreferences> {
        return withContext(Dispatchers.IO) {
            api.updatePreferences(prefs)
        }
    }

    companion object {
        @Volatile
        private var instance: EntertainmentRepository? = null

        fun getInstance(context: Context): EntertainmentRepository {
            return instance ?: synchronized(this) {
                instance ?: EntertainmentRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
