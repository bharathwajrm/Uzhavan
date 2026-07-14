package com.example.uzhavan

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

data class UserProfile(
    val uid: String = "",
    val fullName: String = "",
    val username: String = "",
    val email: String = "",
    val farmName: String = "",
    val location: String = "",
    val cropType: String = "",
    val bio: String = "",
    val profilePic: String = ""
)

data class Post(
    val postId: String = "",
    val uid: String = "",
    val username: String = "",
    val caption: String = "",
    val imageUrl: String = "",
    val timestamp: Long = 0L,
    val likes: Map<String, Boolean> = emptyMap(),
    val category: String = "general",
    val commentCount: Int = 0
)

data class Comment(
    val commentId: String = "",
    val uid: String = "",
    val username: String = "",
    val text: String = "",
    val timestamp: Long = 0L
)

data class ChatMessage(
    val messageId: String = "",
    val senderId: String = "",
    val text: String = "",
    val timestamp: Long = 0L,
    val seen: Boolean = false
)

data class ConnectionRequest(
    val fromUid: String = "",
    val fromUsername: String = "",
    val fromName: String = "",
    val status: String = "pending"
)

class UserViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    val db = FirebaseDatabase.getInstance("https://agri-management-679c4-default-rtdb.firebaseio.com/").reference
    private val httpClient = OkHttpClient()

    // ── Replace these two with your Cloudinary values ─────────────────────────
    // 1. Go to cloudinary.com → Settings → Upload → Add upload preset → Unsigned
    // 2. Your cloud name is on the Dashboard
    private val cloudinaryCloudName = "ftbac9sq"
    private val cloudinaryUploadPreset = "uzhavan"

    private val _user = mutableStateOf<FirebaseUser?>(auth.currentUser)
    val user: State<FirebaseUser?> = _user

    private val _username = mutableStateOf<String?>(null)
    val username: State<String?> = _username

    private val _currentProfile = mutableStateOf<UserProfile?>(null)
    val currentProfile: State<UserProfile?> = _currentProfile

    private val _allUsers = mutableStateOf<List<UserProfile>>(emptyList())
    val allUsers: State<List<UserProfile>> = _allUsers

    private val _connections = mutableStateOf<List<UserProfile>>(emptyList())
    val connections: State<List<UserProfile>> = _connections

    private val _incomingRequests = mutableStateOf<List<ConnectionRequest>>(emptyList())
    val incomingRequests: State<List<ConnectionRequest>> = _incomingRequests

    private val _sentRequests = mutableStateOf<Set<String>>(emptySet())
    val sentRequests: State<Set<String>> = _sentRequests

    private val _posts = mutableStateOf<List<Post>>(emptyList())
    val posts: State<List<Post>> = _posts

    private val _chatMessages = mutableStateOf<List<ChatMessage>>(emptyList())
    val chatMessages: State<List<ChatMessage>> = _chatMessages

    private var chatListenerLastTimestamp = 0L

    private val _comments = mutableStateOf<List<Comment>>(emptyList())
    val comments: State<List<Comment>> = _comments

    private val _uploadState = MutableStateFlow<UploadState>(UploadState.Idle)
    val uploadState: StateFlow<UploadState> = _uploadState.asStateFlow()

    private val _newsArticles = MutableStateFlow<List<NewsArticle>>(emptyList())
    val newsArticles: StateFlow<List<NewsArticle>> = _newsArticles.asStateFlow()

    private val _newsLoading = MutableStateFlow(false)
    val newsLoading: StateFlow<Boolean> = _newsLoading.asStateFlow()

    private val _newsLoadingMore = MutableStateFlow(false)
    val newsLoadingMore: StateFlow<Boolean> = _newsLoadingMore.asStateFlow()

    private val _newsError = MutableStateFlow<String?>(null)
    val newsError: StateFlow<String?> = _newsError.asStateFlow()

    private val _newsLanguage = MutableStateFlow("en")
    val newsLanguage: StateFlow<String> = _newsLanguage.asStateFlow()

    private var currentNewsPage = 1
    private var hasMoreNews = true

    private val _darkMode = MutableStateFlow("system")
    val darkMode: StateFlow<String> = _darkMode.asStateFlow()

    sealed class UploadState {
        object Idle : UploadState()
        data class Uploading(val progress: Float) : UploadState()
        object Success : UploadState()
        data class Error(val message: String) : UploadState()
    }

    init {
        auth.addAuthStateListener { firebaseAuth ->
            _user.value = firebaseAuth.currentUser
            if (firebaseAuth.currentUser != null) {
                fetchCurrentProfile(firebaseAuth.currentUser!!.uid)
                fetchAllUsers()
                fetchConnections(firebaseAuth.currentUser!!.uid)
                fetchIncomingRequests(firebaseAuth.currentUser!!.uid)
                fetchSentRequests(firebaseAuth.currentUser!!.uid)
                fetchFeed()
                fetchNews()
            } else {
                _username.value = null
                _currentProfile.value = null
            }
        }
    }

    fun initPreferences(context: Context) {
        viewModelScope.launch {
            context.dataStore.data.collect { prefs ->
                _darkMode.value = prefs[PrefKeys.DARK_MODE] ?: "system"
                _newsLanguage.value = prefs[PrefKeys.NEWS_LANGUAGE] ?: "en"
            }
        }
    }

    fun setDarkMode(context: Context, mode: String) {
        viewModelScope.launch {
            context.dataStore.edit { it[PrefKeys.DARK_MODE] = mode }
            _darkMode.value = mode
        }
    }

    fun setNewsLanguage(context: Context, lang: String) {
        viewModelScope.launch {
            context.dataStore.edit { it[PrefKeys.NEWS_LANGUAGE] = lang }
            _newsLanguage.value = lang
            // Optionally re-fetch news if the news API supports language filtering
            // fetchNews()
        }
    }

    fun fetchNews() {
        if (_newsLoading.value) return
        viewModelScope.launch {
            _newsLoading.value = true
            _newsError.value = null
            currentNewsPage = 1
            hasMoreNews = true
            NewsRepository.fetchAgriNews(page = 1).fold(
                onSuccess = { (articles, more) ->
                    _newsArticles.value = articles
                    hasMoreNews = more
                    currentNewsPage = 2
                },
                onFailure = { _newsError.value = it.message ?: "Failed to load news" }
            )
            _newsLoading.value = false
        }
    }

    fun loadMoreNews() {
        if (_newsLoadingMore.value || !hasMoreNews) return
        viewModelScope.launch {
            _newsLoadingMore.value = true
            NewsRepository.fetchAgriNews(page = currentNewsPage).fold(
                onSuccess = { (articles, more) ->
                    _newsArticles.value = _newsArticles.value + articles
                    hasMoreNews = more
                    currentNewsPage++
                },
                onFailure = { /* silently ignore load-more errors */ }
            )
            _newsLoadingMore.value = false
        }
    }

    fun saveUserProfile(uid: String, fullName: String, username: String, email: String,
                        farmName: String, location: String, cropType: String) {
        val profile = mapOf(
            "uid" to uid, "fullName" to fullName, "username" to username,
            "email" to email, "farmName" to farmName, "location" to location,
            "cropType" to cropType, "bio" to "", "profilePic" to ""
        )
        db.child("users").child(uid).setValue(profile)
        _username.value = username
        _currentProfile.value = UserProfile(uid, fullName, username, email, farmName, location, cropType)
    }

    fun saveUsername(uid: String, name: String) =
        saveUserProfile(uid, name, name.lowercase().replace(" ", ""), "", "", "", "")

    fun fetchUsername(uid: String) = fetchCurrentProfile(uid)

    private fun fetchCurrentProfile(uid: String) {
        db.child("users").child(uid).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val profile = try { snapshot.getValue(UserProfile::class.java) } catch (e: Exception) { null }
                _currentProfile.value = profile
                _username.value = profile?.username ?: profile?.fullName
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    fun fetchAllUsers() {
        db.child("users").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val myUid = auth.currentUser?.uid
                _allUsers.value = snapshot.children.mapNotNull {
                    try { it.getValue(UserProfile::class.java) } catch (e: Exception) { null }
                }.filter { it.uid != myUid }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    fun sendConnectionRequest(toUid: String) {
        val myUid = auth.currentUser?.uid ?: return
        val myProfile = _currentProfile.value ?: return
        val request = mapOf(
            "fromUid" to myUid, "fromUsername" to myProfile.username,
            "fromName" to myProfile.fullName, "status" to "pending"
        )
        db.child("connectionRequests").child(toUid).child(myUid).setValue(request)
        db.child("sentRequests").child(myUid).child(toUid).setValue(true)
        _sentRequests.value = _sentRequests.value + toUid
    }

    fun acceptConnectionRequest(fromUid: String) {
        val myUid = auth.currentUser?.uid ?: return
        db.child("connectionRequests").child(myUid).child(fromUid).child("status").setValue("accepted")
        db.child("connections").child(myUid).child(fromUid).setValue(true)
        db.child("connections").child(fromUid).child(myUid).setValue(true)
        fetchConnections(myUid)
        _incomingRequests.value = _incomingRequests.value.filter { it.fromUid != fromUid }
    }

    fun rejectConnectionRequest(fromUid: String) {
        val myUid = auth.currentUser?.uid ?: return
        db.child("connectionRequests").child(myUid).child(fromUid).removeValue()
        _incomingRequests.value = _incomingRequests.value.filter { it.fromUid != fromUid }
    }

    private fun fetchConnections(uid: String) {
        db.child("connections").child(uid).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val connectedUids = snapshot.children.map { it.key ?: "" }.filter { it.isNotEmpty() }
                if (connectedUids.isEmpty()) { _connections.value = emptyList(); return }
                val result = mutableListOf<UserProfile>()
                var loaded = 0
                connectedUids.forEach { connUid ->
                    db.child("users").child(connUid).addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(s: DataSnapshot) {
                            try { s.getValue(UserProfile::class.java)?.let { result.add(it) } } catch (e: Exception) {}
                            loaded++
                            if (loaded == connectedUids.size) _connections.value = result.toList()
                        }
                        override fun onCancelled(e: DatabaseError) {
                            loaded++
                            if (loaded == connectedUids.size) _connections.value = result.toList()
                        }
                    })
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun fetchIncomingRequests(uid: String) {
        db.child("connectionRequests").child(uid).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                _incomingRequests.value = snapshot.children.mapNotNull {
                    try { it.getValue(ConnectionRequest::class.java) } catch (e: Exception) { null }
                }.filter { it.status == "pending" }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun fetchSentRequests(uid: String) {
        db.child("sentRequests").child(uid).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                _sentRequests.value = snapshot.children.mapNotNull { it.key }.toSet()
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    fun fetchFeed() {
        db.child("posts").orderByChild("timestamp").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                _posts.value = snapshot.children.mapNotNull {
                    try { it.getValue(Post::class.java) } catch (e: Exception) { null }
                }.sortedByDescending { it.timestamp }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    fun toggleLike(postId: String) {
        val myUid = auth.currentUser?.uid ?: return
        val ref = db.child("posts").child(postId).child("likes").child(myUid)
        val post = _posts.value.find { it.postId == postId } ?: return
        if (post.likes.containsKey(myUid)) ref.removeValue() else ref.setValue(true)
    }

    // ── Comments ─────────────────────────────────────────────────────────────
    fun listenToComments(postId: String) {
        db.child("comments").child(postId).orderByChild("timestamp")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    _comments.value = snapshot.children.mapNotNull {
                        try { it.getValue(Comment::class.java) } catch (e: Exception) { null }
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    fun addComment(postId: String, text: String) {
        val myUid = auth.currentUser?.uid ?: return
        val myUsername = _username.value ?: return
        val ref = db.child("comments").child(postId).push()
        val comment = Comment(
            commentId = ref.key ?: "",
            uid = myUid,
            username = myUsername,
            text = text,
            timestamp = System.currentTimeMillis()
        )
        ref.setValue(comment)
        db.child("posts").child(postId).child("commentCount")
            .setValue(ServerValue.increment(1))
    }

    fun deleteComment(postId: String, commentId: String) {
        val myUid = auth.currentUser?.uid ?: return
        val comment = _comments.value.find { it.commentId == commentId } ?: return
        if (comment.uid != myUid) return
        db.child("comments").child(postId).child(commentId).removeValue()
        db.child("posts").child(postId).child("commentCount")
            .setValue(ServerValue.increment(-1))
    }

    // ── Delete Post ───────────────────────────────────────────────────────────
    fun deletePost(postId: String, imageUrl: String) {
        val myUid = auth.currentUser?.uid ?: return
        val post = _posts.value.find { it.postId == postId } ?: return
        if (post.uid != myUid) return
        db.child("posts").child(postId).removeValue()
        db.child("comments").child(postId).removeValue()
        // Cloudinary deletion requires a signed API call (server-side) — image stays in
        // Cloudinary but is no longer referenced anywhere in the app
    }

    // ── Photo Upload (Cloudinary) ─────────────────────────────────────────────
    fun uploadPostWithImage(context: Context, imageUri: Uri, caption: String, category: String) {
        val myUid = auth.currentUser?.uid ?: return
        val myUsername = _username.value ?: "farmer"
        _uploadState.value = UploadState.Uploading(0f)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bytes = context.contentResolver.openInputStream(imageUri)?.readBytes()
                    ?: throw Exception("Cannot read image")

                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart(
                        "file", "upload.jpg",
                        bytes.toRequestBody("image/jpeg".toMediaType())
                    )
                    .addFormDataPart("upload_preset", cloudinaryUploadPreset)
                    .build()

                val request = Request.Builder()
                    .url("https://api.cloudinary.com/v1_1/$cloudinaryCloudName/image/upload")
                    .post(requestBody)
                    .build()

                val response = httpClient.newCall(request).execute()
                val body = response.body?.string() ?: throw Exception("Empty response")

                if (!response.isSuccessful) throw Exception("Upload failed: $body")

                val imageUrl = JSONObject(body).getString("secure_url")

                withContext(Dispatchers.Main) {
                    val postRef = db.child("posts").push()
                    postRef.setValue(
                        Post(
                            postId    = postRef.key ?: "",
                            uid       = myUid,
                            username  = myUsername,
                            caption   = caption,
                            imageUrl  = imageUrl,
                            timestamp = System.currentTimeMillis(),
                            category  = category
                        )
                    )
                    _uploadState.value = UploadState.Success
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uploadState.value = UploadState.Error(e.message ?: "Upload failed")
                }
            }
        }
    }

    fun createTextPost(caption: String, category: String) {
        val myUid = auth.currentUser?.uid ?: return
        val myUsername = _username.value ?: "farmer"
        val postRef = db.child("posts").push()
        val post = Post(
            postId = postRef.key ?: "",
            uid = myUid,
            username = myUsername,
            caption = caption,
            timestamp = System.currentTimeMillis(),
            category = category
        )
        postRef.setValue(post)
        _uploadState.value = UploadState.Success
    }

    fun resetUploadState() { _uploadState.value = UploadState.Idle }

    // ── Chat ──────────────────────────────────────────────────────────────────
    fun listenToChat(chatId: String) {
        chatListenerLastTimestamp = System.currentTimeMillis()
        db.child("chats").child(chatId).orderByChild("timestamp")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    _chatMessages.value = snapshot.children.mapNotNull {
                        try { it.getValue(ChatMessage::class.java) } catch (e: Exception) { null }
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    fun sendMessage(chatId: String, text: String) {
        val myUid = auth.currentUser?.uid ?: return
        val msgRef = db.child("chats").child(chatId).push()
        val msg = ChatMessage(
            messageId = msgRef.key ?: "",
            senderId = myUid,
            text = text,
            timestamp = System.currentTimeMillis()
        )
        msgRef.setValue(msg)
    }

    fun getChatId(otherUid: String): String {
        val myUid = auth.currentUser?.uid ?: return ""
        return if (myUid < otherUid) "${myUid}_${otherUid}" else "${otherUid}_${myUid}"
    }

    fun signOut() { auth.signOut() }
}
