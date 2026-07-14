package com.example.uzhavan

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.uzhavan.ui.theme.*
import com.google.firebase.auth.FirebaseAuth

private val avatarColors = listOf(
    Color(0xFF2E7D32), Color(0xFF1565C0), Color(0xFF6A1B9A),
    Color(0xFFE65100), Color(0xFF00695C), Color(0xFF4E342E)
)

@Composable
fun MessagesScreen(viewModel: UserViewModel) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0=Friends, 1=Discover, 2=Requests
    var openChatWith by remember { mutableStateOf<UserProfile?>(null) }

    val connections by viewModel.connections
    val allUsers by viewModel.allUsers
    val incomingRequests by viewModel.incomingRequests
    val sentRequests by viewModel.sentRequests
    val myUid = remember { FirebaseAuth.getInstance().currentUser?.uid ?: "" }

    if (openChatWith != null) {
        ChatScreen(
            peer = openChatWith!!,
            viewModel = viewModel,
            onBack = { openChatWith = null }
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AgriBackground)
    ) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Text("Messages", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = AgriGreenDark)
            if (incomingRequests.isNotEmpty()) {
                Badge(
                    containerColor = AgriYellow,
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    Text("${incomingRequests.size}", color = AgriGreenDark, fontSize = 11.sp)
                }
            }
        }

        // Tabs
        SecondaryTabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.White,
            contentColor = AgriGreen
        ) {
            listOf("Friends", "Discover", "Requests").forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(title, fontSize = 13.sp, fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal)
                            if (index == 2 && incomingRequests.isNotEmpty()) {
                                Badge(containerColor = AgriGreen) {
                                    Text("${incomingRequests.size}", color = Color.White, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                )
            }
        }

        when (selectedTab) {
            0 -> FriendsTab(connections = connections, onOpenChat = { openChatWith = it })
            1 -> DiscoverTab(
                users = allUsers,
                connections = connections,
                sentRequests = sentRequests,
                myUid = myUid,
                onConnect = { viewModel.sendConnectionRequest(it) },
                onOpenChat = { openChatWith = it }
            )
            2 -> RequestsTab(
                requests = incomingRequests,
                onAccept = { viewModel.acceptConnectionRequest(it) },
                onReject = { viewModel.rejectConnectionRequest(it) }
            )
        }
    }
}

@Composable
private fun FriendsTab(connections: List<UserProfile>, onOpenChat: (UserProfile) -> Unit) {
    if (connections.isEmpty()) {
        EmptyState(
            emoji = "🤝",
            title = "No connections yet",
            subtitle = "Go to Discover to connect with farmers"
        )
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(connections) { user ->
            FriendListItem(user = user, onClick = { onOpenChat(user) })
        }
    }
}

@Composable
private fun FriendListItem(user: UserProfile, onClick: () -> Unit) {
    val colorIndex = user.uid.hashCode().let { if (it < 0) -it else it } % avatarColors.size
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(avatarColors[colorIndex]),
            contentAlignment = Alignment.Center
        ) {
            val initial = remember(user.fullName, user.username) {
                (user.fullName.ifEmpty { user.username }).firstOrNull()?.uppercaseChar()?.toString() ?: "U"
            }
            Text(
                text = initial,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(user.fullName.ifEmpty { user.username }, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = AgriGreenDark)
            Text("@${user.username}", fontSize = 12.sp, color = AgriGray)
            if (user.farmName.isNotEmpty()) {
                Text("🌾 ${user.farmName}", fontSize = 11.sp, color = AgriGreen)
            }
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = AgriGray)
    }
    HorizontalDivider(color = AgriDivider, thickness = 0.5.dp, modifier = Modifier.padding(start = 80.dp))
}

@Composable
private fun DiscoverTab(
    users: List<UserProfile>,
    connections: List<UserProfile>,
    sentRequests: Set<String>,
    myUid: String,
    onConnect: (String) -> Unit,
    onOpenChat: (UserProfile) -> Unit
) {
    val connectedUids = remember(connections) { connections.map { it.uid }.toSet() }

    if (users.isEmpty()) {
        EmptyState(emoji = "🌍", title = "No users found", subtitle = "Be the first to join!")
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(users) { user ->
            UserDiscoveryCard(
                user = user,
                isConnected = connectedUids.contains(user.uid),
                requestSent = sentRequests.contains(user.uid),
                onConnect = { onConnect(user.uid) },
                onChat = { onOpenChat(user) }
            )
        }
    }
}

