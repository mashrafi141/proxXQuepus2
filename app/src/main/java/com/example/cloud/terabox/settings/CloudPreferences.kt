package com.example.cloud.terabox.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.cloudPreferences by preferencesDataStore(name = "proxxquepus_cloud_preferences")

class CloudPreferences(private val context: Context) {

    companion object {
        private val KEY_ACTIVE_ACCOUNT = stringPreferencesKey("active_account_id")
        private val KEY_OAUTH_STATE = stringPreferencesKey("oauth_state")
        private fun lastPathKey(accountId: String) = stringPreferencesKey("last_path_$accountId")
    }

    val activeAccountId: Flow<String?> = context.cloudPreferences.data.map { preferences ->
        preferences[KEY_ACTIVE_ACCOUNT]
    }

    val oauthState: Flow<String?> = context.cloudPreferences.data.map { preferences ->
        preferences[KEY_OAUTH_STATE]
    }

    suspend fun getActiveAccountId(): String? = activeAccountId.first()

    suspend fun setActiveAccountId(accountId: String?) {
        context.cloudPreferences.edit { preferences ->
            if (accountId == null) preferences.remove(KEY_ACTIVE_ACCOUNT)
            else preferences[KEY_ACTIVE_ACCOUNT] = accountId
        }
    }

    suspend fun saveOAuthState(state: String) {
        context.cloudPreferences.edit { preferences ->
            preferences[KEY_OAUTH_STATE] = state
        }
    }

    suspend fun getOAuthState(): String? = oauthState.first()

    suspend fun clearOAuthState() {
        context.cloudPreferences.edit { preferences ->
            preferences.remove(KEY_OAUTH_STATE)
        }
    }

    suspend fun saveLastPath(accountId: String, path: String) {
        context.cloudPreferences.edit { preferences ->
            preferences[lastPathKey(accountId)] = path
        }
    }

    suspend fun getLastPath(accountId: String): String? {
        val preferences = context.cloudPreferences.data.first()
        return preferences[lastPathKey(accountId)]
    }
}
