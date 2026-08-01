package com.king.mob_app_assignment.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.king.mob_app_assignment.data.remote.FirestoreConstants
import com.king.mob_app_assignment.domain.model.User
import com.king.mob_app_assignment.domain.model.UserRole
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
) : AuthRepository {

    override val currentUser: Flow<User?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            val firebaseUser = auth.currentUser
            if (firebaseUser == null) {
                trySend(null)
            } else {
                trySend(firebaseUser.toBasicUser())
                fetchUserDocument(firebaseUser.uid) { fetched ->
                    trySend(fetched ?: firebaseUser.toBasicUser())
                }
            }
        }
        firebaseAuth.addAuthStateListener(listener)
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }

    private fun fetchUserDocument(uid: String, onResult: (User?) -> Unit) {
        firestore.collection(FirestoreConstants.USERS_COLLECTION).document(uid)
            .get()
            .addOnSuccessListener { snapshot -> onResult(snapshot.toUserOrNull()) }
            .addOnFailureListener { onResult(null) }
    }

    override suspend fun register(email: String, password: String, displayName: String): Result<User> =
        runCatching {
            val authResult = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
            val firebaseUser = authResult.user ?: error("Registration failed: no user returned")

            val profileUpdates = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                .setDisplayName(displayName)
                .build()
            firebaseUser.updateProfile(profileUpdates).await()

            val newUser = User(
                uid = firebaseUser.uid,
                email = email,
                displayName = displayName,
                role = UserRole.CITIZEN,
            )
            firestore.collection(FirestoreConstants.USERS_COLLECTION)
                .document(firebaseUser.uid)
                .set(newUser.toFirestoreMap())
                .await()

            newUser
        }

    override suspend fun login(email: String, password: String): Result<User> =
        runCatching {
            val authResult = firebaseAuth.signInWithEmailAndPassword(email, password).await()
            val firebaseUser = authResult.user ?: error("Login failed: no user returned")
            val snapshot = firestore.collection(FirestoreConstants.USERS_COLLECTION)
                .document(firebaseUser.uid)
                .get()
                .await()
            snapshot.toUserOrNull() ?: firebaseUser.toBasicUser()
        }

    override fun logout() {
        firebaseAuth.signOut()
    }

    override suspend fun fetchCurrentUserProfile(): Result<User?> = runCatching {
        val firebaseUser = firebaseAuth.currentUser ?: return@runCatching null
        val snapshot = firestore.collection(FirestoreConstants.USERS_COLLECTION)
            .document(firebaseUser.uid)
            .get()
            .await()
        snapshot.toUserOrNull() ?: firebaseUser.toBasicUser()
    }

    private fun FirebaseUser.toBasicUser(): User = User(
        uid = uid,
        email = email.orEmpty(),
        displayName = displayName.orEmpty(),
        role = UserRole.CITIZEN,
    )

    private fun com.google.firebase.firestore.DocumentSnapshot.toUserOrNull(): User? {
        if (!exists()) return null
        return User(
            uid = getString(FirestoreConstants.UserFields.UID) ?: id,
            email = getString(FirestoreConstants.UserFields.EMAIL).orEmpty(),
            displayName = getString(FirestoreConstants.UserFields.DISPLAY_NAME).orEmpty(),
            role = UserRole.fromFirestoreValue(getString(FirestoreConstants.UserFields.ROLE)),
        )
    }

    private fun User.toFirestoreMap(): Map<String, Any> = mapOf(
        FirestoreConstants.UserFields.UID to uid,
        FirestoreConstants.UserFields.EMAIL to email,
        FirestoreConstants.UserFields.DISPLAY_NAME to displayName,
        FirestoreConstants.UserFields.ROLE to role.name,
    )
}
