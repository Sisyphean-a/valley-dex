package com.example.stardewoffline.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.appPreferencesDataStore by preferencesDataStore(name = "app_preferences")

@Singleton
class AppPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val preferences: Flow<AppPreferences> = context.appPreferencesDataStore.data.map(::toAppPreferences)

    suspend fun current(): AppPreferences = preferences.first()

    suspend fun setActivePackage(id: String?) = edit { values -> values.setOrRemove(ACTIVE_PACKAGE_ID, id) }

    suspend fun setPreviousPackage(id: String?) = edit { values -> values.setOrRemove(PREVIOUS_PACKAGE_ID, id) }

    suspend fun setLastValidatedPackage(id: String?) = edit { values -> values.setOrRemove(LAST_VALIDATED_PACKAGE_ID, id) }

    suspend fun update(block: (MutablePreferences) -> Unit) = context.appPreferencesDataStore.edit(block)

    private suspend fun edit(action: suspend (MutablePreferences) -> Unit) {
        context.appPreferencesDataStore.edit { preferences -> action(preferences) }
    }

    private fun toAppPreferences(values: Preferences) = AppPreferences(
        activePackageId = values[ACTIVE_PACKAGE_ID],
        previousPackageId = values[PREVIOUS_PACKAGE_ID],
        lastValidatedPackageId = values[LAST_VALIDATED_PACKAGE_ID],
        themeMode = values[THEME_MODE] ?: "system",
        showEnglishName = values[SHOW_ENGLISH_NAME] ?: true,
        showTechnicalFields = values[SHOW_TECHNICAL_FIELDS] ?: false,
        searchHistoryEnabled = values[SEARCH_HISTORY_ENABLED] ?: true,
        listLayoutMode = values[LIST_LAYOUT_MODE] ?: "list",
    )

    private fun MutablePreferences.setOrRemove(key: Preferences.Key<String>, value: String?) {
        if (value == null) remove(key) else this[key] = value
    }

    private companion object {
        val ACTIVE_PACKAGE_ID = stringPreferencesKey("active_package_id")
        val PREVIOUS_PACKAGE_ID = stringPreferencesKey("previous_package_id")
        val LAST_VALIDATED_PACKAGE_ID = stringPreferencesKey("last_validated_package_id")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val SHOW_ENGLISH_NAME = booleanPreferencesKey("show_english_name")
        val SHOW_TECHNICAL_FIELDS = booleanPreferencesKey("show_technical_fields")
        val SEARCH_HISTORY_ENABLED = booleanPreferencesKey("search_history_enabled")
        val LIST_LAYOUT_MODE = stringPreferencesKey("list_layout_mode")
    }
}
