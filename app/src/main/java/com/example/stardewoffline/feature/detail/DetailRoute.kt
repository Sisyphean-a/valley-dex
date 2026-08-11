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
    DetailScreen(
        state = state,
        favorite = favorite,
        onBack = onBack,
        onFavorite = { viewModel.toggleFavorite(favorite) },
        onDetail = onDetail,
    )
}
