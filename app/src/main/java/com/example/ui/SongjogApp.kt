package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.core.AppLanguage
import com.example.core.LocalAppStrings
import com.example.core.ThemeMode
import com.example.core.UserPreferences
import com.example.data.model.CallSession
import com.example.data.model.DirectoryUser
import com.example.data.model.UiState
import com.example.data.repository.SongjogRepository
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.signOutUser
import com.example.ui.screens.ActiveCallScreen
import com.example.ui.screens.ChatRoomScreen
import com.example.ui.screens.CreateGroupScreen
import com.example.ui.screens.GroupInfoScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.IncomingCallBanner
import com.example.ui.screens.NewChatScreen
import com.example.ui.screens.StatusViewerScreen
import com.example.ui.viewmodel.ChatRoomViewModel
import com.example.ui.viewmodel.MainViewModel
import com.example.webrtc.ActiveCallPhase
import com.example.webrtc.WebRtcCallManager
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

@Serializable
data class ChatRoomRoute(val chatId: String)

@Serializable
data object NewChatRoute

@Serializable
data object CreateGroupRoute

@Serializable
data class GroupInfoRoute(val chatId: String)

@Serializable
data class StatusViewerRoute(val ownerUserId: String)

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        trySend(firebaseAuth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

@Composable
fun SongjogApp(
    preferences: UserPreferences,
    currentLanguage: AppLanguage,
    currentThemeMode: ThemeMode,
    widthSizeClass: WindowWidthSizeClass
) {
    val auth = remember { Firebase.auth }
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val scope = rememberCoroutineScope()

    val user = currentUser
    if (user == null) {
        AuthScreen(
            currentLanguage = currentLanguage,
            onLanguageSelected = { lang -> scope.launch { preferences.setLanguage(lang) } },
            onAuthSuccess = {}
        )
    } else {
        key(user.uid) {
            AuthenticatedSongjogContent(
                currentUserId = user.uid,
                preferences = preferences,
                currentLanguage = currentLanguage,
                currentThemeMode = currentThemeMode,
                widthSizeClass = widthSizeClass
            )
        }
    }
}

@Composable
private fun AuthenticatedSongjogContent(
    currentUserId: String,
    preferences: UserPreferences,
    currentLanguage: AppLanguage,
    currentThemeMode: ThemeMode,
    widthSizeClass: WindowWidthSizeClass
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val repository = remember(context) { SongjogRepository(context.applicationContext) }
    val mainViewModel: MainViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                MainViewModel(repository, preferences, currentUserId)
            }
        }
    )

    val callManager = remember(context, repository) {
        WebRtcCallManager(context.applicationContext, repository, scope)
    }
    DisposableEffect(callManager) {
        onDispose { callManager.release() }
    }

    val myProfile by mainViewModel.myProfile.collectAsStateWithLifecycle()
    val directoryState by mainViewModel.directoryUsers.collectAsStateWithLifecycle()
    val statusesState by mainViewModel.statuses.collectAsStateWithLifecycle()
    val incomingCall by mainViewModel.incomingRingingCall.collectAsStateWithLifecycle()
    val activeCallState by callManager.callState.collectAsStateWithLifecycle()
    val actionError by mainViewModel.actionError.collectAsStateWithLifecycle()

    LaunchedEffect(actionError) {
        actionError?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            mainViewModel.clearError()
        }
    }

    var pendingOutgoingCall by remember { mutableStateOf<Pair<DirectoryUser, Boolean>?>(null) }
    var pendingIncomingCall by remember { mutableStateOf<CallSession?>(null) }

    val callPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val micGranted = results[Manifest.permission.RECORD_AUDIO] == true
        val camGranted = results[Manifest.permission.CAMERA] == true

        pendingOutgoingCall?.let { (peer, isVideo) ->
            pendingOutgoingCall = null
            if (micGranted && (!isVideo || camGranted)) {
                callManager.startOutgoingCall(
                    currentUserId = currentUserId,
                    currentUserName = myProfile?.displayName ?: "User",
                    currentUserAvatar = myProfile?.avatarUrl,
                    peerId = peer.userId,
                    peerName = peer.displayName,
                    peerAvatar = peer.avatarUrl,
                    isVideo = isVideo
                )
            } else {
                Toast.makeText(context, strings.callPermissionsRequired, Toast.LENGTH_SHORT).show()
            }
        }

        pendingIncomingCall?.let { session ->
            pendingIncomingCall = null
            val needsCam = session.callType == "video"
            if (micGranted && (!needsCam || camGranted)) {
                callManager.acceptIncomingCall(session, currentUserId)
            } else {
                Toast.makeText(context, strings.callPermissionsRequired, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val startCallWithPermissions: (DirectoryUser, Boolean) -> Unit = { peer, isVideo ->
        val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        val hasCam = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (hasMic && (!isVideo || hasCam)) {
            callManager.startOutgoingCall(
                currentUserId = currentUserId,
                currentUserName = myProfile?.displayName ?: "User",
                currentUserAvatar = myProfile?.avatarUrl,
                peerId = peer.userId,
                peerName = peer.displayName,
                peerAvatar = peer.avatarUrl,
                isVideo = isVideo
            )
        } else {
            pendingOutgoingCall = peer to isVideo
            val perms = if (isVideo) {
                arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
            } else {
                arrayOf(Manifest.permission.RECORD_AUDIO)
            }
            callPermissionsLauncher.launch(perms)
        }
    }

    val acceptIncomingWithPermissions: (CallSession) -> Unit = { session ->
        val needsCam = session.callType == "video"
        val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        val hasCam = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (hasMic && (!needsCam || hasCam)) {
            callManager.acceptIncomingCall(session, currentUserId)
        } else {
            pendingIncomingCall = session
            val perms = if (needsCam) {
                arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
            } else {
                arrayOf(Manifest.permission.RECORD_AUDIO)
            }
            callPermissionsLauncher.launch(perms)
        }
    }

    val directoryUsers = (directoryState as? UiState.Success)?.data ?: emptyList()

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = HomeRoute
        ) {
            composable<HomeRoute> {
                HomeScreen(
                    mainViewModel = mainViewModel,
                    repository = repository,
                    currentUserId = currentUserId,
                    widthSizeClass = widthSizeClass,
                    currentLanguage = currentLanguage,
                    currentThemeMode = currentThemeMode,
                    onOpenChatRoute = { chatId -> navController.navigate(ChatRoomRoute(chatId)) },
                    onNewChatClick = { navController.navigate(NewChatRoute) },
                    onNewGroupClick = { navController.navigate(CreateGroupRoute) },
                    onOpenGroupInfo = { chatId -> navController.navigate(GroupInfoRoute(chatId)) },
                    onOpenStatusViewer = { ownerId -> navController.navigate(StatusViewerRoute(ownerId)) },
                    onStartCall = startCallWithPermissions,
                    onSignOut = {
                        callManager.cleanupSession()
                        signOutUser(
                            credentialManager = credentialManager,
                            scope = scope,
                            onBeforeSignOut = { mainViewModel.goOfflineBeforeSignOut() }
                        )
                    }
                )
            }

            composable<ChatRoomRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<ChatRoomRoute>()
                val roomVm: ChatRoomViewModel = viewModel(
                    key = "${currentUserId}_${route.chatId}",
                    factory = viewModelFactory {
                        initializer {
                            ChatRoomViewModel(repository, route.chatId, currentUserId)
                        }
                    }
                )
                ChatRoomScreen(
                    viewModel = roomVm,
                    myProfile = myProfile,
                    directoryUsers = directoryUsers,
                    currentUserId = currentUserId,
                    language = currentLanguage,
                    showBackButton = true,
                    onBack = { navController.popBackStack() },
                    onOpenGroupInfo = { chatId -> navController.navigate(GroupInfoRoute(chatId)) },
                    onStartCall = startCallWithPermissions
                )
            }

            composable<NewChatRoute> {
                NewChatScreen(
                    directoryState = directoryState,
                    onSelectUser = { target ->
                        mainViewModel.openDirectChat(target) { chatId ->
                            navController.navigate(ChatRoomRoute(chatId)) {
                                popUpTo<NewChatRoute> { inclusive = true }
                            }
                        }
                    },
                    onCreateGroupClick = {
                        navController.navigate(CreateGroupRoute) {
                            popUpTo<NewChatRoute> { inclusive = true }
                        }
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            composable<CreateGroupRoute> {
                CreateGroupScreen(
                    directoryUsers = directoryUsers,
                    onCreateGroup = { name, members, iconBase64 ->
                        mainViewModel.createGroup(name, members, iconBase64) { chatId ->
                            navController.navigate(ChatRoomRoute(chatId)) {
                                popUpTo<CreateGroupRoute> { inclusive = true }
                            }
                        }
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            composable<GroupInfoRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<GroupInfoRoute>()
                val roomVm: ChatRoomViewModel = viewModel(
                    key = "${currentUserId}_info_${route.chatId}",
                    factory = viewModelFactory {
                        initializer {
                            ChatRoomViewModel(repository, route.chatId, currentUserId)
                        }
                    }
                )
                val chat by roomVm.chatThread.collectAsStateWithLifecycle()
                GroupInfoScreen(
                    chat = chat,
                    directoryUsers = directoryUsers,
                    currentUserId = currentUserId,
                    onRenameGroup = roomVm::renameGroup,
                    onAddMembers = roomVm::addMembers,
                    onLeaveGroup = {
                        roomVm.leaveGroup {
                            navController.popBackStack<HomeRoute>(inclusive = false)
                        }
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            composable<StatusViewerRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<StatusViewerRoute>()
                val allStatuses = (statusesState as? UiState.Success)?.data ?: emptyList()
                val ownerStatuses = remember(allStatuses, route.ownerUserId) {
                    allStatuses.filter { it.userId == route.ownerUserId }
                }
                StatusViewerScreen(
                    statuses = ownerStatuses,
                    currentUserId = currentUserId,
                    language = currentLanguage,
                    onMarkViewed = mainViewModel::markStatusViewed,
                    onDeleteStatus = mainViewModel::deleteStatus,
                    onBack = { navController.popBackStack() }
                )
            }
        }

        // Incoming Call Overlay Banner
        AnimatedVisibility(
            visible = incomingCall != null && activeCallState.phase == ActiveCallPhase.IDLE,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .safeDrawingPadding()
        ) {
            incomingCall?.let { call ->
                IncomingCallBanner(
                    call = call,
                    onAccept = { acceptIncomingWithPermissions(call) },
                    onDecline = { mainViewModel.declineIncomingCall(call.id) }
                )
            }
        }

        // Full-Screen Active Call Overlay
        if (activeCallState.phase != ActiveCallPhase.IDLE) {
            ActiveCallScreen(
                callState = activeCallState,
                callManager = callManager
            )
        }
    }
}
