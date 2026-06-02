package com.ico.nekofeed.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "neko_feed_prefs")

class TokenManager(private val context: Context) {

    companion object {
        private val TOKEN_KEY = stringPreferencesKey("auth_token")
        private val USERNAME_KEY = stringPreferencesKey("username")
    }

    val token: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[TOKEN_KEY]
    }

    val username: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[USERNAME_KEY]
    }

    suspend fun saveToken(token: String) {
        context.dataStore.edit { preferences ->
            preferences[TOKEN_KEY] = token
        }
    }

    suspend fun saveUsername(username: String) {
        context.dataStore.edit { preferences ->
            preferences[USERNAME_KEY] = username
        }
    }

    suspend fun clearAll() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }

    suspend fun getToken(): String? {
        var tokenValue: String? = null
        context.dataStore.data.collect { preferences ->
            tokenValue = preferences[TOKEN_KEY]
        }
        return tokenValue
    }
}
