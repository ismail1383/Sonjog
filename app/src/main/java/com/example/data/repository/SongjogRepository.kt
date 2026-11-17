package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.core.OperationType
import com.example.core.handleFirestoreError
import com.example.data.model.CallSession
import com.example.data.model.ChatMessage
import com.example.data.model.ChatThread
import com.example.data.model.DirectoryUser
import com.example.data.model.IceCandidateRecord
import com.example.data.model.StatusStory
import com.example.data.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
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
import java.util.UUID

class SongjogRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        auth = Firebase.auth
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    private fun <T> Flow<T>.logFirestoreErrors(
        operationType: OperationType,
        path: String,
        expectedUid: String?
    ): Flow<T> = catch { error ->
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && (expectedUid == null || auth.currentUser?.uid == expectedUid)) {
            handleFirestoreError(exceptionToLog, operationType, path)
        }
        throw error
    }

    // --- User Profile & Directory ---

    suspend fun ensureUserSignedInProfile(
        firebaseUser: FirebaseUser,
        preferredLanguage: String = "bn"
    ) {
        val uid = requireUserId()
        val userRef = db.collection("users").document(uid)
        val dirRef = db.collection("userDirectory").document(uid)
        val displayName = (firebaseUser.displayName?.takeIf { it.isNotBlank() }
            ?: firebaseUser.email?.substringBefore("@")
            ?: "Songjog User").take(60)
        val email = (firebaseUser.email?.takeIf { it.isNotBlank() } ?: "$uid@songjog.app").take(120)
        val photoUrl = firebaseUser.photoUrl?.toString()?.take(500)

        try {
            val existingUser = userRef.get().await()
            if (!existingUser.exists()) {
                val userPayload = mutableMapOf<String, Any?>(
                    "userId" to uid,
                    "displayName" to displayName,
                    "email" to email,
                    "about" to "হ্যালো! আমি সংযোগ ব্যবহার করছি।",
                    "avatarUrl" to photoUrl,
                    "language" to preferredLanguage,
                    "isOnline" to true,
                    "lastSeen" to FieldValue.serverTimestamp(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                userRef.set(userPayload).await()
            } else {
                userRef.update(
                    mapOf(
                        "isOnline" to true,
                        "lastSeen" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            val existingDir = dirRef.get().await()
            if (!existingDir.exists()) {
                val dirPayload = mutableMapOf<String, Any?>(
                    "userId" to uid,
                    "displayName" to displayName,
                    "searchName" to displayName.lowercase(),
                    "about" to "হ্যালো! আমি সংযোগ ব্যবহার করছি।",
                    "avatarUrl" to photoUrl,
                    "discoverable" to true,
                    "isOnline" to true,
                    "lastSeen" to FieldValue.serverTimestamp(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                dirRef.set(dirPayload).await()
            } else {
                dirRef.update(
                    mapOf(
                        "isOnline" to true,
                        "lastSeen" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, userRef.path)
            throw e
        }
    }

    suspend fun updateProfile(
        displayName: String,
        about: String,
        avatarBase64: String? = null,
        language: String? = null,
        discoverable: Boolean? = null
    ) {
        val uid = requireUserId()
        val cleanName = displayName.trim().take(60).ifBlank { "Songjog User" }
        val cleanAbout = about.trim().take(160)

        val userUpdates = mutableMapOf<String, Any>(
            "displayName" to cleanName,
            "about" to cleanAbout,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (avatarBase64 != null) userUpdates["avatarBase64"] = avatarBase64
        if (language != null) userUpdates["language"] = language

        val dirUpdates = mutableMapOf<String, Any>(
            "displayName" to cleanName,
            "searchName" to cleanName.lowercase(),
            "about" to cleanAbout,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (avatarBase64 != null) dirUpdates["avatarBase64"] = avatarBase64
        if (discoverable != null) dirUpdates["discoverable"] = discoverable

        val userRef = db.collection("users").document(uid)
        val dirRef = db.collection("userDirectory").document(uid)
        try {
            val batch = db.batch()
            batch.update(userRef, userUpdates)
            batch.update(dirRef, dirUpdates)
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, userRef.path)
            throw e
        }
    }

    suspend fun setOnlineStatus(isOnline: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        val updates = mapOf(
            "isOnline" to isOnline,
            "lastSeen" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            val batch = db.batch()
            batch.update(db.collection("users").document(uid), updates)
            batch.update(db.collection("userDirectory").document(uid), updates)
            batch.commit().await()
        } catch (_: Exception) {
            // Best-effort presence update
        }
    }

    fun observeMyProfile(userId: String): Flow<UserProfile?> = flow {
        val uid = requireUserId()
        val docRef = db.collection("users").document(userId)
        emitAll(
            docRef.snapshots()
                .map { snap ->
                    snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .logFirestoreErrors(OperationType.GET, docRef.path, uid)
        )
    }

    fun observeDirectoryUsers(): Flow<List<DirectoryUser>> = flow {
        val uid = requireUserId()
        val path = "userDirectory"
        emitAll(
            db.collection(path)
                .whereEqualTo("discoverable", true)
                .snapshots()
                .map { snap ->
                    snap.toObjects(DirectoryUser::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.displayName.lowercase() }
                }
                .logFirestoreErrors(OperationType.LIST, path, uid)
        )
    }

    // --- Chats & Groups ---

    fun observeChats(userId: String): Flow<List<ChatThread>> = flow {
        val uid = requireUserId()
        val path = "chats"
        emitAll(
            db.collection(path)
                .whereArrayContains("participantIds", userId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(ChatThread::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.lastMessageAt ?: it.updatedAt ?: Timestamp(0, 0) }
                }
                .logFirestoreErrors(OperationType.LIST, path, uid)
        )
    }

    fun observeChat(chatId: String): Flow<ChatThread?> = flow {
        val uid = requireUserId()
        val docRef = db.collection("chats").document(chatId)
        emitAll(
            docRef.snapshots()
                .map { snap ->
                    snap.toObject(ChatThread::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .logFirestoreErrors(OperationType.GET, docRef.path, uid)
        )
    }

    suspend fun getOrCreateDirectChat(
        currentUser: UserProfile,
        targetUser: DirectoryUser
    ): String {
        val uid = requireUserId()
        val sortedIds = listOf(uid, targetUser.userId).sorted()
        val chatId = "dm_${sortedIds[0]}_${sortedIds[1]}".take(120)
        val chatRef = db.collection("chats").document(chatId)
        try {
            val existing = chatRef.get().await()
            if (!existing.exists()) {
                val names = mapOf(
                    uid to currentUser.displayName.ifBlank { "Me" },
                    targetUser.userId to targetUser.displayName.ifBlank { "User" }
                )
                val avatars = buildMap {
                    val myAvatar = currentUser.avatarBase64 ?: currentUser.avatarUrl
                    val theirAvatar = targetUser.avatarBase64 ?: targetUser.avatarUrl
                    if (!myAvatar.isNullOrBlank() && myAvatar.length <= 500) put(uid, myAvatar)
                    if (!theirAvatar.isNullOrBlank() && theirAvatar.length <= 500) put(targetUser.userId, theirAvatar)
                }
                val payload = mapOf(
                    "createdBy" to uid,
                    "participantIds" to sortedIds,
                    "adminIds" to listOf(uid),
                    "isGroup" to false,
                    "participantNames" to names,
                    "participantAvatars" to avatars,
                    "lastMessage" to "",
                    "lastMessageType" to "text",
                    "lastMessageSenderId" to uid,
                    "lastMessageAt" to FieldValue.serverTimestamp(),
                    "unreadCounts" to mapOf(uid to 0L, targetUser.userId to 0L),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                chatRef.set(payload).await()
            }
            return chatId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, chatRef.path)
            throw e
        }
    }

    suspend fun createGroupChat(
        currentUser: UserProfile,
        groupName: String,
        members: List<DirectoryUser>,
        groupIconBase64: String? = null
    ): String {
        val uid = requireUserId()
        val chatId = "grp_${UUID.randomUUID().toString().replace("-", "").take(20)}"
        val chatRef = db.collection("chats").document(chatId)
        val participantIds = (listOf(uid) + members.map { it.userId }).distinct().take(50)
        val names = buildMap {
            put(uid, currentUser.displayName.ifBlank { "Me" })
            members.forEach { put(it.userId, it.displayName) }
        }
        val avatars = buildMap {
            val myAvatar = currentUser.avatarUrl
            if (!myAvatar.isNullOrBlank() && myAvatar.length <= 500) put(uid, myAvatar)
            members.forEach {
                val url = it.avatarUrl
                if (!url.isNullOrBlank() && url.length <= 500) put(it.userId, url)
            }
        }
        val unreadCounts = participantIds.associateWith { 0L }
        val payload = mutableMapOf<String, Any?>(
            "createdBy" to uid,
            "participantIds" to participantIds,
            "adminIds" to listOf(uid),
            "isGroup" to true,
            "groupName" to groupName.trim().take(60),
            "groupIconBase64" to groupIconBase64,
            "participantNames" to names,
            "participantAvatars" to avatars,
            "lastMessage" to groupName.trim().take(60),
            "lastMessageType" to "text",
            "lastMessageSenderId" to uid,
            "lastMessageAt" to FieldValue.serverTimestamp(),
            "unreadCounts" to unreadCounts,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        ).filterValues { it != null }

        try {
            chatRef.set(payload).await()
            return chatId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, chatRef.path)
            throw e
        }
    }

    suspend fun updateGroupInfo(chatId: String, newName: String, iconBase64: String? = null) {
        requireUserId()
        val chatRef = db.collection("chats").document(chatId)
        val updates = mutableMapOf<String, Any>(
            "groupName" to newName.trim().take(60),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (iconBase64 != null) updates["groupIconBase64"] = iconBase64
        try {
            chatRef.update(updates).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, chatRef.path)
            throw e
        }
    }

    suspend fun addGroupMembers(chat: ChatThread, newMembers: List<DirectoryUser>) {
        requireUserId()
        val chatRef = db.collection("chats").document(chat.id)
        val updatedIds = (chat.participantIds + newMembers.map { it.userId }).distinct().take(50)
        val updatedNames = chat.participantNames.toMutableMap().apply {
            newMembers.forEach { put(it.userId, it.displayName) }
        }
        val updatedUnread = chat.unreadCounts.toMutableMap().apply {
            newMembers.forEach { putIfAbsent(it.userId, 0L) }
        }
        try {
            chatRef.update(
                mapOf(
                    "participantIds" to updatedIds,
                    "participantNames" to updatedNames,
                    "unreadCounts" to updatedUnread,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, chatRef.path)
            throw e
        }
    }

    suspend fun leaveGroup(chat: ChatThread) {
        val uid = requireUserId()
        val chatRef = db.collection("chats").document(chat.id)
        val remainingParticipants = chat.participantIds.filterNot { it == uid }
        val remainingAdmins = chat.adminIds.filterNot { it == uid }.let {
            if (it.isEmpty() && remainingParticipants.isNotEmpty()) listOf(remainingParticipants.first()) else it
        }
        val remainingUnread = chat.unreadCounts.toMutableMap().apply { remove(uid) }
        try {
            chatRef.update(
                mapOf(
                    "participantIds" to remainingParticipants,
                    "adminIds" to remainingAdmins,
                    "unreadCounts" to remainingUnread,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, chatRef.path)
            throw e
        }
    }

    // --- Messages ---

    fun observeMessages(chatId: String, userId: String): Flow<List<ChatMessage>> = flow {
        val uid = requireUserId()
        val path = "chats/$chatId/messages"
        emitAll(
            db.collection("chats").document(chatId).collection("messages")
                .whereArrayContains("participantIds", userId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(ChatMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.createdAt ?: Timestamp(0, 0) }
                }
                .logFirestoreErrors(OperationType.LIST, path, uid)
        )
    }

    suspend fun sendMessage(
        chat: ChatThread,
        senderName: String,
        type: String,
        text: String,
        mediaPayload: String? = null,
        mediaDurationMs: Long? = null,
        replyToId: String? = null,
        replyPreview: String? = null
    ) {
        val uid = requireUserId()
        val msgId = "msg_${UUID.randomUUID().toString().replace("-", "").take(20)}"
        val chatRef = db.collection("chats").document(chat.id)
        val msgRef = chatRef.collection("messages").document(msgId)

        val msgPayload = mutableMapOf<String, Any?>(
            "chatId" to chat.id,
            "senderId" to uid,
            "senderName" to senderName.take(60).ifBlank { "User" },
            "participantIds" to chat.participantIds,
            "type" to type,
            "text" to text.take(4000),
            "mediaPayload" to mediaPayload,
            "mediaDurationMs" to mediaDurationMs,
            "replyToId" to replyToId,
            "replyPreview" to replyPreview?.take(200),
            "reactions" to emptyMap<String, String>(),
            "readBy" to listOf(uid),
            "isDeleted" to false,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        ).filterValues { it != null }

        val previewText = when (type) {
            "voice" -> "🎤 ভয়েস বার্তা"
            "image" -> if (text.isNotBlank()) "📷 $text" else "📷 ছবি"
            "video" -> if (text.isNotBlank()) "🎥 $text" else "🎥 ভিডিও"
            "call" -> text
            else -> text
        }.take(300)

        val nextUnread = chat.participantIds.associateWith { pid ->
            if (pid == uid) 0L else (chat.unreadCounts[pid] ?: 0L) + 1L
        }

        val chatUpdates = mapOf(
            "lastMessage" to previewText,
            "lastMessageType" to type,
            "lastMessageSenderId" to uid,
            "lastMessageAt" to FieldValue.serverTimestamp(),
            "unreadCounts" to nextUnread,
            "updatedAt" to FieldValue.serverTimestamp()
        )

        try {
            val batch = db.batch()
            batch.set(msgRef, msgPayload)
            batch.update(chatRef, chatUpdates)
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, msgRef.path)
            throw e
        }
    }

    suspend fun markChatRead(chat: ChatThread) {
        val uid = auth.currentUser?.uid ?: return
        if ((chat.unreadCounts[uid] ?: 0L) == 0L) return
        val nextUnread = chat.unreadCounts.toMutableMap().apply { put(uid, 0L) }
        val chatRef = db.collection("chats").document(chat.id)
        try {
            chatRef.update(
                mapOf(
                    "unreadCounts" to nextUnread,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (_: Exception) {
        }
    }

    suspend fun toggleReaction(chatId: String, message: ChatMessage, emoji: String) {
        val uid = requireUserId()
        val msgRef = db.collection("chats").document(chatId).collection("messages").document(message.id)
        val nextReactions = message.reactions.toMutableMap()
        if (nextReactions[uid] == emoji) {
            nextReactions.remove(uid)
        } else {
            nextReactions[uid] = emoji
        }
        try {
            msgRef.update(
                mapOf(
                    "reactions" to nextReactions,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, msgRef.path)
            throw e
        }
    }

    suspend fun softDeleteMessage(chatId: String, messageId: String) {
        requireUserId()
        val msgRef = db.collection("chats").document(chatId).collection("messages").document(messageId)
        try {
            msgRef.update(
                mapOf(
                    "isDeleted" to true,
                    "text" to "",
                    "mediaPayload" to FieldValue.delete(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, msgRef.path)
            throw e
        }
    }

    // --- Statuses / Stories ---

    fun observeActiveStatuses(): Flow<List<StatusStory>> = flow {
        val uid = requireUserId()
        val path = "statuses"
        emitAll(
            db.collection(path)
                .whereEqualTo("isActive", true)
                .snapshots()
                .map { snap ->
                    val now = System.currentTimeMillis()
                    snap.toObjects(StatusStory::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .filter { it.expiresAtEpochMs > now }
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .logFirestoreErrors(OperationType.LIST, path, uid)
        )
    }

    suspend fun postStatus(
        userName: String,
        userAvatar: String?,
        type: String,
        text: String,
        backgroundHex: String = "#0B7A5C",
        imageBase64: String? = null
    ) {
        val uid = requireUserId()
        val statusId = "st_${UUID.randomUUID().toString().replace("-", "").take(20)}"
        val docRef = db.collection("statuses").document(statusId)
        val payload = mutableMapOf<String, Any?>(
            "userId" to uid,
            "userName" to userName.take(60).ifBlank { "User" },
            "userAvatar" to userAvatar?.take(250000),
            "type" to type,
            "text" to text.take(400),
            "backgroundHex" to backgroundHex,
            "imageBase64" to imageBase64,
            "viewerIds" to emptyList<String>(),
            "isActive" to true,
            "expiresAtEpochMs" to (System.currentTimeMillis() + 24L * 60L * 60L * 1000L),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        ).filterValues { it != null }

        try {
            docRef.set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun markStatusViewed(status: StatusStory) {
        val uid = requireUserId()
        if (uid in status.viewerIds || status.userId == uid) return
        val docRef = db.collection("statuses").document(status.id)
        try {
            docRef.update(
                mapOf(
                    "viewerIds" to (status.viewerIds + uid).distinct().take(200),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (_: Exception) {
        }
    }

    suspend fun deleteStatus(statusId: String) {
        requireUserId()
        val docRef = db.collection("statuses").document(statusId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // --- WebRTC Call Signaling & Call History ---

    fun observeCalls(userId: String): Flow<List<CallSession>> = flow {
        val uid = requireUserId()
        val path = "calls"
        emitAll(
            db.collection(path)
                .whereArrayContains("participantIds", userId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(CallSession::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .logFirestoreErrors(OperationType.LIST, path, uid)
        )
    }

    fun observeCall(callId: String): Flow<CallSession?> = flow {
        val uid = requireUserId()
        val docRef = db.collection("calls").document(callId)
        emitAll(
            docRef.snapshots()
                .map { snap ->
                    snap.toObject(CallSession::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .logFirestoreErrors(OperationType.GET, docRef.path, uid)
        )
    }

    suspend fun createCallOffer(
        callId: String,
        callerName: String,
        callerAvatar: String?,
        calleeId: String,
        calleeName: String,
        calleeAvatar: String?,
        callType: String,
        offerSdp: String
    ) {
        val uid = requireUserId()
        val docRef = db.collection("calls").document(callId)
        val payload = mutableMapOf<String, Any?>(
            "callerId" to uid,
            "callerName" to callerName.take(60).ifBlank { "Caller" },
            "callerAvatar" to callerAvatar?.take(250000),
            "calleeId" to calleeId,
            "calleeName" to calleeName.take(60).ifBlank { "Callee" },
            "calleeAvatar" to calleeAvatar?.take(250000),
            "participantIds" to listOf(uid, calleeId),
            "callType" to callType,
            "status" to "ringing",
            "offerSdp" to offerSdp.take(20000),
            "durationSeconds" to 0L,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        ).filterValues { it != null }

        try {
            docRef.set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun answerCall(callId: String, answerSdp: String) {
        requireUserId()
        val docRef = db.collection("calls").document(callId)
        try {
            docRef.update(
                mapOf(
                    "status" to "accepted",
                    "answerSdp" to answerSdp.take(20000),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun updateCallStatus(callId: String, status: String, durationSeconds: Long = 0L) {
        requireUserId()
        val docRef = db.collection("calls").document(callId)
        try {
            docRef.update(
                mapOf(
                    "status" to status,
                    "durationSeconds" to durationSeconds.coerceAtLeast(0L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Terminal-locked updates are harmless on simultaneous hangup
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
        }
    }

    suspend fun addIceCandidate(
        callId: String,
        isCaller: Boolean,
        participantIds: List<String>,
        sdpMid: String,
        sdpMLineIndex: Int,
        candidate: String
    ) {
        val uid = requireUserId()
        val subCol = if (isCaller) "callerCandidates" else "calleeCandidates"
        val candId = "ice_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val docRef = db.collection("calls").document(callId).collection(subCol).document(candId)
        val payload = mapOf(
            "senderId" to uid,
            "participantIds" to participantIds,
            "sdpMid" to sdpMid.take(64),
            "sdpMLineIndex" to sdpMLineIndex.toLong(),
            "candidate" to candidate.take(1024),
            "createdAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
        }
    }

    fun observeIceCandidates(
        callId: String,
        fromCaller: Boolean,
        userId: String
    ): Flow<List<IceCandidateRecord>> = flow {
        val uid = requireUserId()
        val subCol = if (fromCaller) "callerCandidates" else "calleeCandidates"
        val path = "calls/$callId/$subCol"
        emitAll(
            db.collection("calls").document(callId).collection(subCol)
                .whereArrayContains("participantIds", userId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(IceCandidateRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .logFirestoreErrors(OperationType.LIST, path, uid)
        )
    }
}
