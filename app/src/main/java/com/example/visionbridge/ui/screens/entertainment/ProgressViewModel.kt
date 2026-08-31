package com.example.visionbridge.ui.screens.entertainment

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.visionbridge.api.ApiResult
import com.example.visionbridge.data.*
import com.example.visionbridge.utils.TextToSpeechManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ProgressUiState(
    val progress: UserProgressData = UserProgressData(),
    val achievements: List<AchievementItem> = STATIC_ACHIEVEMENTS,
    val isLoading: Boolean = true,
    val errorMessage: String = ""
)

class ProgressViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = EntertainmentRepository.getInstance(application)
    private val tts = TextToSpeechManager(application)

    private val _uiState = MutableStateFlow(ProgressUiState())
    val uiState: StateFlow<ProgressUiState> = _uiState.asStateFlow()

    init {
        loadProgress()
    }

    fun loadProgress() {
        _uiState.update { it.copy(isLoading = true, errorMessage = "") }

        viewModelScope.launch {
            when (val res = repo.getUserProgress()) {
                is ApiResult.Success -> {
                    val p = res.value
                    val unlockedSet = p.achievements.toSet()
                    val mergedAchievements = STATIC_ACHIEVEMENTS.map { item ->
                        item.copy(isUnlocked = unlockedSet.contains(item.id))
                    }

                    _uiState.update {
                        it.copy(
                            progress = p,
                            achievements = mergedAchievements,
                            isLoading = false
                        )
                    }
                }
                is ApiResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = res.error.userMessage
                        )
                    }
                }
            }
        }
    }

    fun readStatsAloud() {
        val p = _uiState.value.progress
        val unlockedCount = _uiState.value.achievements.count { it.isUnlocked }
        val text = "Your progress: ${p.xp} total XP, a daily streak of ${p.streak} days, and $unlockedCount achievements unlocked."
        tts.speak(text)
    }
}
