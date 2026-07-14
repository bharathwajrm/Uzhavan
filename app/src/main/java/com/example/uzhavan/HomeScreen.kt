package com.example.uzhavan

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.uzhavan.ui.theme.*
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ── Avatar colours ────────────────────────────────────────────────────────────
private val avatarPalette = listOf(
    Color(0xFF2E7D32), Color(0xFF1565C0), Color(0xFF6A1B9A),
    Color(0xFFE65100), Color(0xFF00695C), Color(0xFF4E342E)
)
internal fun avatarColor(uid: String) =
    avatarPalette[uid.hashCode().let { if (it < 0) -it else it } % avatarPalette.size]

internal val categoryEmojis = mapOf(
    "harvest" to "🌾", "machinery" to "🚜", "crops" to "🌱",
    "market" to "📊", "livestock" to "🐄", "general" to "🌿",
    "organic" to "🌿", "irrigation" to "💧", "poultry" to "🐓", "vegetables" to "🥬"
)

// ── Home Screen — vertical news feed only ─────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: UserViewModel) {
    val newsArticles by viewModel.newsArticles.collectAsState()
    val newsLoading  by viewModel.newsLoading.collectAsState()
    val newsLoadingMore by viewModel.newsLoadingMore.collectAsState()
    val newsError    by viewModel.newsError.collectAsState()
    val listState = rememberLazyListState()
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (newsArticles.isEmpty()) viewModel.fetchNews()
    }

    // Trigger load-more when near the end
    LaunchedEffect(listState.firstVisibleItemIndex) {
        val total = listState.layoutInfo.totalItemsCount
        val last = listState.firstVisibleItemIndex + listState.layoutInfo.visibleItemsInfo.size
        if (total > 0 && last >= total - 3) viewModel.loadMoreNews()
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            isRefreshing = true
            viewModel.fetchNews()
            scope.launch {
                delay(800)
                isRefreshing = false
            }
        },
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().background(AgriBackground),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
        // Loading
        if (newsLoading) {
            item {
                Box(
                    Modifier.fillMaxWidth().padding(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(color = AgriGreen, modifier = Modifier.size(36.dp))
                        Text("Loading news…", color = AgriGray, fontSize = 14.sp)
                    }
                }
            }
        } else if (newsArticles.isEmpty()) {
            item {
                Box(
                    Modifier.fillMaxWidth().padding(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("📰", fontSize = 48.sp)
                        Text("No news available", color = AgriGray, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        if (newsError != null) {
                            Text(newsError!!, color = Color(0xFFE53935), fontSize = 12.sp)
                        }
                        TextButton(onClick = { viewModel.fetchNews() }) {
                            Text("Retry", color = AgriGreen)
                        }
                    }
                }
            }
        } else {
            newsArticles.forEach { article ->
                item(key = article.url) {
                    NewsCardVertical(article)
                }
            }

            // Load-more spinner at bottom
            if (newsLoadingMore) {
                item {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AgriGreen, modifier = Modifier.size(28.dp))
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun NewsCardVertical(article: NewsArticle) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        onClick = {
            if (article.url.isNotEmpty()) {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(article.url)))
            }
        }
    ) {
        Column {
            // Image
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                    .background(AgriGreenSurface),
                contentAlignment = Alignment.Center
            ) {
                if (!article.image.isNullOrEmpty()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(article.image)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text("🌾", fontSize = 52.sp)
                }
            }

            // Content
            Column(modifier = Modifier.padding(14.dp)) {
                // Source + time row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = AgriGreenSurface
                    ) {
                        Text(
                            text = article.source.name.ifEmpty { "News" },
                            color = AgriGreen,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        formatNewsTime(article.publishedAt),
                        fontSize = 11.sp,
                        color = AgriGray
                    )
                }

                // Title
                Text(
                    text = article.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = AgriGreenDark,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                // Description
                if (article.description.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = article.description,
                        fontSize = 13.sp,
                        color = Color(0xFF555555),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 18.sp
                    )
                }

                // Read more
                Spacer(Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Read more",
                        color = AgriGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Icon(
                        Icons.Default.ArrowForward,
                        null,
                        tint = AgriGreen,
                        modifier = Modifier.size(14.dp).padding(start = 2.dp)
                    )
                }
            }
        }
    }
}

