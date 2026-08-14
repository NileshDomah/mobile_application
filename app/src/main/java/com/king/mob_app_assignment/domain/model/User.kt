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
    val role: UserRole = UserRole.CITIZEN,
)
