package com.example.stardewoffline.navigation

import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.stardewoffline.feature.detail.DetailRoute
import com.example.stardewoffline.feature.data.DataManagementRoute
import com.example.stardewoffline.feature.favorites.FavoritesRoute
import com.example.stardewoffline.feature.home.HomeRoute
import com.example.stardewoffline.feature.search.SearchRoute
import com.example.stardewoffline.feature.type.TypeListRoute

@Composable
fun AppNavHost() {
    val nav = rememberNavController()
    Scaffold(bottomBar = { NavigationBar { listOf("首页" to "home", "搜索" to "search", "收藏" to "favorites", "更多" to "more").forEach { (label, route) -> NavigationBarItem(selected = false, onClick = { nav.navigate(route) }, icon = { Text(label.take(1)) }, label = { Text(label) }) } } }) { padding ->
        NavHost(nav, "home", Modifier.padding(padding)) {
            composable(route = "home") { HomeRoute(onType = { nav.navigate("type/$it") }) }
            composable(route = "search") { SearchRoute(onDetail = { nav.navigate("detail/${Uri.encode(it)}") }) }
            composable(route = "type/{type}") { TypeListRoute(onDetail = { nav.navigate("detail/${Uri.encode(it)}") }) }
            composable(route = "detail/{id}") {
                DetailRoute(onBack = { nav.popBackStack() }, onDetail = { nav.navigate("detail/${Uri.encode(it)}") })
            }
            composable(route = "favorites") { FavoritesRoute(onDetail = { nav.navigate("detail/${Uri.encode(it)}") }) }
            composable(route = "more") { DataManagementRoute() }
        }
    }
}
