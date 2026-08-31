package com.example.visionbridge.ui.screens.entertainment

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.visionbridge.api.ApiResult
import com.example.visionbridge.audio.ActiveAudioFeature
import com.example.visionbridge.audio.EntertainmentAudioSession
import com.example.visionbridge.data.*
import com.example.visionbridge.utils.TextToSpeechManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class GamesUiState(
    val selectedMode: String = "daily", // "daily" | "trivia" | "riddles" | "twenty_questions" | "memory"
    val dailyChallenge: DailyChallengeData? = null,
    val triviaQuestion: TriviaQuestion? = null,
    val riddle: RiddleItem? = null,
    val twentyQuestions: TwentyQuestionsSession? = null,
    val twentyQHistory: List<Pair<String, String>> = emptyList(),
    val twentyQCount: Int = 0,
    val memoryChallenge: MemoryChallengeData? = null,
    val userAnswerText: String = "",
    val feedback: String = "",
    val isCorrect: Boolean? = null,
    val earnedXp: Int = 0,
    val currentStreak: Int = 0,
    val isLoading: Boolean = false,
    val errorMessage: String = ""
)

class GamesViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = EntertainmentRepository.getInstance(application)
    private val tts = TextToSpeechManager(application)

    private val _uiState = MutableStateFlow(GamesUiState())
    val uiState: StateFlow<GamesUiState> = _uiState.asStateFlow()

    init {
        EntertainmentAudioSession.registerGameCallbacks {
            tts.stop()
        }
        loadGame(_uiState.value.selectedMode)
    }

    fun setMode(mode: String) {
        if (_uiState.value.selectedMode == mode) return
        _uiState.update {
            it.copy(
                selectedMode = mode,
                userAnswerText = "",
                feedback = "",
                isCorrect = null,
                twentyQHistory = emptyList(),
                twentyQCount = 0,
                errorMessage = ""
            )
        }
        loadGame(mode)
    }

    fun setUserAnswerText(text: String) {
        _uiState.update { it.copy(userAnswerText = text) }
    }

    fun loadGame(mode: String = _uiState.value.selectedMode) {
        _uiState.update { it.copy(isLoading = true, feedback = "", isCorrect = null, errorMessage = "") }

        viewModelScope.launch {
            when (mode) {
                "daily" -> {
                    when (val res = repo.getDailyChallenge()) {
                        is ApiResult.Success -> {
                            val data = res.value
                            _uiState.update { it.copy(dailyChallenge = data, isLoading = false) }
                            if (data.isCompleted) {
                                speak("You have already completed today's challenge! Great job maintaining your daily streak.")
                            } else {
                                speak("Today's Daily Challenge: ${data.challenge.question}")
                            }
                        }
                        is ApiResult.Failure -> {
                            _uiState.update { it.copy(isLoading = false, errorMessage = res.error.userMessage) }
                        }
                    }
                }
                "trivia" -> {
                    when (val res = repo.getTriviaQuestion()) {
                        is ApiResult.Success -> {
                            val data = res.value
                            _uiState.update { it.copy(triviaQuestion = data, isLoading = false) }
                            speak("Trivia Question: ${data.question}")
                        }
                        is ApiResult.Failure -> {
                            _uiState.update { it.copy(isLoading = false, errorMessage = res.error.userMessage) }
                        }
                    }
                }
                "riddles" -> {
                    when (val res = repo.getRiddle()) {
                        is ApiResult.Success -> {
                            val data = res.value
                            _uiState.update { it.copy(riddle = data, isLoading = false) }
                            speak("Riddle: ${data.riddle}")
                        }
                        is ApiResult.Failure -> {
                            _uiState.update { it.copy(isLoading = false, errorMessage = res.error.userMessage) }
                        }
                    }
                }
                "twenty_questions" -> {
                    when (val res = repo.startTwentyQuestions()) {
                        is ApiResult.Success -> {
                            val data = res.value
                            _uiState.update {
                                it.copy(
                                    twentyQuestions = data,
                                    twentyQHistory = emptyList(),
                                    twentyQCount = 0,
                                    isLoading = false
                                )
                            }
                            speak("I have thought of a secret everyday object. Ask me yes or no questions to guess it!")
                        }
                        is ApiResult.Failure -> {
                            _uiState.update { it.copy(isLoading = false, errorMessage = res.error.userMessage) }
                        }
                    }
                }
                "memory" -> {
                    when (val res = repo.getMemoryChallenge(1)) {
                        is ApiResult.Success -> {
                            val data = res.value
                            _uiState.update { it.copy(memoryChallenge = data, isLoading = false) }
                            speak("Listen to these words and repeat them back in order: ${data.prompt}")
                        }
                        is ApiResult.Failure -> {
                            _uiState.update { it.copy(isLoading = false, errorMessage = res.error.userMessage) }
                        }
                    }
                }
            }
        }
    }

    fun submitAnswer(ans: String? = null) {
        val answer = (ans ?: _uiState.value.userAnswerText).trim()
        if (answer.isBlank()) return

        _uiState.update { it.copy(isLoading = true) }

        viewModelScope.launch {
            when (_uiState.value.selectedMode) {
                "daily" -> {
                    val daily = _uiState.value.dailyChallenge ?: return@launch
                    when (val res = repo.completeDailyChallenge(answer, daily.challenge.answer)) {
                        is ApiResult.Success -> {
                            val r = res.value
                            if (r.completed) {
                                _uiState.update {
                                    it.copy(
                                        isLoading = false,
                                        isCorrect = true,
                                        earnedXp = r.earnedXp,
                                        currentStreak = r.streak,
                                        feedback = "Correct! +${r.earnedXp} XP earned! Current streak: ${r.streak} days!"
                                    )
                                }
                                speak("Correct! You earned ${r.earnedXp} XP! Current streak is ${r.streak} days!")
                            } else {
                                _uiState.update {
                                    it.copy(
                                        isLoading = false,
                                        isCorrect = false,
                                        feedback = "Not quite right. Try again!"
                                    )
                                }
                                speak("Not quite right. Try again!")
                            }
                        }
                        is ApiResult.Failure -> {
                            _uiState.update { it.copy(isLoading = false, errorMessage = res.error.userMessage) }
                        }
                    }
                }
                "trivia" -> {
                    val trivia = _uiState.value.triviaQuestion ?: return@launch
                    when (val res = repo.evaluateGameAnswer(trivia.answer, answer, "trivia")) {
                        is ApiResult.Success -> {
                            val r = res.value
                            if (r.isCorrect) {
                                repo.addXp(10, "trivia_master")
                                _uiState.update {
                                    it.copy(
                                        isLoading = false,
                                        isCorrect = true,
                                        feedback = "Correct! +10 XP"
                                    )
                                }
                                speak("Correct! Well done, 10 XP added!")
                            } else {
                                _uiState.update {
                                    it.copy(
                                        isLoading = false,
                                        isCorrect = false,
                                        feedback = "Incorrect. The correct answer was ${trivia.answer}."
                                    )
                                }
                                speak("Incorrect. The correct answer was ${trivia.answer}.")
                            }
                        }
                        is ApiResult.Failure -> {
                            _uiState.update { it.copy(isLoading = false, errorMessage = res.error.userMessage) }
                        }
                    }
                }
                "riddles" -> {
                    val riddle = _uiState.value.riddle ?: return@launch
                    when (val res = repo.evaluateGameAnswer(riddle.answer, answer, "riddle")) {
                        is ApiResult.Success -> {
                            val r = res.value
                            if (r.isCorrect) {
                                repo.addXp(10, "riddle_solver")
                                _uiState.update {
                                    it.copy(
                                        isLoading = false,
                                        isCorrect = true,
                                        feedback = "Correct! You cracked the riddle! +10 XP"
                                    )
                                }
                                speak("Correct! You cracked the riddle! 10 XP added.")
                            } else {
                                _uiState.update {
                                    it.copy(
                                        isLoading = false,
                                        isCorrect = false,
                                        feedback = "Not quite. The answer was ${riddle.answer}."
                                    )
                                }
                                speak("Not quite. The answer was ${riddle.answer}.")
                            }
                        }
                        is ApiResult.Failure -> {
                            _uiState.update { it.copy(isLoading = false, errorMessage = res.error.userMessage) }
                        }
                    }
                }
                "twenty_questions" -> {
                    val session = _uiState.value.twentyQuestions ?: return@launch
                    when (val res = repo.askTwentyQuestions(session.secretObject, answer)) {
                        is ApiResult.Success -> {
                            val r = res.value
                            val newHistory = _uiState.value.twentyQHistory + Pair(answer, r.answer)
                            val newCount = _uiState.value.twentyQCount + 1

                            if (r.isCorrect) {
                                repo.addXp(20, "game_champion")
                                _uiState.update {
                                    it.copy(
                                        isLoading = false,
                                        isCorrect = true,
                                        twentyQHistory = newHistory,
                                        twentyQCount = newCount,
                                        userAnswerText = "",
                                        feedback = "Winner! You guessed the object in $newCount questions! +20 XP"
                                    )
                                }
                                speak("Winner! You correctly guessed the secret object: ${session.secretObject} in $newCount questions!")
                            } else {
                                _uiState.update {
                                    it.copy(
                                        isLoading = false,
                                        twentyQHistory = newHistory,
                                        twentyQCount = newCount,
                                        userAnswerText = "",
                                        feedback = r.answer
                                    )
                                }
                                speak(r.answer)
                            }
                        }
                        is ApiResult.Failure -> {
                            _uiState.update { it.copy(isLoading = false, errorMessage = res.error.userMessage) }
                        }
                    }
                }
                "memory" -> {
                    val memory = _uiState.value.memoryChallenge ?: return@launch
                    val expected = memory.words.map { it.lowercase().trim() }
                    val userWords = answer.lowercase().split(Regex("[ ,]+")).filter { it.isNotBlank() }
                    val matched = expected.count { userWords.contains(it) }
                    val isExact = matched == expected.size

                    _uiState.update { it.copy(isLoading = false) }

                    if (isExact) {
                        repo.addXp(15, "game_champion")
                        _uiState.update {
                            it.copy(
                                isCorrect = true,
                                feedback = "Perfect memory! All words matched! +15 XP"
                            )
                        }
                        speak("Perfect memory! You repeated all the words accurately! 15 XP added.")
                    } else {
                        val seq = memory.words.joinToString(", ")
                        _uiState.update {
                            it.copy(
                                isCorrect = false,
                                feedback = "You remembered $matched of ${expected.size} words. The sequence was: $seq"
                            )
                        }
                        speak("You remembered $matched of ${expected.size} words. The words were $seq.")
                    }
                }
            }
        }
    }

    fun repeatPrompt() {
        when (_uiState.value.selectedMode) {
            "daily" -> _uiState.value.dailyChallenge?.challenge?.question?.let { speak(it) }
            "trivia" -> _uiState.value.triviaQuestion?.question?.let { speak(it) }
            "riddles" -> _uiState.value.riddle?.riddle?.let { speak(it) }
            "twenty_questions" -> speak("I have thought of a secret object. Ask me yes or no questions to guess it!")
            "memory" -> _uiState.value.memoryChallenge?.prompt?.let { speak(it) }
        }
    }

    fun giveClue() {
        if (_uiState.value.selectedMode == "riddles") {
            val clue = _uiState.value.riddle?.clue
            if (!clue.isNullOrBlank()) {
                speak("Clue: $clue")
            } else {
                speak("No clue is available for this riddle.")
            }
        }
    }

    private fun speak(text: String) {
        EntertainmentAudioSession.requestSession(ActiveAudioFeature.GAME)
        tts.speak(text)
    }
}
