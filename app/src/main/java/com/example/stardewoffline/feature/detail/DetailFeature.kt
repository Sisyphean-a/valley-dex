package com.example.stardewoffline.feature.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.stardewoffline.core.common.AppResult
import com.example.stardewoffline.core.common.getOrNull
import com.example.stardewoffline.core.model.WikiEntry
import com.example.stardewoffline.data.Schema5ContentRepository
import com.example.stardewoffline.data.UserDataRepository
import com.example.stardewoffline.data.wiki.WikiCatalogue
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
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
    private val content: Schema5ContentRepository,
    private val user: UserDataRepository,
) : ViewModel() {
    private val id = checkNotNull<String>(savedStateHandle["id"])
    private val mutableState = MutableStateFlow(DetailUiState())
    val state = mutableState.asStateFlow()
    val favorite = user.isFavorite(id)

    init {
        viewModelScope.launch { load() }
    }

    fun toggleFavorite(value: Boolean) = viewModelScope.launch { user.toggleFavorite(id, !value) }

    /**
     * Failure: any content or presentation exception becomes a visible error instead of leaving the detail page loading forever.
     */
    private suspend fun load() {
        val result = try {
            catalogue.entry(id)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            mutableState.value = DetailUiState(error = "读取条目失败：${throwable.message ?: throwable::class.simpleName}")
            return
        }
        when (result) {
            is AppResult.Success -> {
                mutableState.value = DetailUiState(
                    entry = result.value,
                    packageRoot = content.packageRoot(),
                )
                user.recordView(id)
            }
            is AppResult.Failure -> mutableState.value = DetailUiState(error = result.error.message)
        }
    }
}
