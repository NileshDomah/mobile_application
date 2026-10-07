package com.king.mob_app_assignment.domain.model

data class User(
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    // Lowercased, unique across all accounts - reserved via a separate /usernames/{username}
    // document at registration time (see AuthRepositoryImpl.register) since Firestore rules
    // alone can't enforce cross-document uniqueness within a single /users/{uid} write. Lets a
    // citizen sign in with a username instead of typing their email - Firebase Auth itself still
    // only ever signs in with email/password under the hood; LoginViewModel resolves whichever
    // the user typed to the matching email first.
    val username: String = "",
    // Collected at registration (see RegisterScreen) - kept as a plain string rather than a
    // structured/validated phone type, matching how this project doesn't otherwise do
    // international phone number parsing/formatting anywhere else. Optional in practice (the
    // registration form asks for it but does not hard-block on it) since not every citizen
    // wants to share a phone number to file a report.
    val phoneNumber: String = "",
    val role: UserRole = UserRole.CITIZEN,
    // This device's current FCM registration token, refreshed whenever
    // FcmTokenService.onNewToken() fires (token rotation, app reinstall, data clear). Used by
    // the sendReportStatusPush Cloud Function to find where to deliver a push notification when
    // this user's report changes status. Only ever the CALLING USER's own most-recent token -
    // a real multi-device deployment would need an array of tokens, one per device; this project
    // keeps it to a single field since demonstrating the push mechanism end-to-end matters more
    // than multi-device fan-out for a coursework submission (see PLACEHOLDERS_TODO.csv).
    val fcmToken: String = "",
)
