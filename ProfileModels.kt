package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String = "",
    val city: String = "",
    val country: String = "",
    val bio: String = "",
    val preferredMethod: String = "MWL",
    val preferredReciterId: String = "ar.alafasy",
    val dailyDhikrGoal: Long = 100L,
    val totalDhikrSynced: Long = 0L,
    val bookmarkedCount: Long = 0L,
    val publicVisible: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class PublicCommunityProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val photoUrl: String = "",
    val city: String = "",
    val country: String = "",
    val bio: String = "",
    val dailyDhikrGoal: Long = 100L,
    val totalDhikrSynced: Long = 0L,
    val publicVisible: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
