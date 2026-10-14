package com.wtracker.field.configs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import com.wtracker.field.services.models.BusinessSettings

private val Context.appPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = "wtracker_app_prefs")

class AppPreferencesStore(context: Context) {
    private val dataStore = context.applicationContext.appPreferencesDataStore

    val isLightModeFlow: Flow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences -> preferences[KEY_IS_LIGHT_MODE] ?: false }

    suspend fun setLightMode(isLightMode: Boolean) {
        dataStore.edit { preferences -> preferences[KEY_IS_LIGHT_MODE] = isLightMode }
    }

    val lastSeenSummaryIdFlow: Flow<String?> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences -> preferences[KEY_LAST_SEEN_SUMMARY_ID] }

    suspend fun setLastSeenSummaryId(summaryId: String) {
        dataStore.edit { preferences -> preferences[KEY_LAST_SEEN_SUMMARY_ID] = summaryId }
    }

    val businessSettingsFlow: Flow<BusinessSettings> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            val jsonString = preferences[KEY_BUSINESS_SETTINGS]
            if (jsonString != null) {
                try {
                    Json.decodeFromString<BusinessSettings>(jsonString)
                } catch (e: Exception) {
                    BusinessSettings()
                }
            } else {
                BusinessSettings()
            }
        }

    suspend fun setBusinessSettings(settings: BusinessSettings) {
        dataStore.edit { preferences ->
            preferences[KEY_BUSINESS_SETTINGS] = Json.encodeToString(settings)
        }
    }

    suspend fun setGeofenceRadiusMeters(radiusMeters: Int) {
        dataStore.edit { preferences ->
            val current = preferences[KEY_BUSINESS_SETTINGS]
                ?.let { runCatching { Json.decodeFromString<BusinessSettings>(it) }.getOrNull() }
                ?: BusinessSettings()
            preferences[KEY_BUSINESS_SETTINGS] = Json.encodeToString(current.copy(geofenceRadiusMeters = radiusMeters))
        }
    }

    companion object {
        private val KEY_IS_LIGHT_MODE = booleanPreferencesKey("is_light_mode")
        private val KEY_LAST_SEEN_SUMMARY_ID = stringPreferencesKey("last_seen_summary_id")
        private val KEY_BUSINESS_SETTINGS = stringPreferencesKey("business_settings")
    }
}
