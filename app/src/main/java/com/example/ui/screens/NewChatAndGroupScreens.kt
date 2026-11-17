package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.ImageCompressor
import com.example.core.LocalAppStrings
import com.example.data.model.ChatThread
import com.example.data.model.DirectoryUser
import com.example.data.model.UiState
import com.example.ui.components.ErrorStateCard
import com.example.ui.components.LoadingStateView
import com.example.ui.components.UserAvatar
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewChatScreen(
    directoryState: UiState<List<DirectoryUser>>,
    onSelectUser: (DirectoryUser) -> Unit,
    onCreateGroupClick: () -> Unit,
    onBack: () -> Unit
) {
    val strings = LocalAppStrings.current
    BackHandler(onBack = onBack)
    var query by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.selectContactTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = strings.cancel)
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(strings.searchPeoplePlaceholder) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(onClick = onCreateGroupClick)
                    .minimumInteractiveComponentSize()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.GroupAdd,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = strings.newGroup,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            when (directoryState) {
                UiState.Loading -> LoadingStateView(modifier = Modifier.weight(1f))
                is UiState.Error -> ErrorStateCard(message = directoryState.message, modifier = Modifier.weight(1f))
                is UiState.Success -> {
                    val filtered = remember(directoryState.data, query) {
                        directoryState.data.filter {
                            query.isBlank() || it.displayName.contains(query, ignoreCase = true)
                        }
                    }
                    if (filtered.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = strings.noPeopleFound,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(filtered, key = { it.userId }) { user ->
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    tonalElevation = 1.dp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable { onSelectUser(user) }
                                        .minimumInteractiveComponentSize()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        UserAvatar(
                                            name = user.displayName,
                                            avatarUrl = user.avatarUrl,
                                            avatarBase64 = user.avatarBase64,
                                            size = 48.dp,
                                            isOnline = user.isOnline
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(user.displayName, style = MaterialTheme.typography.titleMedium)
                                            Text(
                                                text = user.about.ifBlank { strings.defaultAbout },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1
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
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGroupScreen(
    directoryUsers: List<DirectoryUser>,
    onCreateGroup: (groupName: String, members: List<DirectoryUser>, iconBase64: String?) -> Unit,
    onBack: () -> Unit
) {
    val strings = LocalAppStrings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    BackHandler(onBack = onBack)

    var groupName by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var groupIconBase64 by remember { mutableStateOf<String?>(null) }
    val selectedMembers = remember { mutableStateListOf<DirectoryUser>() }

    val iconPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                groupIconBase64 = ImageCompressor.compressUriToBase64(context, uri, maxDimension = 320, quality = 65)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.createGroupTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = strings.cancel)
                    }
                }
            )
        },
        floatingActionButton = {
            if (groupName.isNotBlank() && selectedMembers.isNotEmpty()) {
                FloatingActionButton(
                    onClick = { onCreateGroup(groupName, selectedMembers.toList(), groupIconBase64) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("confirm_create_group_fab")
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = strings.createGroupButton)
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .clickable {
                            iconPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (groupIconBase64 != null) {
                        UserAvatar(name = groupName, avatarBase64 = groupIconBase64, size = 58.dp, isGroup = true)
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.PhotoCamera,
                            contentDescription = strings.changeAvatar,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                OutlinedTextField(
                    value = groupName,
                    onValueChange = { if (it.length <= 60) groupName = it },
                    placeholder = { Text(strings.groupNameHint) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f).testTag("group_name_input")
                )
            }

            if (selectedMembers.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    items(selectedMembers, key = { it.userId }) { member ->
                        InputChip(
                            selected = true,
                            onClick = { selectedMembers.remove(member) },
                            label = { Text(member.displayName) }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text(strings.selectParticipantsHint) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            )

            val filtered = remember(directoryUsers, search) {
                directoryUsers.filter { search.isBlank() || it.displayName.contains(search, ignoreCase = true) }
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(filtered, key = { it.userId }) { user ->
                    val checked = selectedMembers.any { it.userId == user.userId }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 1.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                if (checked) selectedMembers.removeAll { it.userId == user.userId }
                                else selectedMembers.add(user)
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            UserAvatar(
                                name = user.displayName,
                                avatarUrl = user.avatarUrl,
                                avatarBase64 = user.avatarBase64,
                                size = 44.dp,
                                isOnline = user.isOnline
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = user.displayName,
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f)
                            )
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { isChecked ->
                                    if (isChecked) {
                                        if (!checked) selectedMembers.add(user)
                                    } else {
                                        selectedMembers.removeAll { it.userId == user.userId }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupInfoScreen(
    chat: ChatThread?,
    directoryUsers: List<DirectoryUser>,
    currentUserId: String,
    onRenameGroup: (String) -> Unit,
    onAddMembers: (List<DirectoryUser>) -> Unit,
    onLeaveGroup: () -> Unit,
    onBack: () -> Unit
) {
    val strings = LocalAppStrings.current
    BackHandler(onBack = onBack)

    var showRenameDialog by remember { mutableStateOf(false) }
    var showAddMembersDialog by remember { mutableStateOf(false) }

    val isAdmin = chat?.adminIds?.contains(currentUserId) == true

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.groupInfoTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = strings.cancel)
                    }
                }
            )
        }
    ) { innerPadding ->
        if (chat == null) {
            LoadingStateView(modifier = Modifier.padding(innerPadding))
            return@Scaffold
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        UserAvatar(
                            name = chat.groupName ?: "Group",
                            avatarBase64 = chat.groupIconBase64,
                            size = 84.dp,
                            isGroup = true
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = chat.groupName ?: "Group",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = "${chat.participantIds.size} ${strings.membersCountSuffix}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (isAdmin) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton(onClick = { showRenameDialog = true }) {
                                    Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(strings.editGroupName)
                                }
                                OutlinedButton(onClick = { showAddMembersDialog = true }) {
                                    Icon(Icons.Rounded.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(strings.addMembers)
                                }
                            }
                        }
                    }
                }
            }

            items(chat.participantIds, key = { it }) { memberId ->
                val name = chat.participantNames[memberId]
                    ?: directoryUsers.firstOrNull { it.userId == memberId }?.displayName
                    ?: "User"
                val memberAdmin = memberId in chat.adminIds
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        UserAvatar(name = name, size = 44.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = name,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                        if (memberAdmin) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = strings.groupAdminLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            item {
                OutlinedButton(
                    onClick = onLeaveGroup,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ExitToApp, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(strings.leaveGroup)
                }
            }
        }
    }

    if (showRenameDialog && chat != null) {
        var newName by remember { mutableStateOf(chat.groupName ?: "") }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text(strings.editGroupName) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { if (it.length <= 60) newName = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRenameDialog = false
                        onRenameGroup(newName)
                    },
                    enabled = newName.isNotBlank()
                ) {
                    Text(strings.save)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text(strings.cancel) }
            }
        )
    }

    if (showAddMembersDialog && chat != null) {
        val candidates = directoryUsers.filterNot { it.userId in chat.participantIds }
        val toAdd = remember { mutableStateListOf<DirectoryUser>() }
        AlertDialog(
            onDismissRequest = { showAddMembersDialog = false },
            title = { Text(strings.addMembers) },
            text = {
                if (candidates.isEmpty()) {
                    Text(strings.noPeopleFound)
                } else {
                    LazyColumn(modifier = Modifier.height(260.dp)) {
                        items(candidates, key = { it.userId }) { user ->
                            val checked = toAdd.any { it.userId == user.userId }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (checked) toAdd.removeAll { it.userId == user.userId }
                                        else toAdd.add(user)
                                    }
                                    .padding(vertical = 6.dp)
                            ) {
                                Checkbox(checked = checked, onCheckedChange = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(user.displayName)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showAddMembersDialog = false
                        if (toAdd.isNotEmpty()) onAddMembers(toAdd.toList())
                    },
                    enabled = toAdd.isNotEmpty()
                ) {
                    Text(strings.addMembers)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddMembersDialog = false }) { Text(strings.cancel) }
            }
        )
    }
}
