package com.example.stardewoffline.feature.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.stardewoffline.core.common.AppResult
import com.example.stardewoffline.core.model.EntityDetail
import com.example.stardewoffline.data.ContentRepository
import com.example.stardewoffline.data.UserDataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class DetailViewModel @Inject constructor(saved: SavedStateHandle, private val content: ContentRepository, private val user: UserDataRepository) : ViewModel() {
    private val id = checkNotNull<String>(saved["id"]); private val mutableDetail = MutableStateFlow<EntityDetail?>(null); val detail = mutableDetail.asStateFlow()
    val favorite = user.isFavorite(id); val note = user.note(id)
    init { viewModelScope.launch { mutableDetail.value = (content.detail(id) as? AppResult.Success)?.value; user.recordView(id) } }
    fun toggle(value: Boolean) = viewModelScope.launch { user.toggleFavorite(id, !value) }
    fun saveNote(value: String) = viewModelScope.launch { user.saveNote(id, value) }
}

@Composable
fun DetailRoute(viewModel: DetailViewModel = hiltViewModel()) {
    val detail by viewModel.detail.collectAsState(); val favorite by viewModel.favorite.collectAsState(false); val note by viewModel.note.collectAsState(null); val text = remember(note?.content) { mutableStateOf(note?.content.orEmpty()) }
    val entity = detail ?: return
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(entity.nameZh, style = androidx.compose.material3.MaterialTheme.typography.headlineSmall); entity.nameEn?.let { Text(it) }
        Text("${entity.entityType}${entity.category?.let { " · $it" }.orEmpty()}"); entity.descriptionZh?.let { Text(it) }
        Button(onClick = { viewModel.toggle(favorite) }) { Text(if (favorite) "取消收藏" else "收藏") }
        entity.extraJson["officialDerived"]?.let { Text("结构化数据\n$it") }
        OutlinedTextField(value = text.value, onValueChange = { if (it.length <= 5000) text.value = it }, label = { Text("个人笔记") }, modifier = Modifier.fillMaxSize())
        Button(onClick = { viewModel.saveNote(text.value) }) { Text("保存笔记") }
    }
}
