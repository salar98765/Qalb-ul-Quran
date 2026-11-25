package com.example.data

import android.content.Context
import com.example.R
import com.example.model.PublicCommunityProfile
import com.example.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class ProfileRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank() || uid != userId) {
            throw IllegalStateException("Unauthenticated profile observation refused.")
        }
        val path = "users/$userId"
        emitAll(
            db.collection("users").document(userId)
                .snapshots()
                .map { snapshot ->
                    if (!snapshot.exists()) null
                    else snapshot.toObject(
                        UserProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observeGlobalCommunity(currentUserId: String): Flow<List<PublicCommunityProfile>> = flow {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank() || uid != currentUserId) {
            throw IllegalStateException("Unauthenticated community observation refused.")
        }
        val path = "publicProfiles"
        emitAll(
            db.collection("publicProfiles")
                .whereEqualTo("publicVisible", true)
                .limit(100)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        PublicCommunityProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == currentUserId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun ensureOrSaveProfile(
        displayName: String,
        email: String,
        photoUrl: String,
        city: String,
        country: String,
        bio: String,
        preferredMethod: String,
        preferredReciterId: String,
        dailyDhikrGoal: Long,
        totalDhikrSynced: Long,
        bookmarkedCount: Long,
        publicVisible: Boolean
    ): Result<Unit> {
        var currentPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()

            val cleanName = displayName.trim().take(60)
            require(cleanName.length >= 2) { "Display name must be at least 2 characters." }
            val cleanEmail = email.trim().take(150)
            val cleanPhoto = photoUrl.trim().take(500)
            val cleanCity = city.trim().ifEmpty { "Makkah" }.take(80)
            val cleanCountry = country.trim().ifEmpty { "Saudi Arabia" }.take(80)
            val cleanBio = bio.trim().take(240)
            val cleanMethod = preferredMethod.trim().ifEmpty { "MWL" }.take(30)
            val cleanReciter = preferredReciterId.trim().ifEmpty { "ar.alafasy" }.take(60)
            val clampedGoal = dailyDhikrGoal.coerceIn(1L, 10000L)
            val clampedTotal = totalDhikrSynced.coerceIn(0L, 100_000_000L)
            val clampedBookmarks = bookmarkedCount.coerceIn(0L, 10000L)

            val userRef = db.collection("users").document(uid)
            val publicRef = db.collection("publicProfiles").document(uid)

            currentPath = userRef.path
            val userExists = userRef.get().await().exists()
            if (!userExists) {
                val createPayload = mapOf(
                    "userId" to uid,
                    "displayName" to cleanName,
                    "email" to cleanEmail,
                    "photoUrl" to cleanPhoto,
                    "city" to cleanCity,
                    "country" to cleanCountry,
                    "bio" to cleanBio,
                    "preferredMethod" to cleanMethod,
                    "preferredReciterId" to cleanReciter,
                    "dailyDhikrGoal" to clampedGoal,
                    "totalDhikrSynced" to clampedTotal,
                    "bookmarkedCount" to clampedBookmarks,
                    "publicVisible" to publicVisible,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                userRef.set(createPayload).await()
            } else {
                val updatePayload = mapOf(
                    "displayName" to cleanName,
                    "email" to cleanEmail,
                    "photoUrl" to cleanPhoto,
                    "city" to cleanCity,
                    "country" to cleanCountry,
                    "bio" to cleanBio,
                    "preferredMethod" to cleanMethod,
                    "preferredReciterId" to cleanReciter,
                    "dailyDhikrGoal" to clampedGoal,
                    "totalDhikrSynced" to clampedTotal,
                    "bookmarkedCount" to clampedBookmarks,
                    "publicVisible" to publicVisible,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                userRef.update(updatePayload).await()
            }

            currentPath = publicRef.path
            val publicExists = publicRef.get().await().exists()
            if (!publicExists) {
                val publicCreate = mapOf(
                    "userId" to uid,
                    "displayName" to cleanName,
                    "photoUrl" to cleanPhoto,
                    "city" to cleanCity,
                    "country" to cleanCountry,
                    "bio" to cleanBio,
                    "dailyDhikrGoal" to clampedGoal,
                    "totalDhikrSynced" to clampedTotal,
                    "publicVisible" to publicVisible,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                publicRef.set(publicCreate).await()
            } else {
                val publicUpdate = mapOf(
                    "displayName" to cleanName,
                    "photoUrl" to cleanPhoto,
                    "city" to cleanCity,
                    "country" to cleanCountry,
                    "bio" to cleanBio,
                    "dailyDhikrGoal" to clampedGoal,
                    "totalDhikrSynced" to clampedTotal,
                    "publicVisible" to publicVisible,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                publicRef.update(publicUpdate).await()
            }

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, currentPath)
            Result.failure(e)
        }
    }

    suspend fun syncSpiritualStats(
        city: String,
        country: String,
        preferredMethod: String,
        preferredReciterId: String,
        totalDhikrSynced: Long,
        bookmarkedCount: Long
    ): Result<Unit> {
        var currentPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            val cleanCity = city.trim().ifEmpty { "Makkah" }.take(80)
            val cleanCountry = country.trim().ifEmpty { "Saudi Arabia" }.take(80)
            val cleanMethod = preferredMethod.trim().ifEmpty { "MWL" }.take(30)
            val cleanReciter = preferredReciterId.trim().ifEmpty { "ar.alafasy" }.take(60)
            val clampedTotal = totalDhikrSynced.coerceIn(0L, 100_000_000L)
            val clampedBookmarks = bookmarkedCount.coerceIn(0L, 10000L)

            val userRef = db.collection("users").document(uid)
            val publicRef = db.collection("publicProfiles").document(uid)

            currentPath = userRef.path
            if (userRef.get().await().exists()) {
                userRef.update(
                    mapOf(
                        "city" to cleanCity,
                        "country" to cleanCountry,
                        "preferredMethod" to cleanMethod,
                        "preferredReciterId" to cleanReciter,
                        "totalDhikrSynced" to clampedTotal,
                        "bookmarkedCount" to clampedBookmarks,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            currentPath = publicRef.path
            if (publicRef.get().await().exists()) {
                publicRef.update(
                    mapOf(
                        "city" to cleanCity,
                        "country" to cleanCountry,
                        "totalDhikrSynced" to clampedTotal,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, currentPath)
            Result.failure(e)
        }
    }

    suspend fun deleteCloudProfile(): Result<Unit> {
        var currentPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            val publicRef = db.collection("publicProfiles").document(uid)
            val userRef = db.collection("users").document(uid)

            currentPath = publicRef.path
            if (publicRef.get().await().exists()) {
                publicRef.delete().await()
            }

            currentPath = userRef.path
            if (userRef.get().await().exists()) {
                userRef.delete().await()
            }

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, currentPath)
            Result.failure(e)
        }
    }
}
