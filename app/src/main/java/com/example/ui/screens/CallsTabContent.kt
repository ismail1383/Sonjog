package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallMade
import androidx.compose.material.icons.automirrored.rounded.CallMissed
import androidx.compose.material.icons.automirrored.rounded.CallReceived
import androidx.compose.material.icons.rounded.AddIcCall
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.core.AppLanguage
import com.example.core.LocalAppStrings
import com.example.core.TimeFormatter
import com.example.data.model.CallSession
import com.example.data.model.DirectoryUser
import com.example.data.model.UiState
import com.example.ui.components.ErrorStateCard
import com.example.ui.components.LoadingStateView
import com.example.ui.components.UserAvatar
import com.example.ui.theme.OnlineGreen

@Composable
fun CallsTabContent(
    callsState: UiState<List<CallSession>>,
    directoryUsers: List<DirectoryUser>,
    currentUserId: String,
    language: AppLanguage,
    onStartCall: (peer: DirectoryUser, isVideo: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    var showNewCallPicker by remember { mutableStateOf(false) }

    val directoryMap = remember(directoryUsers) { directoryUsers.associateBy { it.userId } }

    Column(modifier = modifier.fillMaxSize()) {
        when (callsState) {
            UiState.Loading -> LoadingStateView()
            is UiState.Error -> ErrorStateCard(message = callsState.message)
            is UiState.Success -> {
                val calls = callsState.data
                if (calls.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PhoneInTalk,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(52.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = strings.noCallsYet,
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = strings.noCallsSubtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        if (directoryUsers.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(18.dp))
                            Button(
                                onClick = { showNewCallPicker = true },
                                modifier = Modifier.testTag("start_new_call_button")
                            ) {
                                Icon(Icons.Rounded.AddIcCall, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(strings.audioCall)
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(calls, key = { it.id }) { call ->
                            val isOutgoing = call.callerId == currentUserId
                            val peerId = if (isOutgoing) call.calleeId else call.callerId
                            val peerName = if (isOutgoing) call.calleeName else call.callerName
                            val peerAvatar = if (isOutgoing) call.calleeAvatar else call.callerAvatar
                            val peerUser = directoryMap[peerId] ?: DirectoryUser(
                                userId = peerId,
                                displayName = peerName,
                                avatarUrl = peerAvatar
                            )
                            val isMissed = !isOutgoing && call.status in listOf("missed", "declined", "ringing")
                            val isVideo = call.callType == "video"

                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surface,
                                tonalElevation = 1.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    UserAvatar(
                                        name = peerName,
                                        avatarUrl = peerAvatar,
                                        size = 48.dp,
                                        isOnline = peerUser.isOnline
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = peerName,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = if (isMissed) MaterialTheme.colorScheme.error
                                            else MaterialTheme.colorScheme.onSurface
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = when {
                                                    isMissed -> Icons.AutoMirrored.Rounded.CallMissed
                                                    isOutgoing -> Icons.AutoMirrored.Rounded.CallMade
                                                    else -> Icons.AutoMirrored.Rounded.CallReceived
                                                },
                                                contentDescription = null,
                                                tint = if (isMissed) MaterialTheme.colorScheme.error else OnlineGreen,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            val label = buildString {
                                                append(
                                                    when {
                                                        isMissed -> strings.callMissed
                                                        isOutgoing -> strings.callOutgoing
                                                        else -> strings.callIncoming
                                                    }
                                                )
                                                if (call.durationSeconds > 0) {
                                                    append(" • ${TimeFormatter.formatSeconds(call.durationSeconds)}")
                                                }
                                                val time = TimeFormatter.formatChatListDate(
                                                    call.createdAt,
                                                    language.code,
                                                    strings.justNow
                                                )
                                                if (time.isNotBlank()) append(" • $time")
                                            }
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    IconButton(
                                        onClick = { onStartCall(peerUser, false) },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Call,
                                            contentDescription = strings.audioCall,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    IconButton(
                                        onClick = { onStartCall(peerUser, true) },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Videocam,
                                            contentDescription = strings.videoCall,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNewCallPicker) {
        AlertDialog(
            onDismissRequest = { showNewCallPicker = false },
            title = { Text(strings.startCallTitle) },
            text = {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.height(300.dp)
                ) {
                    items(directoryUsers, key = { it.userId }) { user ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showNewCallPicker = false
                                    onStartCall(user, false)
                                }
                                .padding(vertical = 6.dp)
                        ) {
                            UserAvatar(
                                name = user.displayName,
                                avatarUrl = user.avatarUrl,
                                avatarBase64 = user.avatarBase64,
                                size = 42.dp,
                                isOnline = user.isOnline
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = user.displayName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = {
                                showNewCallPicker = false
                                onStartCall(user, false)
                            }) {
                                Icon(Icons.Rounded.Call, contentDescription = strings.audioCall, tint = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = {
                                showNewCallPicker = false
                                onStartCall(user, true)
                            }) {
                                Icon(Icons.Rounded.Videocam, contentDescription = strings.videoCall, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showNewCallPicker = false }) {
                    Text(strings.cancel)
                }
            }
        )
    }
}