@Composable
private fun UserDiscoveryCard(
    user: UserProfile,
    isConnected: Boolean,
    requestSent: Boolean,
    onConnect: () -> Unit,
    onChat: () -> Unit
) {
    val colorIndex = user.uid.hashCode().let { if (it < 0) -it else it } % avatarColors.size

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(avatarColors[colorIndex]),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = (user.fullName.ifEmpty { user.username }).first().uppercaseChar().toString(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(user.fullName.ifEmpty { user.username }, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = AgriGreenDark)
                Text("@${user.username}", fontSize = 12.sp, color = AgriGray)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                    if (user.cropType.isNotEmpty()) AgriChip("🌱 ${user.cropType}")
                    if (user.location.isNotEmpty()) AgriChip("📍 ${user.location}")
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            when {
                isConnected -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = AgriGreenSurface,
                            border = BorderStroke(1.dp, AgriGreen)
                        ) {
                            Text("Connected ✓", color = AgriGreen, fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                        }
                        TextButton(onClick = onChat, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
                            Icon(Icons.Default.Chat, contentDescription = null, tint = AgriGreen, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Chat", color = AgriGreen, fontSize = 12.sp)
                        }
                    }
                }
                requestSent -> {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = AgriYellowLight,
                        border = BorderStroke(1.dp, AgriYellow)
                    ) {
                        Text("Pending…", color = AgriEarth, fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                    }
                }
                else -> {
                    Button(
                        onClick = onConnect,
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AgriGreen),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Connect", color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun RequestsTab(
    requests: List<ConnectionRequest>,
    onAccept: (String) -> Unit,
    onReject: (String) -> Unit
) {
    if (requests.isEmpty()) {
        EmptyState(emoji = "📬", title = "No pending requests", subtitle = "Connection requests will appear here")
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Connection Requests", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = AgriGreenDark, modifier = Modifier.padding(bottom = 4.dp))
        }
        items(requests) { req ->
            RequestCard(request = req, onAccept = { onAccept(req.fromUid) }, onReject = { onReject(req.fromUid) })
        }
    }
}

@Composable
private fun RequestCard(request: ConnectionRequest, onAccept: () -> Unit, onReject: () -> Unit) {
    val colorIndex = request.fromUid.hashCode().let { if (it < 0) -it else it } % avatarColors.size
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(avatarColors[colorIndex]),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = request.fromName.ifEmpty { request.fromUsername }.first().uppercaseChar().toString(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(request.fromName.ifEmpty { request.fromUsername }, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = AgriGreenDark)
                Text("@${request.fromUsername}", fontSize = 12.sp, color = AgriGray)
                Text("wants to connect with you", fontSize = 12.sp, color = AgriGray, modifier = Modifier.padding(top = 2.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = onAccept,
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AgriGreen),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text("Accept", color = Color.White, fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = onReject,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, Color(0xFFE53935)),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text("Reject", color = Color(0xFFE53935), fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun ChatScreen(peer: UserProfile, viewModel: UserViewModel, onBack: () -> Unit) {
    val chatId = viewModel.getChatId(peer.uid)
    val messages by viewModel.chatMessages
    val myUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val colorIndex = peer.uid.hashCode().let { if (it < 0) -it else it } % avatarColors.size

    LaunchedEffect(chatId) {
        viewModel.listenToChat(chatId)
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(modifier = Modifier.fillMaxSize().background(AgriBackground)) {
        // Chat header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = AgriGreenDark)
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(avatarColors[colorIndex]),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = (peer.fullName.ifEmpty { peer.username }).first().uppercaseChar().toString(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(peer.fullName.ifEmpty { peer.username }, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = AgriGreenDark)
                Text("@${peer.username}", fontSize = 11.sp, color = AgriGray)
            }
            IconButton(onClick = {}) {
                Icon(Icons.Default.MoreVert, contentDescription = null, tint = AgriGray)
            }
        }
        HorizontalDivider(color = AgriDivider)

        // Messages
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🌾", fontSize = 40.sp)
                            Text("Start a conversation!", color = AgriGray, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }
            }
            items(messages) { msg ->
                ChatBubble(message = msg, isMe = msg.senderId == myUid)
            }
        }

        // Input bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = { Text("Message…", color = AgriGray) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AgriGreen,
                    unfocusedBorderColor = AgriDivider,
                    focusedTextColor = AgriGreenDark,
                    unfocusedTextColor = AgriGreenDark
                ),
                maxLines = 4,
                trailingIcon = {
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.EmojiEmotions, contentDescription = null, tint = AgriGray)
                    }
                }
            )
            Spacer(modifier = Modifier.width(8.dp))
            FloatingActionButton(
                onClick = {
                    if (inputText.trim().isNotEmpty()) {
                        viewModel.sendMessage(chatId, inputText.trim())
                        inputText = ""
                    }
                },
                modifier = Modifier.size(48.dp),
                containerColor = AgriGreen,
                contentColor = Color.White,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage, isMe: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier.widthIn(max = 280.dp),
            horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = 18.dp, topEnd = 18.dp,
                            bottomStart = if (isMe) 18.dp else 4.dp,
                            bottomEnd = if (isMe) 4.dp else 18.dp
                        )
                    )
                    .background(if (isMe) AgriGreen else Color.White)
                    .border(
                        width = if (isMe) 0.dp else 1.dp,
                        color = if (isMe) Color.Transparent else AgriDivider,
                        shape = RoundedCornerShape(
                            topStart = 18.dp, topEnd = 18.dp,
                            bottomStart = if (isMe) 18.dp else 4.dp,
                            bottomEnd = if (isMe) 4.dp else 18.dp
                        )
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    text = message.text,
                    color = if (isMe) Color.White else AgriGreenDark,
                    fontSize = 14.sp
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = formatTime(message.timestamp),
                    fontSize = 10.sp,
                    color = AgriGray
                )
                if (isMe) {
                    Icon(
                        if (message.seen) Icons.Default.DoneAll else Icons.Default.Done,
                        contentDescription = null,
                        tint = if (message.seen) AgriGreen else AgriGray,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AgriChip(text: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = AgriGreenSurface
    ) {
        Text(text, fontSize = 10.sp, color = AgriGreen, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun EmptyState(emoji: String, title: String, subtitle: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(emoji, fontSize = 56.sp)
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = AgriGreenDark)
            Text(subtitle, fontSize = 13.sp, color = AgriGray)
        }
    }
}

private fun formatTime(timestamp: Long): String {
    val sdf = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(timestamp))
}
