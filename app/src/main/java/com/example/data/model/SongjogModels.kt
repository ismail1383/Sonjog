package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val about: String = "",
    val avatarUrl: String? = null,
    val avatarBase64: String? = null,
    val language: String = "bn",
    val isOnline: Boolean = false,
    @ServerTimestamp val lastSeen: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class DirectoryUser(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val searchName: String = "",
    val about: String = "",
    val avatarUrl: String? = null,
    val avatarBase64: String? = null,
    val discoverable: Boolean = true,
    val isOnline: Boolean = false,
    @ServerTimestamp val lastSeen: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ChatThread(
    @DocumentId val id: String = "",
    val createdBy: String = "",
    val participantIds: List<String> = emptyList(),
    val adminIds: List<String> = emptyList(),
    @field:JvmField val isGroup: Boolean = false,
    val groupName: String? = null,
    val groupIconBase64: String? = null,
    val participantNames: Map<String, String> = emptyMap(),
    val participantAvatars: Map<String, String> = emptyMap(),
    val lastMessage: String = "",
    val lastMessageType: String = "text",
    val lastMessageSenderId: String = "",
    @ServerTimestamp val lastMessageAt: Timestamp? = null,
    val unreadCounts: Map<String, Long> = emptyMap(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val chatId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val participantIds: List<String> = emptyList(),
    val type: String = "text",
    val text: String = "",
    val mediaPayload: String? = null,
    val mediaDurationMs: Long? = null,
    val replyToId: String? = null,
    val replyPreview: String? = null,
    val reactions: Map<String, String> = emptyMap(),
    val readBy: List<String> = emptyList(),
    @field:JvmField val isDeleted: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class StatusStory(
    @DocumentId val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val userAvatar: String? = null,
    val type: String = "text",
    val text: String = "",
    val backgroundHex: String = "#0B7A5C",
    val imageBase64: String? = null,
    val viewerIds: List<String> = emptyList(),
    @field:JvmField val isActive: Boolean = true,
    val expiresAtEpochMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CallSession(
    @DocumentId val id: String = "",
    val callerId: String = "",
    val callerName: String = "",
    val callerAvatar: String? = null,
    val calleeId: String = "",
    val calleeName: String = "",
    val calleeAvatar: String? = null,
    val participantIds: List<String> = emptyList(),
    val callType: String = "audio",
    val status: String = "ringing",
    val offerSdp: String? = null,
    val answerSdp: String? = null,
    val durationSeconds: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class IceCandidateRecord(
    @DocumentId val id: String = "",
    val senderId: String = "",
    val participantIds: List<String> = emptyList(),
    val sdpMid: String = "",
    val sdpMLineIndex: Long = 0L,
    val candidate: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)