// ── Post Card ─────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostCard(post: Post, myUid: String, viewModel: UserViewModel) {
    val context   = LocalContext.current
    val isLiked   = post.likes.containsKey(myUid)
    val likeCount = post.likes.size
    val isOwner   = post.uid == myUid
    var saved by remember { mutableStateOf(false) }
    var showComments by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    if (showComments) {
        CommentsBottomSheet(
            postId    = post.postId,
            myUid     = myUid,
            viewModel = viewModel,
            onDismiss = { showComments = false }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title   = { Text("Delete Post") },
            text    = { Text("Are you sure you want to delete this post? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePost(post.postId, post.imageUrl)
                    showDeleteDialog = false
                }) { Text("Delete", color = Color(0xFFE53935)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }

    Card(
        modifier  = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape     = RoundedCornerShape(0.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(42.dp).clip(CircleShape).background(avatarColor(post.uid)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        post.username.firstOrNull()?.uppercaseChar()?.toString() ?: "F",
                        color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(post.username, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = AgriGreenDark)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "${categoryEmojis[post.category] ?: "🌿"} ${post.category.replaceFirstChar { it.uppercase() }}",
                            fontSize = 11.sp, color = AgriGray
                        )
                        Text("·", fontSize = 11.sp, color = AgriGray)
                        Text(timeAgo(post.timestamp), fontSize = 11.sp, color = AgriGray)
                    }
                }
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, null, tint = AgriGray)
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        if (isOwner) {
                            DropdownMenuItem(
                                text = { Text("Delete Post", color = Color(0xFFE53935)) },
                                leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color(0xFFE53935)) },
                                onClick = { showMenu = false; showDeleteDialog = true }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Share") },
                            leadingIcon = { Icon(Icons.Default.Share, null) },
                            onClick = { showMenu = false; sharePost(context, post) }
                        )
                        DropdownMenuItem(
                            text = { Text("Copy Link") },
                            leadingIcon = { Icon(Icons.Default.Link, null) },
                            onClick = { showMenu = false }
                        )
                    }
                }
            }

            // Image or emoji placeholder
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .background(AgriGreenSurface),
                contentAlignment = Alignment.Center
            ) {
                if (post.imageUrl.isNotEmpty()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(post.imageUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(categoryEmojis[post.category] ?: "🌿", fontSize = 72.sp)
                        Text(
                            post.category.replaceFirstChar { it.uppercase() },
                            color = AgriGreen, fontSize = 14.sp, fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Action row
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PostActionBtn(
                    icon = if (isLiked) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                    tint = if (isLiked) Color(0xFFE53935) else AgriGreenDark,
                    onClick = { viewModel.toggleLike(post.postId) }
                )
                PostActionBtn(Icons.Outlined.ChatBubbleOutline, onClick = { showComments = true })
                PostActionBtn(Icons.Outlined.Share, onClick = { sharePost(context, post) })
                Spacer(Modifier.weight(1f))
                PostActionBtn(
                    icon = if (saved) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                    tint = if (saved) AgriGreen else AgriGreenDark,
                    onClick = { saved = !saved }
                )
            }

            // Like count
            if (likeCount > 0) {
                Text(
                    "$likeCount ${if (likeCount == 1) "like" else "likes"}",
                    fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = AgriGreenDark,
                    modifier = Modifier.padding(horizontal = 14.dp)
                )
            }

            // Caption
            Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                Text(post.username, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = AgriGreenDark)
                Spacer(Modifier.width(6.dp))
                Text(post.caption, fontSize = 13.sp, color = Color(0xFF333333), maxLines = 3, overflow = TextOverflow.Ellipsis)
            }

            // Comment count
            if (post.commentCount > 0) {
                TextButton(
                    onClick = { showComments = true },
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
                ) {
                    Text("View all ${post.commentCount} comments", fontSize = 12.sp, color = AgriGray)
                }
            }

            HorizontalDivider(color = AgriDivider, thickness = 0.5.dp)
        }
    }
}

@Composable
private fun PostActionBtn(icon: ImageVector, tint: Color = AgriGreenDark, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(42.dp)) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
    }
}

// ── Comments Bottom Sheet ─────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsBottomSheet(
    postId: String,
    myUid: String,
    viewModel: UserViewModel,
    onDismiss: () -> Unit
) {
    val comments by viewModel.comments
    var commentText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(postId) { viewModel.listenToComments(postId) }
    LaunchedEffect(comments.size) {
        if (comments.isNotEmpty()) scope.launch { listState.animateScrollToItem(comments.size - 1) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Comments", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = AgriGreenDark, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null, tint = AgriGray) }
            }
            HorizontalDivider(color = AgriDivider)

            if (comments.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("💬", fontSize = 36.sp)
                        Text("No comments yet", color = AgriGray, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(comments, key = { it.commentId }) { comment ->
                        CommentItem(
                            comment = comment,
                            isOwner = comment.uid == myUid,
                            onDelete = { viewModel.deleteComment(postId, comment.commentId) }
                        )
                    }
                }
            }

            HorizontalDivider(color = AgriDivider)

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = commentText,
                    onValueChange = { commentText = it },
                    placeholder = { Text("Add a comment…", color = AgriGray, fontSize = 14.sp) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = AgriGreen,
                        unfocusedBorderColor = AgriDivider,
                        focusedTextColor     = AgriGreenDark,
                        unfocusedTextColor   = AgriGreenDark
                    )
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (commentText.trim().isNotEmpty()) {
                            viewModel.addComment(postId, commentText.trim())
                            commentText = ""
                        }
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (commentText.trim().isNotEmpty()) AgriGreen else AgriGray)
                ) {
                    Icon(Icons.Default.Send, null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun CommentItem(comment: Comment, isOwner: Boolean, onDelete: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier.size(36.dp).clip(CircleShape).background(avatarColor(comment.uid)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                comment.username.firstOrNull()?.uppercaseChar()?.toString() ?: "F",
                color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(comment.username, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = AgriGreenDark)
                Text(timeAgo(comment.timestamp), fontSize = 10.sp, color = AgriGray)
            }
            Text(comment.text, fontSize = 13.sp, color = Color(0xFF333333))
        }
        if (isOwner) {
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.DeleteOutline, null, tint = AgriGray, modifier = Modifier.size(16.dp))
            }
        }
    }
}

// ── Share ─────────────────────────────────────────────────────────────────────
private fun sharePost(context: Context, post: Post) {
    val text = "Check out this post by @${post.username} on Uzhavan:\n\n${post.caption}\n\n#Uzhavan #Agriculture #Farming"
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Share via"))
}

// ── Time helpers ──────────────────────────────────────────────────────────────
internal fun timeAgo(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000     -> "Just now"
        diff < 3_600_000  -> "${diff / 60_000}m ago"
        diff < 86_400_000 -> "${diff / 3_600_000}h ago"
        else              -> "${diff / 86_400_000}d ago"
    }
}

private fun formatNewsTime(publishedAt: String): String {
    return try {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.getDefault())
        val date = sdf.parse(publishedAt) ?: return publishedAt
        timeAgo(date.time)
    } catch (e: Exception) { publishedAt.take(10) }
}
