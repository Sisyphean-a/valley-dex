package com.example.stardewoffline.navigation

import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.stardewoffline.feature.about.AboutRoute
import com.example.stardewoffline.feature.about.LicensesRoute
import com.example.stardewoffline.feature.data.DataManagementRoute
import com.example.stardewoffline.feature.detail.DetailRoute
import com.example.stardewoffline.feature.favorites.FavoritesRoute
import com.example.stardewoffline.feature.history.HistoryRoute
import com.example.stardewoffline.feature.home.HomeRoute
import com.example.stardewoffline.feature.more.MoreRoute
import com.example.stardewoffline.feature.search.SearchRoute
import com.example.stardewoffline.feature.settings.SettingsRoute
import com.example.stardewoffline.feature.type.TypeListRoute

private data class MainDestination(
    val label: String,
    val route: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

private val MAIN_DESTINATIONS = listOf(
    MainDestination("首页", "home", Icons.Filled.Home),
    MainDestination("搜索", "search", Icons.Filled.Search),
    MainDestination("收藏", "favorites", Icons.Filled.Favorite),
    MainDestination("更多", "more", Icons.Filled.MoreHoriz),
)

@Composable
fun AppNavHost() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    Scaffold(bottomBar = { if (route in MAIN_DESTINATIONS.map(MainDestination::route)) BottomBar(route, nav::navigate) }) { padding ->
        NavHost(nav, "home", Modifier.padding(padding)) {
            composable("home") { HomeRoute(onCategory = { nav.navigate("catalogue/${Uri.encode(it)}") }, onDetail = { nav.navigate(detailRoute(it)) }) }
            composable("search") { SearchRoute(onDetail = { nav.navigate(detailRoute(it)) }) }
            composable("catalogue/{categoryId}") { TypeListRoute(onDetail = { nav.navigate(detailRoute(it)) }) }
            composable("detail/{id}") { DetailRoute(onBack = nav::popBackStack, onDetail = { nav.navigate(detailRoute(it)) }) }
            composable("favorites") { FavoritesRoute(onDetail = { nav.navigate(detailRoute(it)) }) }
            composable("more") { MoreRoute(
                onHistory = { nav.navigate("history") }, onDataManagement = { nav.navigate("data") },
                onSettings = { nav.navigate("settings") }, onAbout = { nav.navigate("about") }, onLicenses = { nav.navigate("licenses") },
            ) }
            composable("history") { HistoryRoute(nav::popBackStack, onDetail = { nav.navigate(detailRoute(it)) }) }
            composable("settings") { SettingsRoute() }
            composable("data") { DataManagementRoute(nav::popBackStack) }
            composable("about") { AboutRoute(nav::popBackStack) }
            composable("licenses") { LicensesRoute(nav::popBackStack) }
        }
    }
}

@Composable
private fun BottomBar(currentRoute: String?, onNavigate: (String) -> Unit) {
    NavigationBar {
        MAIN_DESTINATIONS.forEach { item ->
            NavigationBarItem(
                selected = currentRoute == item.route,
                onClick = { onNavigate(item.route) },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) },
            )
        }
    }
}

private fun detailRoute(id: String) = "detail/${Uri.encode(id)}"
