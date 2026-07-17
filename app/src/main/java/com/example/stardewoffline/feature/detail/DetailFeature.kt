package com.example.stardewoffline.feature.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.stardewoffline.core.common.getOrNull
import com.example.stardewoffline.core.json.DetailPresentationParser
import com.example.stardewoffline.core.model.DetailPresentation
import com.example.stardewoffline.core.model.EntityDetail
import com.example.stardewoffline.core.model.EntitySummary
import com.example.stardewoffline.data.ContentRepository
import com.example.stardewoffline.data.EntityRelationResolver
import com.example.stardewoffline.data.UserDataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DetailUiState(
    val entity: EntityDetail? = null,
    val presentation: DetailPresentation? = null,
    val targets: Map<String, EntitySummary> = emptyMap(),
    val aliases: List<String> = emptyList(),
    val packageRoot: File? = null,
    val error: String? = null,
)

@HiltViewModel
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val content: ContentRepository,
    private val relations: EntityRelationResolver,
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
        val entity = content.detail(id).getOrNull()
            ?: run { mutableState.value = DetailUiState(error = "当前数据包中未找到此条目"); return }
        val presentation = DetailPresentationParser.present(entity)
        val targets = relations.resolve(presentation.relationGroups.flatMap { it.relations })
        mutableState.value = DetailUiState(entity, presentation, targets, content.aliases(id).getOrNull().orEmpty(), content.packageRoot())
        user.recordView(id)
    }
}
