package com.example.uzhavan

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class ChatNotificationService : Service() {

    private val db = FirebaseDatabase
        .getInstance("https://agri-management-679c4-default-rtdb.firebaseio.com/")
        .reference

    // chatId -> last known message timestamp
    private val lastSeenTimestamps = mutableMapOf<String, Long>()
    private val chatListeners = mutableMapOf<String, ValueEventListener>()
    private var connectionsListener: ValueEventListener? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val myUid = FirebaseAuth.getInstance().currentUser?.uid ?: return START_NOT_STICKY
        listenForConnections(myUid)
        return START_STICKY
    }

    private fun listenForConnections(myUid: String) {
        connectionsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val connectedUids = snapshot.children.mapNotNull { it.key }
                // Remove listeners for uids no longer connected
                val activeChatIds = connectedUids.map { chatId(myUid, it) }.toSet()
                chatListeners.keys.filter { it !in activeChatIds }.forEach { id ->
                    db.child("chats").child(id).removeEventListener(chatListeners[id]!!)
                    chatListeners.remove(id)
                }
                // Add listeners for new connections
                connectedUids.forEach { peerUid ->
                    val cid = chatId(myUid, peerUid)
                    if (!chatListeners.containsKey(cid)) {
                        listenToChat(myUid, peerUid, cid)
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        db.child("connections").child(myUid).addValueEventListener(connectionsListener!!)
    }

    private fun listenToChat(myUid: String, peerUid: String, cid: String) {
        // Seed the last-seen timestamp so we don't notify on old messages
        lastSeenTimestamps[cid] = System.currentTimeMillis()

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val messages = snapshot.children.mapNotNull {
                    try { it.getValue(ChatMessage::class.java) } catch (e: Exception) { null }
                }
                val threshold = lastSeenTimestamps[cid] ?: return
                val newMessages = messages.filter {
                    it.senderId != myUid && it.timestamp > threshold
                }
                if (newMessages.isNotEmpty()) {
                    lastSeenTimestamps[cid] = newMessages.maxOf { it.timestamp }
                    // Fetch sender name then notify
                    db.child("users").child(peerUid)
                        .addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(s: DataSnapshot) {
                                val profile = try {
                                    s.getValue(UserProfile::class.java)
                                } catch (e: Exception) { null }
                                val senderName = profile?.fullName?.ifEmpty { profile.username }
                                    ?: "Someone"
                                val lastMsg = newMessages.last().text
                                showNotification(senderName, lastMsg, newMessages.size)
                            }
                            override fun onCancelled(e: DatabaseError) {}
                        })
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        chatListeners[cid] = listener
        db.child("chats").child(cid).orderByChild("timestamp").addValueEventListener(listener)
    }

    private fun showNotification(senderName: String, message: String, count: Int) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pi = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title = if (count > 1) "$senderName sent $count messages" else senderName
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        nm.notify(NOTIF_ID, notif)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Messages",
            NotificationManager.IMPORTANCE_HIGH
        ).apply { description = "New message notifications" }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    override fun onDestroy() {
        super.onDestroy()
        val myUid = FirebaseAuth.getInstance().currentUser?.uid
        if (myUid != null && connectionsListener != null) {
            db.child("connections").child(myUid).removeEventListener(connectionsListener!!)
        }
        chatListeners.forEach { (cid, listener) ->
            db.child("chats").child(cid).removeEventListener(listener)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun chatId(a: String, b: String) = if (a < b) "${a}_${b}" else "${b}_${a}"

    companion object {
        const val CHANNEL_ID = "uzhavan_messages"
        const val NOTIF_ID   = 1001
    }
}
