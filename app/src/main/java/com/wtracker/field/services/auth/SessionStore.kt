package com.wtracker.field.services.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.wtracker.field.services.models.UserSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "wtracker_session")

/**
 * Persistencia de la sesión JWT (almacenamiento privado de la app, excluido de backups).
 * Mantiene una caché en memoria porque los interceptores de OkHttp son síncronos.
 */
class SessionStore(context: Context) {

    private val dataStore = context.applicationContext.sessionDataStore
    private val cachedSession = MutableStateFlow<UserSession?>(null)
    private val loadMutex = Mutex()

    @Volatile
    private var isLoaded = false

    val sessionFlow: StateFlow<UserSession?> = cachedSession.asStateFlow()

    suspend fun ensureLoaded(): UserSession? {
        if (isLoaded) return cachedSession.value
        return loadMutex.withLock {
            if (!isLoaded) {
                cachedSession.value = readFromDisk()
                isLoaded = true
            }
            cachedSession.value
        }
    }

    /** Uso exclusivo desde hilos de OkHttp (nunca desde el hilo principal). */
    fun peekSessionBlocking(): UserSession? {
        if (isLoaded) return cachedSession.value
        return runBlocking { ensureLoaded() }
    }

    suspend fun saveSession(session: UserSession) {
        dataStore.edit { preferences ->
            preferences[KEY_USER_ID] = session.userId
            preferences[KEY_FULL_NAME] = session.fullName
            preferences[KEY_EMAIL] = session.email
            preferences[KEY_ROLE] = session.role
            preferences[KEY_ACCESS_TOKEN] = session.accessToken
            preferences[KEY_REFRESH_TOKEN] = session.refreshToken
        }
        cachedSession.value = session
        isLoaded = true
    }

    suspend fun updateAccessToken(accessToken: String) {
        val currentSession = cachedSession.value ?: return
        dataStore.edit { preferences -> preferences[KEY_ACCESS_TOKEN] = accessToken }
        cachedSession.value = currentSession.copy(accessToken = accessToken)
    }

    suspend fun clearSession() {
        dataStore.edit { preferences -> preferences.clear() }
        cachedSession.value = null
        isLoaded = true
    }

    private suspend fun readFromDisk(): UserSession? {
        val preferences = dataStore.data
            .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
            .first()

        val userId = preferences[KEY_USER_ID] ?: return null
        val accessToken = preferences[KEY_ACCESS_TOKEN] ?: return null
        val refreshToken = preferences[KEY_REFRESH_TOKEN] ?: return null

        return UserSession(
            userId = userId,
            fullName = preferences[KEY_FULL_NAME].orEmpty(),
            email = preferences[KEY_EMAIL].orEmpty(),
            role = preferences[KEY_ROLE].orEmpty(),
            accessToken = accessToken,
            refreshToken = refreshToken,
        )
    }

    private companion object {
        val KEY_USER_ID = stringPreferencesKey("user_id")
        val KEY_FULL_NAME = stringPreferencesKey("full_name")
        val KEY_EMAIL = stringPreferencesKey("email")
        val KEY_ROLE = stringPreferencesKey("role")
        val KEY_ACCESS_TOKEN = stringPreferencesKey("access_token")
        val KEY_REFRESH_TOKEN = stringPreferencesKey("refresh_token")
    }
}
