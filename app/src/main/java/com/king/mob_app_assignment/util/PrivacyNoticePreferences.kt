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

private val Context.privacyNoticeDataStore by preferencesDataStore(name = "privacy_notice_settings")

private val HAS_ACKNOWLEDGED_PRIVACY_NOTICE = booleanPreferencesKey("has_acknowledged_privacy_notice")

/**
 * Tracks whether the citizen has been shown and acknowledged the privacy notice - a required
 * step (not skippable the way OnboardingScreen's own pager can be skipped past) shown once,
 * right after onboarding, before the auth/main graph. Separate DataStore file from
 * OnboardingPreferences since this is a distinct concern (data-collection disclosure, not "have
 * you seen the feature walkthrough") that should never be silently reset alongside onboarding
 * state if either one is ever cleared independently.
 */
@Singleton
class PrivacyNoticePreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val hasAcknowledgedFlow: Flow<Boolean> = context.privacyNoticeDataStore.data.map { prefs ->
        prefs[HAS_ACKNOWLEDGED_PRIVACY_NOTICE] ?: false
    }

    suspend fun setAcknowledged() {
        context.privacyNoticeDataStore.edit { prefs ->
            prefs[HAS_ACKNOWLEDGED_PRIVACY_NOTICE] = true
        }
    }
}
