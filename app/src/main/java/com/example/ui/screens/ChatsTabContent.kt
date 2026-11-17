package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddComment
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.AppLanguage
import com.example.core.LocalAppStrings
import com.example.core.TimeFormatter
import com.example.data.model.ChatThread
import com.example.data.model.DirectoryUser
import com.example.data.model.UiState
import com.example.ui.components.ErrorStateCard
import com.example.ui.components.LoadingStateView
import com.example.ui.components.UserAvatar

private enum class ChatFilter { ALL, UNREAD, GROUPS }

@Composable
fun ChatsTabContent(
    chatsState: UiState<List<ChatThread>>,
    directoryUsers: List<DirectoryUser>,
    currentUserId: String,
    language: AppLanguage,
    selectedChatId: String?,
    onOpenChat: (String) -> Unit,
    onNewChatClick: () -> Unit,
    onNewGroupClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(ChatFilter.ALL) }

    val directoryMap = remember(directoryUsers) {
        directoryUsers.associateBy { it.userId }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Search Bar
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(strings.searchChatsPlaceholder, style = MaterialTheme.typography.bodyMedium) },
            leadingIcon = {
                Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Rounded.Close, contentDescription = strings.cancel)
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag("chats_search_input")
        )

        // Filter Chips
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            item {
                FilterChip(
                    selected = filter == ChatFilter.ALL,
                    onClick = { filter = ChatFilter.ALL },
                    label = { Text(strings.filterAll) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
            item {
                FilterChip(
                    selected = filter == ChatFilter.UNREAD,
                    onClick = { filter = ChatFilter.UNREAD },
                    label = { Text(strings.filterUnread) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
            item {
                FilterChip(
                    selected = filter == ChatFilter.GROUPS,
                    onClick = { filter = ChatFilter.GROUPS },
                    label = { Text(strings.filterGroups) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }

        when (chatsState) {
            UiState.Loading -> LoadingStateView(modifier = Modifier.weight(1f))
            is UiState.Error -> ErrorStateCard(message = chatsState.message, modifier = Modifier.weight(1f))
            is UiState.Success -> {
                val filteredChats = remember(chatsState.data, query, filter, currentUserId, directoryMap) {
                    chatsState.data.filter { chat ->
                        val unread = chat.unreadCounts[currentUserId] ?: 0L
                        val passesFilter = when (filter) {
                            ChatFilter.ALL -> true
                            ChatFilter.UNREAD -> unread > 0L
                            ChatFilter.GROUPS -> chat.isGroup
                        }
                        if (!passesFilter) return@filter false
                        if (query.isBlank()) return@filter true
                        val title = resolveChatTitle(chat, currentUserId, directoryMap)
                        title.contains(query, ignoreCase = true) ||
                            chat.lastMessage.contains(query, ignoreCase = true)
                    }
                }

                if (filteredChats.isEmpty()) {
                    EmptyChatsState(
                        onNewChatClick = onNewChatClick,
                        onNewGroupClick = onNewGroupClick,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(filteredChats, key = { it.id }) { chat ->
                            val peerId = chat.participantIds.firstOrNull { it != currentUserId }
                            val peerUser = peerId?.let { directoryMap[it] }
                            val title = resolveChatTitle(chat, currentUserId, directoryMap)
                            val unreadCount = chat.unreadCounts[currentUserId] ?: 0L
                            val isSelected = selectedChatId == chat.id

                            ChatThreadCard(
                                chat = chat,
                                title = title,
                                peerUser = peerUser,
                                unreadCount = unreadCount,
                                language = language,
                                isSelected = isSelected,
                                onClick = { onOpenChat(chat.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatThreadCard(
    chat: ChatThread,
    title: String,
    peerUser: DirectoryUser?,
    unreadCount: Long,
    language: AppLanguage,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val strings = LocalAppStrings.current
    val avatarUrl = if (chat.isGroup) null else peerUser?.avatarUrl
    val avatarBase64 = if (chat.isGroup) chat.groupIconBase64 else peerUser?.avatarBase64
    val isOnline = !chat.isGroup && (peerUser?.isOnline == true)
    val timeText = TimeFormatter.formatChatListDate(chat.lastMessageAt, language.code, strings.justNow)

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        else MaterialTheme.colorScheme.surface,
        tonalElevation = if (isSelected) 3.dp else 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .minimumInteractiveComponentSize()
            .testTag("chat_item_${chat.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UserAvatar(
                name = title,
                avatarUrl = avatarUrl,
                avatarBase64 = avatarBase64,
                size = 52.dp,
                isOnline = isOnline,
                isGroup = chat.isGroup
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (unreadCount > 0) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        val typeIcon = when (chat.lastMessageType) {
                            "voice" -> Icons.Rounded.Mic
                            "image" -> Icons.Rounded.Image
                            "video" -> Icons.Rounded.Videocam
                            "call" -> Icons.Rounded.Call
                            else -> null
                        }
                        if (typeIcon != null) {
                            Icon(
                                imageVector = typeIcon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                        Text(
                            text = chat.lastMessage.ifBlank { strings.startNewChat },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (unreadCount > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = unreadCount.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyChatsState(
    onNewChatClick: () -> Unit,
    onNewGroupClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_empty_chats_1794832243754),
            contentDescription = strings.noChatsTitle,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(180.dp, 136.dp)
                .clip(RoundedCornerShape(24.dp))
        )
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = strings.noChatsTitle,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = strings.noChatsSubtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onNewChatClick,
                modifier = Modifier.testTag("empty_new_chat_button")
            ) {
                Icon(Icons.Rounded.AddComment, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(strings.startNewChat)
            }
            OutlinedButton(
                onClick = onNewGroupClick,
                modifier = Modifier.testTag("empty_new_group_button")
            ) {
                Icon(Icons.Rounded.GroupAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(strings.newGroup)
            }
        }
    }
}

fun resolveChatTitle(
    chat: ChatThread,
    currentUserId: String,
    directoryMap: Map<String, DirectoryUser>
): String {
    if (chat.isGroup) {
        return chat.groupName?.takeIf { it.isNotBlank() } ?: "Group"
    }
    val peerId = chat.participantIds.firstOrNull { it != currentUserId }
    return peerId?.let { directoryMap[it]?.displayName ?: chat.participantNames[it] }
        ?: chat.participantNames.entries.firstOrNull { it.key != currentUserId }?.value
        ?: "Chat"
}
