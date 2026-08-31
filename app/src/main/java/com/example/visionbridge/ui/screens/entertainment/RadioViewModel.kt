package com.example.visionbridge.ui.screens.entertainment

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.visionbridge.api.ApiResult
import com.example.visionbridge.audio.*
import com.example.visionbridge.data.EntertainmentRepository
import com.example.visionbridge.data.RadioLocation
import com.example.visionbridge.data.RadioStation
import com.example.visionbridge.utils.TextToSpeechManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class RadioUiState(
    val currentStation: RadioStation? = null,
    val stations: List<RadioStation> = emptyList(),
    val location: RadioLocation? = null,
    val selectedFilter: String = "local", // "local" | "marathi" | "hindi" | "english"
    val searchQuery: String = "",
    val playbackState: MediaPlaybackState = MediaPlaybackState.IDLE,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val isReconnecting: Boolean = false,
    val errorMessage: String = "",
    val drawerOpen: Boolean = false
)

class RadioViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = EntertainmentRepository.getInstance(application)
    private val tts = TextToSpeechManager(application)

    private val _uiState = MutableStateFlow(RadioUiState())
    val uiState: StateFlow<RadioUiState> = _uiState.asStateFlow()

    init {
        // Ensure MediaService is started
        EntertainmentMediaService.startService(application)

        // Observe MediaService states
        viewModelScope.launch {
            EntertainmentMediaService.playbackStateFlow.collect { pState ->
                _uiState.update {
                    it.copy(
                        playbackState = pState,
                        isPlaying = pState == MediaPlaybackState.PLAYING,
                        isLoading = pState == MediaPlaybackState.CONNECTING,
                        isReconnecting = pState == MediaPlaybackState.RECONNECTING
                    )
                }
            }
        }

        viewModelScope.launch {
            EntertainmentMediaService.currentStationFlow.collect { station ->
                _uiState.update { it.copy(currentStation = station) }
            }
        }

        viewModelScope.launch {
            EntertainmentMediaService.errorMessageFlow.collect { err ->
                if (err.isNotBlank()) {
                    _uiState.update { it.copy(errorMessage = err) }
                }
            }
        }

        // Initial station discovery
        loadLocalStations()
    }

    fun toggleDrawer() {
        _uiState.update { it.copy(drawerOpen = !it.drawerOpen) }
    }

    fun setDrawerOpen(open: Boolean) {
        _uiState.update { it.copy(drawerOpen = open) }
    }

    fun loadLocalStations(filter: String = "local", autoPlay: Boolean = false) {
        _uiState.update { it.copy(isLoading = true, selectedFilter = filter, errorMessage = "") }
        viewModelScope.launch {
            val result = repo.getLocalStations(
                language = if (filter == "local") null else filter,
                forceRefresh = true
            )
            when (result) {
                is ApiResult.Success -> {
                    val location = result.value.first
                    val stationList = result.value.second
                    EntertainmentMediaService.setStationsList(stationList)
                    _uiState.update {
                        it.copy(
                            stations = stationList,
                            location = location,
                            isLoading = false
                        )
                    }
                    if (autoPlay && stationList.isNotEmpty() && !itIsPlaying()) {
                        playStation(stationList[0])
                    }
                }
                is ApiResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = result.error.userMessage
                        )
                    }
                }
            }
        }
    }

    fun searchStations(query: String) {
        if (query.isBlank()) {
            loadLocalStations(_uiState.value.selectedFilter)
            return
        }

        _uiState.update { it.copy(isLoading = true, searchQuery = query, errorMessage = "") }
        viewModelScope.launch {
            val result = repo.searchStations(query)
            when (result) {
                is ApiResult.Success -> {
                    val stationList = result.value
                    EntertainmentMediaService.setStationsList(stationList)
                    _uiState.update {
                        it.copy(
                            stations = stationList,
                            isLoading = false
                        )
                    }
                }
                is ApiResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = result.error.userMessage
                        )
                    }
                }
            }
        }
    }

    fun playStation(station: RadioStation) {
        val service = EntertainmentMediaService.instance
        if (service != null) {
            service.playRadioStation(station)
        } else {
            EntertainmentMediaService.startService(getApplication())
            viewModelScope.launch {
                delay(150)
                EntertainmentMediaService.instance?.playRadioStation(station)
            }
        }
    }

    fun togglePlayPause() {
        val service = EntertainmentMediaService.instance
        if (service == null) {
            EntertainmentMediaService.startService(getApplication())
            viewModelScope.launch {
                delay(150)
                togglePlayPause()
            }
            return
        }

        if (_uiState.value.isPlaying) {
            service.pausePlayback()
        } else {
            val current = _uiState.value.currentStation
            if (current != null) {
                service.resumePlayback()
            } else if (_uiState.value.stations.isNotEmpty()) {
                playStation(_uiState.value.stations[0])
            } else {
                loadLocalStations(autoPlay = true)
            }
        }
    }

    fun nextStation() {
        val stations = _uiState.value.stations
        if (stations.isEmpty()) return
        val current = _uiState.value.currentStation
        val currentIdx = stations.indexOfFirst { it.id == current?.id }
        val nextIdx = if (currentIdx >= 0) (currentIdx + 1) % stations.size else 0
        playStation(stations[nextIdx])
    }

    fun prevStation() {
        val stations = _uiState.value.stations
        if (stations.isEmpty()) return
        val current = _uiState.value.currentStation
        val currentIdx = stations.indexOfFirst { it.id == current?.id }
        val prevIdx = if (currentIdx > 0) currentIdx - 1 else stations.size - 1
        playStation(stations[prevIdx])
    }

    fun stopRadio() {
        EntertainmentMediaService.instance?.stopPlayback()
    }

    fun getCurrentStationSpeechInfo(): String {
        val st = _uiState.value.currentStation
        return if (st != null) {
            val loc = if (st.city.isNotBlank()) " from ${st.city}" else ""
            "Now playing ${st.name}$loc."
        } else {
            "No radio station is currently active."
        }
    }

    private fun itIsPlaying(): Boolean = _uiState.value.isPlaying
}
