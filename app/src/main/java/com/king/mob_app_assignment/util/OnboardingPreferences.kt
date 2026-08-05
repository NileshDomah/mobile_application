package com.king.mob_app_assignment.util

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.onboardingDataStore by preferencesDataStore(name = "onboarding_settings")

private val HAS_COMPLETED_ONBOARDING = booleanPreferencesKey("has_completed_onboarding")

@Singleton
class OnboardingPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val hasCompletedOnboardingFlow: Flow<Boolean> = context.onboardingDataStore.data.map { prefs ->
        prefs[HAS_COMPLETED_ONBOARDING] ?: false
    }

    suspend fun setOnboardingCompleted() {
        context.onboardingDataStore.edit { prefs ->
            prefs[HAS_COMPLETED_ONBOARDING] = true
        }
    }
}
