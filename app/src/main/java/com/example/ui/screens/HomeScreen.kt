package com.example.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.AddComment
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.core.AppLanguage
import com.example.core.LocalAppStrings
import com.example.core.ThemeMode
import com.example.data.model.DirectoryUser
import com.example.data.model.UiState
import com.example.data.repository.SongjogRepository
import com.example.ui.auth.LanguageTogglePill
import com.example.ui.viewmodel.ChatRoomViewModel
import com.example.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    mainViewModel: MainViewModel,
    repository: SongjogRepository,
    currentUserId: String,
    widthSizeClass: WindowWidthSizeClass,
    currentLanguage: AppLanguage,
    currentThemeMode: ThemeMode,
    onOpenChatRoute: (String) -> Unit,
    onNewChatClick: () -> Unit,
    onNewGroupClick: () -> Unit,
    onOpenGroupInfo: (String) -> Unit,
    onOpenStatusViewer: (String) -> Unit,
    onStartCall: (DirectoryUser, Boolean) -> Unit,
    onSignOut: () -> Unit
) {
    val strings = LocalAppStrings.current
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var expandedSelectedChatId by rememberSaveable { mutableStateOf<String?>(null) }

    val myProfile by mainViewModel.myProfile.collectAsStateWithLifecycle()
    val directoryState by mainViewModel.directoryUsers.collectAsStateWithLifecycle()
    val chatsState by mainViewModel.chats.collectAsStateWithLifecycle()
    val statusesState by mainViewModel.statuses.collectAsStateWithLifecycle()
    val callsState by mainViewModel.calls.collectAsStateWithLifecycle()

    val directoryUsers = (directoryState as? UiState.Success)?.data ?: emptyList()
    val useRail = widthSizeClass != WindowWidthSizeClass.Compact
    val isExpanded = widthSizeClass == WindowWidthSizeClass.Expanded

    val tabItems = listOf(
        Triple(strings.tabChats, Icons.Rounded.ChatBubble, Icons.Outlined.ChatBubbleOutline),
        Triple(strings.tabStatus, Icons.Rounded.AutoAwesome, Icons.Outlined.AutoAwesome),
        Triple(strings.tabCalls, Icons.Rounded.Call, Icons.Outlined.Call),
        Triple(strings.tabSettings, Icons.Rounded.Settings, Icons.Outlined.Settings)
    )

    val handleOpenChat: (String) -> Unit = { chatId ->
        if (isExpanded) {
            expandedSelectedChatId = chatId
        } else {
            onOpenChatRoute(chatId)
        }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        if (useRail) {
            NavigationRail(
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Spacer(modifier = Modifier.weight(0.1f))
                tabItems.forEachIndexed { idx, (label, activeIcon, inactiveIcon) ->
                    NavigationRailItem(
                        selected = selectedTab == idx,
                        onClick = { selectedTab = idx },
                        icon = {
                            Icon(
                                imageVector = if (selectedTab == idx) activeIcon else inactiveIcon,
                                contentDescription = label
                            )
                        },
                        label = { Text(label) }
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
            }
            VerticalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Forum,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = when (selectedTab) {
                                    0 -> strings.appName
                                    1 -> strings.tabStatus
                                    2 -> strings.tabCalls
                                    else -> strings.tabSettings
                                },
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    actions = {
                        LanguageTogglePill(
                            currentLanguage = currentLanguage,
                            onToggle = {
                                mainViewModel.setLanguage(
                                    if (currentLanguage == AppLanguage.BENGALI) AppLanguage.ENGLISH
                                    else AppLanguage.BENGALI
                                )
                            },
                            modifier = Modifier.padding(end = 10.dp)
                        )
                    }
                )
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        tabItems.forEachIndexed { idx, (label, activeIcon, inactiveIcon) ->
                            NavigationBarItem(
                                selected = selectedTab == idx,
                                onClick = { selectedTab = idx },
                                icon = {
                                    Icon(
                                        imageVector = if (selectedTab == idx) activeIcon else inactiveIcon,
                                        contentDescription = label
                                    )
                                },
                                label = { Text(label) },
                                modifier = Modifier.testTag("nav_tab_$idx")
                            )
                        }
                    }
                }
            },
            floatingActionButton = {
                if (selectedTab == 0) {
                    FloatingActionButton(
                        onClick = onNewChatClick,
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.testTag("new_chat_fab")
                    ) {
                        Icon(Icons.Rounded.AddComment, contentDescription = strings.startNewChat)
                    }
                }
            },
            modifier = Modifier.weight(1f)
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (selectedTab) {
                    0 -> {
                        if (isExpanded) {
                            Row(modifier = Modifier.fillMaxSize()) {
                                ChatsTabContent(
                                    chatsState = chatsState,
                                    directoryUsers = directoryUsers,
                                    currentUserId = currentUserId,
                                    language = currentLanguage,
                                    selectedChatId = expandedSelectedChatId,
                                    onOpenChat = handleOpenChat,
                                    onNewChatClick = onNewChatClick,
                                    onNewGroupClick = onNewGroupClick,
                                    modifier = Modifier.weight(0.42f).fillMaxHeight()
                                )
                                VerticalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                                Box(modifier = Modifier.weight(0.58f).fillMaxHeight()) {
                                    val chatId = expandedSelectedChatId
                                    if (chatId == null) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = strings.selectChatPrompt,
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    } else {
                                        val roomVm: ChatRoomViewModel = viewModel(
                                            key = "${currentUserId}_$chatId",
                                            factory = viewModelFactory {
                                                initializer {
                                                    ChatRoomViewModel(repository, chatId, currentUserId)
                                                }
                                            }
                                        )
                                        ChatRoomScreen(
                                            viewModel = roomVm,
                                            myProfile = myProfile,
                                            directoryUsers = directoryUsers,
                                            currentUserId = currentUserId,
                                            language = currentLanguage,
                                            showBackButton = false,
                                            onBack = { expandedSelectedChatId = null },
                                            onOpenGroupInfo = onOpenGroupInfo,
                                            onStartCall = onStartCall
                                        )
                                    }
                                }
                            }
                        } else {
                            ChatsTabContent(
                                chatsState = chatsState,
                                directoryUsers = directoryUsers,
                                currentUserId = currentUserId,
                                language = currentLanguage,
                                selectedChatId = null,
                                onOpenChat = handleOpenChat,
                                onNewChatClick = onNewChatClick,
                                onNewGroupClick = onNewGroupClick
                            )
                        }
                    }
                    1 -> StatusTabContent(
                        statusesState = statusesState,
                        myProfile = myProfile,
                        currentUserId = currentUserId,
                        language = currentLanguage,
                        onPostTextStatus = mainViewModel::postTextStatus,
                        onPostImageStatus = mainViewModel::postImageStatus,
                        onOpenStatusViewer = onOpenStatusViewer
                    )
                    2 -> CallsTabContent(
                        callsState = callsState,
                        directoryUsers = directoryUsers,
                        currentUserId = currentUserId,
                        language = currentLanguage,
                        onStartCall = onStartCall
                    )
                    else -> SettingsTabContent(
                        profile = myProfile,
                        currentLanguage = currentLanguage,
                        currentThemeMode = currentThemeMode,
                        onLanguageSelected = mainViewModel::setLanguage,
                        onThemeSelected = mainViewModel::setThemeMode,
                        onUpdateProfile = { name, about, avatarB64, discoverable ->
                            mainViewModel.updateProfile(name, about, avatarB64, discoverable)
                        },
                        onSignOut = onSignOut
                    )
                }
            }
        }
    }
}
