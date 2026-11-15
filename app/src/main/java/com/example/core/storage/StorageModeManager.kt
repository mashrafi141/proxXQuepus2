package com.example.core.storage

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.appPreferences by preferencesDataStore(name = "proxxquepus_app_preferences")

class StorageModeManager(private val context: Context) {

    companion object {
        private val KEY_STORAGE_MODE = stringPreferencesKey("storage_mode")
    }

    val storageMode: Flow<StorageMode> = context.appPreferences.data.map { preferences ->
        when (preferences[KEY_STORAGE_MODE]) {
            StorageMode.CLOUD.name -> StorageMode.CLOUD
            else -> StorageMode.LOCAL
        }
    }

    suspend fun setStorageMode(mode: StorageMode) {
        context.appPreferences.edit { preferences ->
            preferences[KEY_STORAGE_MODE] = mode.name
        }
    }
}
