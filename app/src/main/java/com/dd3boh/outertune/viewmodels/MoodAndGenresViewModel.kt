package com.dd3boh.outertune.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dd3boh.outertune.utils.aether.AetherProxyManager
import com.dd3boh.outertune.utils.reportException
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.pages.MoodAndGenres
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MoodAndGenresViewModel @Inject constructor() : ViewModel() {
    val moodAndGenres = MutableStateFlow<List<MoodAndGenres>?>(null)

    init {
        load()
        viewModelScope.launch {
            AetherProxyManager.state
                .distinctUntilChanged()
                .collect { state ->
                    if (state == AetherProxyManager.State.CONNECTED && moodAndGenres.value == null) {
                        load()
                    }
                }
        }
    }

    fun load() {
        viewModelScope.launch {
            if (AetherProxyManager.state.value == AetherProxyManager.State.STARTING) {
                AetherProxyManager.awaitReady(20_000L)
            }
            YouTube.moodAndGenres().onSuccess {
                moodAndGenres.value = it
            }.onFailure {
                reportException(it)
            }
        }
    }
}
