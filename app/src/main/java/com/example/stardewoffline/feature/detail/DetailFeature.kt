package com.example.stardewoffline.feature.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.stardewoffline.core.common.getOrNull
import com.example.stardewoffline.core.model.WikiEntry
import com.example.stardewoffline.data.ContentRepository
import com.example.stardewoffline.data.UserDataRepository
import com.example.stardewoffline.data.wiki.WikiCatalogue
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DetailUiState(
    val entry: WikiEntry? = null,
    val packageRoot: File? = null,
    val error: String? = null,
)

@HiltViewModel
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val catalogue: WikiCatalogue,
    private val content: ContentRepository,
    private val user: UserDataRepository,
) : ViewModel() {
    private val id = checkNotNull<String>(savedStateHandle["id"])
    private val mutableState = MutableStateFlow(DetailUiState())
    val state = mutableState.asStateFlow()
    val favorite = user.isFavorite(id)
    val note = user.note(id)

    init {
        viewModelScope.launch { load() }
    }

    fun toggleFavorite(value: Boolean) = viewModelScope.launch { user.toggleFavorite(id, !value) }

    fun saveNote(value: String) = viewModelScope.launch { user.saveNote(id, value) }

    private suspend fun load() {
        val entry = catalogue.entry(id).getOrNull()
        if (entry == null) {
            mutableState.value = DetailUiState(error = "当前数据包中未找到此条目")
            return
        }
        mutableState.value = DetailUiState(entry = entry, packageRoot = content.packageRoot())
        user.recordView(id)
    }
}
