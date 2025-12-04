package com.example.compow

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.compow.network.SocketIOManager
import kotlinx.coroutines.*

/**
 * Foreground Service for maintaining Socket.IO connection in background
 * Ensures users stay online and receive alerts even when app is closed
 */
class SocketForegroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var socketManager: SocketIOManager

    private var userId: String? = null
    private var userName: String? = null
    private var isInChatRoom = false

    companion object {
        const val NOTIFICATION_ID = 2001
        const val CHANNEL_ID = "CompowSocketChannel"

        const val ACTION_START_CONNECT = "ACTION_START_CONNECT"
        const val ACTION_STOP_CONNECT = "ACTION_STOP_CONNECT"
        const val ACTION_JOIN_ROOM = "ACTION_JOIN_ROOM"
        const val ACTION_LEAVE_ROOM = "ACTION_LEAVE_ROOM"

        const val EXTRA_USER_ID = "USER_ID"
        const val EXTRA_USER_NAME = "USER_NAME"

        fun startConnection(context: Context, userId: String, userName: String) {
            val intent = Intent(context, SocketForegroundService::class.java).apply {
                action = ACTION_START_CONNECT
                putExtra(EXTRA_USER_ID, userId)
                putExtra(EXTRA_USER_NAME, userName)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopConnection(context: Context) {
            val intent = Intent(context, SocketForegroundService::class.java).apply {
                action = ACTION_STOP_CONNECT
            }
            context.startService(intent)
        }

        fun joinChatRoom(context: Context) {
            val intent = Intent(context, SocketForegroundService::class.java).apply {
                action = ACTION_JOIN_ROOM
            }
            context.startService(intent)
        }

        fun leaveChatRoom(context: Context) {
            val intent = Intent(context, SocketForegroundService::class.java).apply {
                action = ACTION_LEAVE_ROOM
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        socketManager = SocketIOManager.getInstance()
        createNotificationChannel()
        Log.d("SocketForegroundService", "🔧 Service created")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_CONNECT -> {
                userId = intent.getStringExtra(EXTRA_USER_ID)
                userName = intent.getStringExtra(EXTRA_USER_NAME)

                if (userId != null) {
                    startForeground(NOTIFICATION_ID, createNotification("Connecting..."))
                    connectToSocket()
                } else {
                    Log.e("SocketForegroundService", "❌ No USER_ID provided")
                    stopSelf()
                }
            }

            ACTION_STOP_CONNECT -> {
                disconnectFromSocket()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }

            ACTION_JOIN_ROOM -> {
                if (userId != null && socketManager.isConnected.value) {
                    joinChatRoom()
                }
            }

            ACTION_LEAVE_ROOM -> {
                if (userId != null && socketManager.isConnected.value) {
                    leaveChatRoom()
                }
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        Log.d("SocketForegroundService", "🛑 Service destroyed")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "ComPow Connection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Maintains connection for emergency alerts"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(contentText: String): Notification {
        val stopIntent = Intent(this, SocketForegroundService::class.java).apply {
            action = ACTION_STOP_CONNECT
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ComPow Active")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Disconnect",
                stopPendingIntent
            )
            .build()
    }

    private fun updateNotification(contentText: String) {
        try {
            val notification = createNotification(contentText)
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e("SocketForegroundService", "Failed to update notification: ${e.message}")
        }
    }

    private fun connectToSocket() {
        serviceScope.launch {
            try {
                if (!socketManager.isConnected.value) {
                    socketManager.connect()

                    // Wait for connection
                    var attempts = 0
                    while (!socketManager.isConnected.value && attempts < 20) {
                        delay(500)
                        attempts++
                    }

                    if (socketManager.isConnected.value) {
                        socketManager.joinUserRoom(userId!!, userName ?: "User")

                        // Check if user was previously in chat room
                        val prefs = getSharedPreferences("compow_prefs", MODE_PRIVATE)
                        val wasInChatRoom = prefs.getBoolean("is_in_chat_room", false)

                        if (wasInChatRoom) {
                            isInChatRoom = true
                            withContext(Dispatchers.Main) {
                                updateNotification("Connected • Chat Room Active")
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                updateNotification("Connected • Ready for alerts")
                            }
                        }

                        Log.d("SocketForegroundService", "✅ Connected - User: $userId")
                    } else {
                        withContext(Dispatchers.Main) {
                            updateNotification("Connection failed")
                        }
                        Log.e("SocketForegroundService", "❌ Connection timeout")
                    }
                }
            } catch (e: Exception) {
                Log.e("SocketForegroundService", "❌ Error: ${e.message}")
                withContext(Dispatchers.Main) {
                    updateNotification("Connection error")
                }
            }
        }
    }

    private fun joinChatRoom() {
        serviceScope.launch {
            try {
                if (socketManager.isConnected.value && userId != null) {
                    socketManager.joinUserRoom(userId!!, userName ?: "User")
                    isInChatRoom = true

                    withContext(Dispatchers.Main) {
                        updateNotification("Connected • Chat Room Active")
                    }

                    Log.d("SocketForegroundService", "🚪 Joined chat room: $userId")
                }
            } catch (e: Exception) {
                Log.e("SocketForegroundService", "❌ Join room error: ${e.message}")
            }
        }
    }

    private fun leaveChatRoom() {
        serviceScope.launch {
            try {
                if (socketManager.isConnected.value && userId != null) {
                    socketManager.leaveUserRoom(userId!!)
                    isInChatRoom = false

                    withContext(Dispatchers.Main) {
                        updateNotification("Connected • Ready for alerts")
                    }

                    Log.d("SocketForegroundService", "🚪 Left chat room: $userId")
                }
            } catch (e: Exception) {
                Log.e("SocketForegroundService", "❌ Leave room error: ${e.message}")
            }
        }
    }

    private fun disconnectFromSocket() {
        serviceScope.launch {
            try {
                if (userId != null && socketManager.isConnected.value) {
                    socketManager.leaveUserRoom(userId!!)
                }
                socketManager.disconnect()
                Log.d("SocketForegroundService", "🔌 Disconnected")
            } catch (e: Exception) {
                Log.e("SocketForegroundService", "❌ Disconnect error: ${e.message}")
            }
        }
    }
}