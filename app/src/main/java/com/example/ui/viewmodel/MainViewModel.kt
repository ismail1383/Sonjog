package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.AppLanguage
import com.example.core.ThemeMode
import com.example.core.UserPreferences
import com.example.data.model.CallSession
import com.example.data.model.ChatThread
import com.example.data.model.DirectoryUser
import com.example.data.model.StatusStory
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.repository.SongjogRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(
    private val repository: SongjogRepository,
    private val preferences: UserPreferences,
    private val currentUserId: String
) : ViewModel() {

    private fun <T> Flow<T>.toUiStateFlow(label: String): StateFlow<UiState<T>> =
        this.map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing $label", rootError)
                emit(UiState.Error(rootError.message ?: "Failed to load $label"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading
            )

    val myProfile: StateFlow<UserProfile?> = repository.observeMyProfile(currentUserId)
        .catch { error ->
            if (error is CancellationException) throw error
            Log.w(TAG, "Profile observer error", error)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val directoryUsers: StateFlow<UiState<List<DirectoryUser>>> = repository.observeDirectoryUsers()
        .map { users -> users.filterNot { it.userId == currentUserId } }
        .toUiStateFlow("directoryUsers")

    val chats: StateFlow<UiState<List<ChatThread>>> = repository.observeChats(currentUserId)
        .toUiStateFlow("chats")

    val statuses: StateFlow<UiState<List<StatusStory>>> = repository.observeActiveStatuses()
        .toUiStateFlow("statuses")

    val calls: StateFlow<UiState<List<CallSession>>> = repository.observeCalls(currentUserId)
        .toUiStateFlow("calls")

    val incomingRingingCall: StateFlow<CallSession?> = repository.observeCalls(currentUserId)
        .map { list ->
            list.firstOrNull { it.calleeId == currentUserId && it.status == "ringing" }
        }
        .catch { error ->
            if (error is CancellationException) throw error
            Log.w(TAG, "Incoming call observer error", error)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    init {
        viewModelScope.launch {
            Firebase.auth.currentUser?.let { user ->
                runCatching { repository.ensureUserSignedInProfile(user) }
                    .onFailure { Log.w(TAG, "Failed ensuring signed-in profile", it) }
            }
        }
    }

    fun clearError() {
        _actionError.value = null
    }

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch {
            preferences.setLanguage(language)
            val profile = myProfile.value
            if (profile != null) {
                runCatching {
                    repository.updateProfile(
                        displayName = profile.displayName,
                        about = profile.about,
                        language = language.code
                    )
                }
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            preferences.setThemeMode(mode)
        }
    }

    fun updateProfile(
        displayName: String,
        about: String,
        avatarBase64: String? = null,
        discoverable: Boolean? = null
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            runCatching {
                repository.updateProfile(
                    displayName = displayName,
                    about = about,
                    avatarBase64 = avatarBase64,
                    discoverable = discoverable
                )
            }.onFailure {
                _actionError.value = it.localizedMessage
            }
            _isBusy.value = false
        }
    }

    fun openDirectChat(target: DirectoryUser, onChatReady: (String) -> Unit) {
        viewModelScope.launch {
            _isBusy.value = true
            val me = myProfile.value ?: UserProfile(
                userId = currentUserId,
                displayName = Firebase.auth.currentUser?.displayName ?: "Me"
            )
            runCatching {
                repository.getOrCreateDirectChat(me, target)
            }.onSuccess { chatId ->
                onChatReady(chatId)
            }.onFailure {
                _actionError.value = it.localizedMessage
            }
            _isBusy.value = false
        }
    }

    fun createGroup(
        groupName: String,
        members: List<DirectoryUser>,
        iconBase64: String? = null,
        onGroupCreated: (String) -> Unit
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val me = myProfile.value ?: UserProfile(
                userId = currentUserId,
                displayName = Firebase.auth.currentUser?.displayName ?: "Me"
            )
            runCatching {
                repository.createGroupChat(me, groupName, members, iconBase64)
            }.onSuccess { chatId ->
                onGroupCreated(chatId)
            }.onFailure {
                _actionError.value = it.localizedMessage
            }
            _isBusy.value = false
        }
    }

    fun postTextStatus(text: String, backgroundHex: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val me = myProfile.value
            runCatching {
                repository.postStatus(
                    userName = me?.displayName ?: Firebase.auth.currentUser?.displayName ?: "User",
                    userAvatar = me?.avatarBase64 ?: me?.avatarUrl,
                    type = "text",
                    text = text,
                    backgroundHex = backgroundHex
                )
            }.onFailure {
                _actionError.value = it.localizedMessage
            }
            _isBusy.value = false
        }
    }

    fun postImageStatus(caption: String, imageBase64: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val me = myProfile.value
            runCatching {
                repository.postStatus(
                    userName = me?.displayName ?: Firebase.auth.currentUser?.displayName ?: "User",
                    userAvatar = me?.avatarBase64 ?: me?.avatarUrl,
                    type = "image",
                    text = caption,
                    backgroundHex = "#0D1512",
                    imageBase64 = imageBase64
                )
            }.onFailure {
                _actionError.value = it.localizedMessage
            }
            _isBusy.value = false
        }
    }

    fun markStatusViewed(status: StatusStory) {
        viewModelScope.launch {
            repository.markStatusViewed(status)
        }
    }

    fun deleteStatus(statusId: String) {
        viewModelScope.launch {
            runCatching { repository.deleteStatus(statusId) }
                .onFailure { _actionError.value = it.localizedMessage }
        }
    }

    fun declineIncomingCall(callId: String) {
        viewModelScope.launch {
            repository.updateCallStatus(callId, "declined", 0L)
        }
    }

    suspend fun goOfflineBeforeSignOut() {
        repository.setOnlineStatus(false)
    }

    private companion object {
        const val TAG = "MainViewModel"
    }
}
