package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.NoorPreferences
import com.example.data.ProfileRepository
import com.example.model.PublicCommunityProfile
import com.example.model.UserProfile
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ProfileUiState<out T> {
    data object Loading : ProfileUiState<Nothing>
    data class Success<T>(val data: T) : ProfileUiState<T>
    data class Error(val message: String) : ProfileUiState<Nothing>
}

class ProfileViewModel(
    private val repository: ProfileRepository,
    private val currentUserId: String
) : ViewModel() {

    val userProfileState: StateFlow<ProfileUiState<UserProfile?>> =
        repository.observeUserProfile(currentUserId)
            .map<UserProfile?, ProfileUiState<UserProfile?>> { ProfileUiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing user profile", rootError)
                emit(ProfileUiState.Error(rootError.message ?: "Failed to load profile"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = ProfileUiState.Loading
            )

    private val _communitySearch = MutableStateFlow("")
    val communitySearch: StateFlow<String> = _communitySearch.asStateFlow()

    val communityState: StateFlow<ProfileUiState<List<PublicCommunityProfile>>> =
        combine(
            repository.observeGlobalCommunity(currentUserId),
            _communitySearch
        ) { profiles, query ->
            val q = query.trim().lowercase()
            val sorted = profiles.sortedByDescending { it.totalDhikrSynced }
            val filtered = if (q.isEmpty()) sorted
            else sorted.filter {
                it.displayName.lowercase().contains(q) ||
                    it.city.lowercase().contains(q) ||
                    it.country.lowercase().contains(q)
            }
            ProfileUiState.Success(filtered) as ProfileUiState<List<PublicCommunityProfile>>
        }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing global community", rootError)
                emit(ProfileUiState.Error(rootError.message ?: "Failed to load global community"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = ProfileUiState.Loading
            )

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private var bootstrapAttempted = false

    fun updateCommunitySearch(query: String) {
        _communitySearch.value = query
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }

    fun bootstrapProfileIfMissing(firebaseUser: FirebaseUser, localPrefs: NoorPreferences) {
        if (bootstrapAttempted) return
        val currentState = userProfileState.value
        if (currentState is ProfileUiState.Success && currentState.data == null) {
            bootstrapAttempted = true
            val initialName = firebaseUser.displayName?.trim()?.takeIf { it.length >= 2 }
                ?: firebaseUser.email?.substringBefore("@")?.takeIf { it.length >= 2 }
                ?: "Qalb-ul-Quran Member"
            viewModelScope.launch {
                _isSaving.value = true
                val result = repository.ensureOrSaveProfile(
                    displayName = initialName,
                    email = firebaseUser.email ?: "",
                    photoUrl = firebaseUser.photoUrl?.toString() ?: "",
                    city = localPrefs.city.name,
                    country = localPrefs.city.country,
                    bio = "Peace be upon you. Growing daily with Qalb-ul-Quran.",
                    preferredMethod = localPrefs.calculationMethod.name,
                    preferredReciterId = localPrefs.reciterId,
                    dailyDhikrGoal = 100L,
                    totalDhikrSynced = localPrefs.tasbeehLifetimeTotal,
                    bookmarkedCount = localPrefs.bookmarks.size.toLong(),
                    publicVisible = true
                )
                _isSaving.value = false
                if (result.isFailure) {
                    _actionMessage.value = result.exceptionOrNull()?.localizedMessage
                        ?: "Unable to initialize cloud profile."
                }
            }
        }
    }

    fun saveProfileEdits(
        firebaseUser: FirebaseUser,
        localPrefs: NoorPreferences,
        displayName: String,
        city: String,
        country: String,
        bio: String,
        dailyDhikrGoal: Long,
        publicVisible: Boolean,
        onSuccess: () -> Unit
    ) {
        val cleanName = displayName.trim()
        if (cleanName.length < 2) {
            _actionMessage.value = "Display name must be at least 2 characters."
            return
        }
        val cleanCity = city.trim()
        val cleanCountry = country.trim()
        if (cleanCity.isEmpty() || cleanCountry.isEmpty()) {
            _actionMessage.value = "City and country are required."
            return
        }
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.ensureOrSaveProfile(
                displayName = cleanName,
                email = firebaseUser.email ?: "",
                photoUrl = firebaseUser.photoUrl?.toString() ?: "",
                city = cleanCity,
                country = cleanCountry,
                bio = bio,
                preferredMethod = localPrefs.calculationMethod.name,
                preferredReciterId = localPrefs.reciterId,
                dailyDhikrGoal = dailyDhikrGoal.coerceIn(1L, 10000L),
                totalDhikrSynced = localPrefs.tasbeehLifetimeTotal,
                bookmarkedCount = localPrefs.bookmarks.size.toLong(),
                publicVisible = publicVisible
            )
            _isSaving.value = false
            result.fold(
                onSuccess = {
                    _actionMessage.value = "Profile saved to cloud"
                    onSuccess()
                },
                onFailure = { err ->
                    _actionMessage.value = err.localizedMessage ?: "Failed to save profile"
                }
            )
        }
    }

    fun syncLocalStats(localPrefs: NoorPreferences) {
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.syncSpiritualStats(
                city = localPrefs.city.name,
                country = localPrefs.city.country,
                preferredMethod = localPrefs.calculationMethod.name,
                preferredReciterId = localPrefs.reciterId,
                totalDhikrSynced = localPrefs.tasbeehLifetimeTotal,
                bookmarkedCount = localPrefs.bookmarks.size.toLong()
            )
            _isSaving.value = false
            result.fold(
                onSuccess = { _actionMessage.value = "Synced local Dhikr & bookmarks to cloud" },
                onFailure = { err ->
                    _actionMessage.value = err.localizedMessage ?: "Sync failed"
                }
            )
        }
    }

    fun deleteCloudProfile(onDeleted: () -> Unit) {
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.deleteCloudProfile()
            _isSaving.value = false
            result.fold(
                onSuccess = { onDeleted() },
                onFailure = { err ->
                    _actionMessage.value = err.localizedMessage ?: "Failed to delete cloud profile"
                }
            )
        }
    }

    private companion object {
        const val TAG = "ProfileVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
