package com.king.mob_app_assignment.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.king.mob_app_assignment.data.repository.AuthRepository
import com.king.mob_app_assignment.util.BiometricCredentialStore
import com.king.mob_app_assignment.util.LANGUAGE_TAG_SYSTEM_DEFAULT
import com.king.mob_app_assignment.util.MlKitLanguage
import com.king.mob_app_assignment.util.MlKitLanguages
import com.king.mob_app_assignment.util.ShakePrefsKeys
import com.king.mob_app_assignment.util.ShakePreferences
import com.king.mob_app_assignment.util.TranslationPreferences
import com.king.mob_app_assignment.util.TranslationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EnableBiometricState(
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isComplete: Boolean = false,
    // Set once the typed password has been verified against Firebase - tells SettingsScreen to
    // launch a real BiometricPrompt challenge before biometric login is actually switched on.
    // Holds the credentials needed to finish enabling once that challenge succeeds (see
    // onBiometricChallengeSucceeded) - the password itself never left this ViewModel/state before
    // this point either, so nothing new is exposed by carrying it one step further.
    val pendingBiometricChallenge: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val shakePreferences: ShakePreferences,
    private val biometricCredentialStore: BiometricCredentialStore,
    private val translationPreferences: TranslationPreferences,
    private val translationRepository: TranslationRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    val threshold: StateFlow<Float> = shakePreferences.thresholdFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ShakePrefsKeys.DEFAULT_THRESHOLD.toFloat())

    private val _biometricLoginEnabled = MutableStateFlow(biometricCredentialStore.isEnabled())
    val biometricLoginEnabled: StateFlow<Boolean> = _biometricLoginEnabled.asStateFlow()

    fun disableBiometricLogin() {
        biometricCredentialStore.disable()
        _biometricLoginEnabled.value = false
    }

    private val _enableBiometricState = MutableStateFlow(EnableBiometricState())
    val enableBiometricState: StateFlow<EnableBiometricState> = _enableBiometricState.asStateFlow()

    fun onEnableBiometricPasswordChange(value: String) {
        _enableBiometricState.update { it.copy(password = value, errorMessage = null) }
    }

    /** See BiometricCredentialStore.buildEnableCryptoObject's doc comment. */
    fun buildEnableCryptoObject() = biometricCredentialStore.buildEnableCryptoObject()

    // Settings has no plaintext password in hand the way a fresh LoginViewModel login() does
    // (see that ViewModel's own onEnableBiometricLogin for the "right after manual login" path) -
    // so enabling from here re-runs a real Firebase sign-in with the typed password first, purely
    // as a verification step (Firebase safely accepts re-authenticating as the already-signed-in
    // user). This makes "enable biometric login" available as a standalone Settings action, but
    // it does NOT by itself call BiometricCredentialStore.enable() - see pendingBiometricChallenge
    // below for why.
    //
    // The current user's email is read fresh here via currentUser.first(), NOT cached from an
    // init{} block - a previous version stashed it once in init{} into a plain var, but that
    // read races SettingsViewModel's own construction: authRepository.currentUser is a
    // callbackFlow over FirebaseAuth's AuthStateListener, whose first emission can arrive after
    // the user has already opened this dialog and tapped Enable, leaving the cached var null and
    // silently no-oping (email ?: return) with no error shown at all. Reading it fresh on submit
    // has no such race: by the time Settings is even reachable the user is already signed in, so
    // this always resolves immediately from the listener's already-delivered state.
    fun onEnableBiometricSubmit() {
        val password = _enableBiometricState.value.password
        if (password.isBlank()) {
            _enableBiometricState.update { it.copy(errorMessage = "Enter your password to confirm.") }
            return
        }
        _enableBiometricState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val email = authRepository.currentUser.first()?.email
            if (email.isNullOrBlank()) {
                _enableBiometricState.update {
                    it.copy(isLoading = false, errorMessage = "Couldn't confirm your account. Please try again.")
                }
                return@launch
            }
            val result = authRepository.login(email, password)
            result
                .onSuccess {
                    pendingEmail = email
                    _enableBiometricState.update { it.copy(isLoading = false, pendingBiometricChallenge = true) }
                }
                .onFailure {
                    _enableBiometricState.update {
                        it.copy(isLoading = false, errorMessage = "Incorrect password. Please try again.")
                    }
                }
        }
    }

    // ROOT CAUSE (2026-09-09, user-reported "enable biometric keeps asking for password and
    // doesn't enable"): BiometricCredentialStore.getOrCreateKey() generates its Keystore key with
    // setUserAuthenticationRequired(true) covering BOTH PURPOSE_ENCRYPT and PURPOSE_DECRYPT - the
    // key requires a fresh authentication for either operation, not just decrypt as the earlier
    // doc comment assumed. enable() calls cipher.init(ENCRYPT_MODE, ...) with no authentication
    // having just occurred at all when called straight from a verified-password submit - Firebase
    // password verification is not a device-level authentication event Android's Keystore
    // recognizes. That throws UserNotAuthenticatedException, caught by enable()'s own runCatching
    // and turned into a plain `false`, so it failed identically and silently every single time
    // regardless of how many times the password was re-entered correctly.
    //
    // FIXED by requiring a real BiometricPrompt challenge here too - the same fix already applied
    // to LoginScreen's post-login "enable" popup (see LoginViewModel.onEnableBiometricLoginRequested).
    // pendingEmail/pendingPassword hold the verified credentials only until this challenge
    // resolves; enable() is now only ever called from the BiometricPrompt success callback, at
    // which point a real authentication has just occurred and the Keystore key unlocks correctly.
    //
    // SECOND ROOT CAUSE (2026-09-11 - see BiometricCredentialStore.buildEnableCryptoObject's doc
    // comment for the full explanation): the fix above was necessary but not sufficient. The
    // BiometricPrompt here originally carried no CryptoObject at all, so its success callback had
    // no authenticated Cipher to hand back - onBiometricChallengeSucceeded used to call
    // biometricCredentialStore.enable(email, password) with no cipher, which (in the old
    // enable() signature) created its own fresh Cipher and immediately failed with
    // UserNotAuthenticatedException, exactly reproducing "keeps asking, never enables." Now takes
    // the authenticated Cipher from BiometricPrompt.AuthenticationResult.cryptoObject directly.
    private var pendingEmail: String? = null

    fun onBiometricChallengeSucceeded(authenticatedCipher: javax.crypto.Cipher) {
        val email = pendingEmail
        val password = _enableBiometricState.value.password
        pendingEmail = null
        if (email == null) {
            _enableBiometricState.update {
                it.copy(pendingBiometricChallenge = false, errorMessage = "Something went wrong. Please try again.")
            }
            return
        }
        val success = biometricCredentialStore.enable(email, password, authenticatedCipher)
        _biometricLoginEnabled.value = success
        _enableBiometricState.value = EnableBiometricState(isComplete = success)
    }

    fun onBiometricChallengeFailedOrCancelled() {
        _enableBiometricState.update { it.copy(pendingBiometricChallenge = false) }
    }

    fun resetEnableBiometricState() {
        pendingEmail = null
        _enableBiometricState.value = EnableBiometricState()
    }

    val languageTag: StateFlow<String> = shakePreferences.languageTagFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LANGUAGE_TAG_SYSTEM_DEFAULT)

    val backgroundShakeEnabled: StateFlow<Boolean> = shakePreferences.backgroundShakeEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setBackgroundShakeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            shakePreferences.setBackgroundShakeEnabled(enabled)
        }
    }

    fun onThresholdChange(value: Float) {
        viewModelScope.launch {
            shakePreferences.setThreshold(value)
        }
    }

    // AppCompatDelegate.setApplicationLocales() only auto-recreates the Activity (correctly,
    // preserving nav/back-stack state) when it extends AppCompatActivity, which MainActivity now
    // does specifically to support this. No manual recreate()/event-plumbing is needed here -
    // AppCompat handles the Activity recreation itself once the locale change is applied.
    fun onLanguageChange(tag: String) {
        viewModelScope.launch {
            shakePreferences.setLanguageTag(tag)
        }
        val locales = if (tag == LANGUAGE_TAG_SYSTEM_DEFAULT) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(tag)
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }

    val activeMlKitLanguageTag: StateFlow<String> = translationPreferences.activeMlKitLanguageTagFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val downloadedMlKitLanguageTags: StateFlow<Set<String>> = translationPreferences.downloadedLanguageTagsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    private val _mlKitDownloadInProgressTag = MutableStateFlow<String?>(null)
    val mlKitDownloadInProgressTag: StateFlow<String?> = _mlKitDownloadInProgressTag.asStateFlow()

    private val _mlKitDownloadError = MutableStateFlow<String?>(null)
    val mlKitDownloadError: StateFlow<String?> = _mlKitDownloadError.asStateFlow()

    fun clearMlKitDownloadError() {
        _mlKitDownloadError.value = null
    }

    // Switches the app's live UI text to a downloaded ML Kit language (or back to the app's own
    // native EN/ES/FR when tag is empty). Deliberately does NOT touch AppCompatDelegate's locale
    // here - the app's own string resources (English, since that's ML Kit's translation source)
    // stay the active Android locale for system chrome/date-formatting purposes; only the text
    // routed through translatedStringResource() actually changes, see that composable's own doc
    // comment for why.
    fun onMlKitLanguageChange(tag: String) {
        viewModelScope.launch {
            translationPreferences.setActiveMlKitLanguageTag(tag)
        }
    }

    // Downloads the model for [language] if needed, then activates it. Surfaces failure (no
    // network, storage full, etc.) via mlKitDownloadError rather than silently leaving the
    // picker on the old language - the user picked a specific language, staying on the previous
    // one without explanation would look like the tap did nothing.
    fun onMlKitLanguageSelect(language: MlKitLanguage, wifiOnly: Boolean) {
        val alreadyDownloaded = downloadedMlKitLanguageTags.value.contains(language.displayTag)
        if (alreadyDownloaded) {
            onMlKitLanguageChange(language.displayTag)
            return
        }
        viewModelScope.launch {
            _mlKitDownloadInProgressTag.value = language.displayTag
            val result = translationRepository.downloadLanguage(language, wifiOnly)
            _mlKitDownloadInProgressTag.value = null
            result.onSuccess {
                onMlKitLanguageChange(language.displayTag)
            }.onFailure {
                _mlKitDownloadError.value = language.displayName
            }
        }
    }

    companion object {
        val mlKitLanguages: List<MlKitLanguage> = MlKitLanguages.available
    }
}
