package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.ChatMessage
import com.example.data.model.ChatThread
import com.example.data.model.DirectoryUser
import com.example.data.model.UiState
import com.example.data.repository.SongjogRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChatRoomViewModel(
    private val repository: SongjogRepository,
    private val chatId: String,
    private val currentUserId: String
) : ViewModel() {

    val chatThread: StateFlow<ChatThread?> = repository.observeChat(chatId)
        .catch { error ->
            if (error is CancellationException) throw error
            Log.w(TAG, "Error observing chat $chatId", error)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val messagesState: StateFlow<UiState<List<ChatMessage>>> =
        repository.observeMessages(chatId, currentUserId)
            .map<List<ChatMessage>, UiState<List<ChatMessage>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing messages", rootError)
                emit(UiState.Error(rootError.message ?: "Failed to load messages"))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    private val _replyingTo = MutableStateFlow<ChatMessage?>(null)
    val replyingTo: StateFlow<ChatMessage?> = _replyingTo.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun setReplyTarget(message: ChatMessage?) {
        _replyingTo.value = message
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun markRead() {
        val chat = chatThread.value ?: return
        viewModelScope.launch {
            repository.markChatRead(chat)
        }
    }

    fun sendText(senderName: String, text: String) {
        val trimmed = text.trim()
        val chat = chatThread.value ?: return
        if (trimmed.isEmpty()) return
        val reply = _replyingTo.value
        _replyingTo.value = null

        viewModelScope.launch {
            runCatching {
                repository.sendMessage(
                    chat = chat,
                    senderName = senderName,
                    type = "text",
                    text = trimmed,
                    replyToId = reply?.id,
                    replyPreview = reply?.let {
                        if (it.text.isNotBlank()) it.text else it.type
                    }
                )
            }.onFailure {
                _errorMessage.value = it.localizedMessage
            }
        }
    }

    fun sendVoiceNote(senderName: String, audioBase64: String, durationMs: Long) {
        val chat = chatThread.value ?: return
        val reply = _replyingTo.value
        _replyingTo.value = null
        viewModelScope.launch {
            runCatching {
                repository.sendMessage(
                    chat = chat,
                    senderName = senderName,
                    type = "voice",
                    text = "",
                    mediaPayload = audioBase64,
                    mediaDurationMs = durationMs,
                    replyToId = reply?.id,
                    replyPreview = reply?.text
                )
            }.onFailure {
                _errorMessage.value = it.localizedMessage
            }
        }
    }

    fun sendImage(senderName: String, caption: String, imageBase64: String) {
        val chat = chatThread.value ?: return
        val reply = _replyingTo.value
        _replyingTo.value = null
        viewModelScope.launch {
            runCatching {
                repository.sendMessage(
                    chat = chat,
                    senderName = senderName,
                    type = "image",
                    text = caption.trim(),
                    mediaPayload = imageBase64,
                    replyToId = reply?.id,
                    replyPreview = reply?.text
                )
            }.onFailure {
                _errorMessage.value = it.localizedMessage
            }
        }
    }

    fun sendVideoPreview(senderName: String, caption: String, thumbnailBase64: String?, durationMs: Long) {
        val chat = chatThread.value ?: return
        val reply = _replyingTo.value
        _replyingTo.value = null
        viewModelScope.launch {
            runCatching {
                repository.sendMessage(
                    chat = chat,
                    senderName = senderName,
                    type = "video",
                    text = caption.trim(),
                    mediaPayload = thumbnailBase64,
                    mediaDurationMs = durationMs,
                    replyToId = reply?.id,
                    replyPreview = reply?.text
                )
            }.onFailure {
                _errorMessage.value = it.localizedMessage
            }
        }
    }

    fun toggleReaction(message: ChatMessage, emoji: String) {
        viewModelScope.launch {
            runCatching { repository.toggleReaction(chatId, message, emoji) }
                .onFailure { _errorMessage.value = it.localizedMessage }
        }
    }

    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            runCatching { repository.softDeleteMessage(chatId, messageId) }
                .onFailure { _errorMessage.value = it.localizedMessage }
        }
    }

    fun renameGroup(newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            runCatching { repository.updateGroupInfo(chatId, newName) }
                .onFailure { _errorMessage.value = it.localizedMessage }
        }
    }

    fun addMembers(members: List<DirectoryUser>) {
        val chat = chatThread.value ?: return
        viewModelScope.launch {
            runCatching { repository.addGroupMembers(chat, members) }
                .onFailure { _errorMessage.value = it.localizedMessage }
        }
    }

    fun leaveGroup(onLeft: () -> Unit) {
        val chat = chatThread.value ?: return
        viewModelScope.launch {
            runCatching { repository.leaveGroup(chat) }
                .onSuccess { onLeft() }
                .onFailure { _errorMessage.value = it.localizedMessage }
        }
    }

    private companion object {
        const val TAG = "ChatRoomVM"
    }
}
