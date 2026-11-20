package fr.penpenic.babyphone.role

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settings: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class RoleRepository @Inject constructor(@param:ApplicationContext private val context: Context) {

    private val key = stringPreferencesKey("role")

    val role: Flow<Role?> = context.settings.data.map { Role.fromStored(it[key]) }

    suspend fun setRole(role: Role?) {
        context.settings.edit { prefs ->
            if (role == null) prefs.remove(key) else prefs[key] = role.name
        }
    }
}
