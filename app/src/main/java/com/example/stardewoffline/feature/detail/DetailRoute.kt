package com.example.stardewoffline.feature.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun DetailRoute(
    onBack: () -> Unit,
    onDetail: (String) -> Unit,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val favorite by viewModel.favorite.collectAsState(false)
    val note by viewModel.note.collectAsState(null)
    DetailScreen(
        state = state,
        favorite = favorite,
        note = note?.content.orEmpty(),
        onBack = onBack,
        onFavorite = { viewModel.toggleFavorite(favorite) },
        onSaveNote = viewModel::saveNote,
        onDetail = onDetail,
    )
}
