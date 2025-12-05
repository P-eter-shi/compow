package com.example.compow.network

import android.util.Log
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.emitter.Emitter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.net.URISyntaxException

class SocketIOManager private constructor() {

    private var socket: Socket? = null

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    // StateFlow for received alerts
    private val _emergencyAlertReceived = MutableStateFlow<EmergencyAlertData?>(null)
    val emergencyAlertReceived: StateFlow<EmergencyAlertData?> = _emergencyAlertReceived.asStateFlow()

    private val _safeAlertReceived = MutableStateFlow<SafeAlertData?>(null)
    val safeAlertReceived: StateFlow<SafeAlertData?> = _safeAlertReceived.asStateFlow()

    private val _contactLiveLocation = MutableStateFlow<ContactLocationData?>(null)
    val contactLiveLocation: StateFlow<ContactLocationData?> = _contactLiveLocation.asStateFlow()

    // NEW: StateFlow for chat messages
    private val _chatMessageReceived = MutableStateFlow<ChatMessageData?>(null)
    val chatMessageReceived: StateFlow<ChatMessageData?> = _chatMessageReceived.asStateFlow()

    companion object {
        @Volatile
        private var INSTANCE: SocketIOManager? = null

        // Server URL
        private const val SERVER_URL = "http://10.10.111.246:3000"

        // Socket Events
        const val EVENT_CONNECT = Socket.EVENT_CONNECT
        const val EVENT_DISCONNECT = Socket.EVENT_DISCONNECT
        const val EVENT_CONNECT_ERROR = Socket.EVENT_CONNECT_ERROR
        const val EVENT_JOIN_ROOM = "join_room"
        const val EVENT_LEAVE_ROOM = "leave_room"
        const val EVENT_EMERGENCY_ALERT = "emergency_alert"
        const val EVENT_SAFE_ALERT = "safe_alert"
        const val EVENT_LIVE_LOCATION_UPDATE = "live_location_update"
        const val EVENT_EMERGENCY_ALERT_RECEIVED = "emergency_alert_received"
        const val EVENT_SAFE_ALERT_RECEIVED = "safe_alert_received"
        const val EVENT_CONTACT_LIVE_LOCATION = "contact_live_location"
        const val EVENT_USER_STATUS_CHANGED = "user_status_changed"
        const val EVENT_CHAT_MESSAGE = "chat_message"
        const val EVENT_CHAT_MESSAGE_RECEIVED = "chat_message_received"

        fun getInstance(): SocketIOManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SocketIOManager().also {
                    INSTANCE = it
                }
            }
        }
    }

    // Data classes
    data class EmergencyAlertData(
        val fromUserId: String,
        val fromUserName: String,
        val message: String,
        val latitude: Double,
        val longitude: Double,
        val timestamp: Long
    )

    data class SafeAlertData(
        val fromUserId: String,
        val fromUserName: String,
        val message: String,
        val timestamp: Long
    )

    data class ContactLocationData(
        val fromUserId: String,
        val fromUserName: String?,
        val latitude: Double,
        val longitude: Double,
        val timestamp: Long
    )

    data class ChatMessageData(
        val fromUserId: String,
        val fromUserName: String,
        val message: String,
        val timestamp: Long
    )

    // Event Listeners
    private val onConnect = Emitter.Listener {
        _isConnected.value = true
        Log.d("SocketIOManager", "✅ Socket connected")
    }

    private val onDisconnect = Emitter.Listener {
        _isConnected.value = false
        Log.d("SocketIOManager", "❌ Socket disconnected")
    }

    private val onConnectError = Emitter.Listener { args ->
        _isConnected.value = false
        val error = if (args.isNotEmpty()) args[0].toString() else "Unknown error"
        Log.e("SocketIOManager", "❌ Connection error: $error")
    }

    private val onEmergencyAlertReceived = Emitter.Listener { args ->
        if (args.isNotEmpty()) {
            try {
                val data = args[0] as? JSONObject
                if (data != null) {
                    val alertData = EmergencyAlertData(
                        fromUserId = data.optString("fromUserId", ""),
                        fromUserName = data.optString("fromUserName", "Unknown"),
                        message = data.optString("message", ""),
                        latitude = data.optDouble("latitude", 0.0),
                        longitude = data.optDouble("longitude", 0.0),
                        timestamp = data.optLong("timestamp", System.currentTimeMillis())
                    )

                    CoroutineScope(Dispatchers.Main).launch {
                        _emergencyAlertReceived.value = alertData
                        Log.d("SocketIOManager", "🚨 Emergency alert from: ${alertData.fromUserName}")
                    }
                }
            } catch (e: Exception) {
                Log.e("SocketIOManager", "❌ Error parsing alert: ${e.message}")
            }
        }
    }

    private val onSafeAlertReceived = Emitter.Listener { args ->
        if (args.isNotEmpty()) {
            try {
                val data = args[0] as? JSONObject
                if (data != null) {
                    val alertData = SafeAlertData(
                        fromUserId = data.optString("fromUserId", ""),
                        fromUserName = data.optString("fromUserName", "Unknown"),
                        message = data.optString("message", ""),
                        timestamp = data.optLong("timestamp", System.currentTimeMillis())
                    )

                    CoroutineScope(Dispatchers.Main).launch {
                        _safeAlertReceived.value = alertData
                        Log.d("SocketIOManager", "✅ Safe alert from: ${alertData.fromUserName}")
                    }
                }
            } catch (e: Exception) {
                Log.e("SocketIOManager", "❌ Error parsing safe alert: ${e.message}")
            }
        }
    }

    private val onContactLiveLocation = Emitter.Listener { args ->
        if (args.isNotEmpty()) {
            try {
                val data = args[0] as? JSONObject
                if (data != null) {
                    val locationData = ContactLocationData(
                        fromUserId = data.optString("fromUserId", ""),
                        fromUserName = data.optString("fromUserName"),
                        latitude = data.optDouble("latitude", 0.0),
                        longitude = data.optDouble("longitude", 0.0),
                        timestamp = data.optLong("timestamp", System.currentTimeMillis())
                    )

                    CoroutineScope(Dispatchers.Main).launch {
                        _contactLiveLocation.value = locationData
                        Log.d("SocketIOManager", "🌍 Location from: ${locationData.fromUserId}")
                    }
                }
            } catch (e: Exception) {
                Log.e("SocketIOManager", "❌ Error parsing location: ${e.message}")
            }
        }
    }

    private val onChatMessageReceived = Emitter.Listener { args ->
        if (args.isNotEmpty()) {
            try {
                val data = args[0] as? JSONObject
                if (data != null) {
                    val messageData = ChatMessageData(
                        fromUserId = data.optString("fromUserId", ""),
                        fromUserName = data.optString("fromUserName", "Unknown"),
                        message = data.optString("message", ""),
                        timestamp = data.optLong("timestamp", System.currentTimeMillis())
                    )

                    CoroutineScope(Dispatchers.Main).launch {
                        _chatMessageReceived.value = messageData
                        Log.d("SocketIOManager", "💬 Chat message from: ${messageData.fromUserName}")
                    }
                }
            } catch (e: Exception) {
                Log.e("SocketIOManager", "❌ Error parsing chat message: ${e.message}")
            }
        }
    }

    private val onUserStatusChanged = Emitter.Listener { args ->
        if (args.isNotEmpty()) {
            try {
                val data = args[0] as? JSONObject
                if (data != null) {
                    val userId = data.optString("userId", "")
                    val userName = data.optString("userName", "Unknown")
                    val status = data.optString("status", "offline")
                    Log.d("SocketIOManager", "👤 $userName ($userId) is now $status")
                }
            } catch (e: Exception) {
                Log.e("SocketIOManager", "❌ Error parsing status: ${e.message}")
            }
        }
    }

    init {
        try {
            val opts = IO.Options().apply {
                reconnection = true
                reconnectionAttempts = Integer.MAX_VALUE
                reconnectionDelay = 1000
                reconnectionDelayMax = 5000
                timeout = 20000
                transports = arrayOf("websocket")
            }

            socket = IO.socket(SERVER_URL, opts)
            setupSocketListeners()
            Log.d("SocketIOManager", "📡 SocketIO initialized: $SERVER_URL")

        } catch (e: URISyntaxException) {
            Log.e("SocketIOManager", "❌ Init error: ${e.message}")
        }
    }

    private fun setupSocketListeners() {
        socket?.apply {
            on(EVENT_CONNECT, onConnect)
            on(EVENT_DISCONNECT, onDisconnect)
            on(EVENT_CONNECT_ERROR, onConnectError)
            on(EVENT_EMERGENCY_ALERT_RECEIVED, onEmergencyAlertReceived)
            on(EVENT_SAFE_ALERT_RECEIVED, onSafeAlertReceived)
            on(EVENT_CONTACT_LIVE_LOCATION, onContactLiveLocation)
            on(EVENT_USER_STATUS_CHANGED, onUserStatusChanged)
            on(EVENT_CHAT_MESSAGE_RECEIVED, onChatMessageReceived)
        }
    }

    fun connect() {
        if (!_isConnected.value) {
            socket?.connect()
            Log.d("SocketIOManager", "🔄 Connecting to: $SERVER_URL")
        } else {
            Log.d("SocketIOManager", "ℹ️ Already connected")
        }
    }

    fun disconnect() {
        Log.d("SocketIOManager", "🔄 Disconnecting...")
        socket?.disconnect()
        _isConnected.value = false
    }

    fun joinUserRoom(userId: String, userName: String) {
        if (_isConnected.value) {
            val data = JSONObject().apply {
                put("userId", userId)
                put("userName", userName)
            }
            socket?.emit(EVENT_JOIN_ROOM, data)
            Log.d("SocketIOManager", "🚪 Joined room: $userId as $userName")
        } else {
            Log.w("SocketIOManager", "⚠️ Cannot join - not connected")
        }
    }

    fun leaveUserRoom(userId: String) {
        if (_isConnected.value) {
            val data = JSONObject().apply {
                put("userId", userId)
            }
            socket?.emit(EVENT_LEAVE_ROOM, data)
            Log.d("SocketIOManager", "🚪 Left room: $userId")
        }
    }

    fun sendChatMessage(
        fromUserId: String,
        fromUserName: String,
        message: String,
        contactIds: List<String>,
        callback: (Boolean, String?) -> Unit
    ) {
        if (!_isConnected.value) {
            CoroutineScope(Dispatchers.Main).launch {
                callback(false, "Not connected")
            }
            return
        }

        try {
            val data = JSONObject().apply {
                put("fromUserId", fromUserId)
                put("fromUserName", fromUserName)
                put("message", message)
                put("contactIds", JSONArray(contactIds))
                put("timestamp", System.currentTimeMillis())
            }

            socket?.emit(EVENT_CHAT_MESSAGE, data, io.socket.client.Ack { args ->
                CoroutineScope(Dispatchers.Main).launch {
                    if (args.isNotEmpty()) {
                        val response = args[0] as? JSONObject
                        val success = response?.optBoolean("success", false) ?: false
                        val responseMsg = response?.optString("message")
                        callback(success, responseMsg)
                    } else {
                        callback(false, "No response")
                    }
                }
            })

            Log.d("SocketIOManager", "💬 Chat message sent to ${contactIds.size} contacts")

        } catch (e: Exception) {
            Log.e("SocketIOManager", "❌ Error: ${e.message}")
            CoroutineScope(Dispatchers.Main).launch {
                callback(false, e.message)
            }
        }
    }

    fun sendEmergencyAlert(
        fromUserId: String,
        fromUserName: String,
        message: String,
        latitude: Double?,
        longitude: Double?,
        contactIds: List<String>,
        callback: (Boolean, String?) -> Unit
    ) {
        if (!_isConnected.value) {
            CoroutineScope(Dispatchers.Main).launch {
                callback(false, "Not connected")
            }
            return
        }

        try {
            val data = JSONObject().apply {
                put("fromUserId", fromUserId)
                put("fromUserName", fromUserName)
                put("message", message)
                put("latitude", latitude ?: 0.0)
                put("longitude", longitude ?: 0.0)
                put("contactIds", JSONArray(contactIds))
                put("timestamp", System.currentTimeMillis())
            }

            socket?.emit(EVENT_EMERGENCY_ALERT, data, io.socket.client.Ack { args ->
                CoroutineScope(Dispatchers.Main).launch {
                    if (args.isNotEmpty()) {
                        val response = args[0] as? JSONObject
                        val success = response?.optBoolean("success", false) ?: false
                        val responseMsg = response?.optString("message")
                        callback(success, responseMsg)
                        Log.d("SocketIOManager", "📨 Response: $responseMsg")
                    } else {
                        callback(false, "No response")
                    }
                }
            })

            Log.d("SocketIOManager", "🚨 Alert sent to ${contactIds.size} contacts")

        } catch (e: Exception) {
            Log.e("SocketIOManager", "❌ Error: ${e.message}")
            CoroutineScope(Dispatchers.Main).launch {
                callback(false, e.message)
            }
        }
    }

    fun sendLiveLocationUpdate(
        fromUserId: String,
        fromUserName: String?,
        latitude: Double,
        longitude: Double,
        contactIds: List<String>
    ) {
        if (!_isConnected.value) {
            Log.w("SocketIOManager", "⚠️ Cannot send location - not connected")
            return
        }

        try {
            val data = JSONObject().apply {
                put("fromUserId", fromUserId)
                if (fromUserName != null) {
                    put("fromUserName", fromUserName)
                }
                put("latitude", latitude)
                put("longitude", longitude)
                put("contactIds", JSONArray(contactIds))
                put("timestamp", System.currentTimeMillis())
            }

            socket?.emit(EVENT_LIVE_LOCATION_UPDATE, data)

        } catch (e: Exception) {
            Log.e("SocketIOManager", "❌ Location error: ${e.message}")
        }
    }

    fun sendSafeAlert(
        fromUserId: String,
        fromUserName: String,
        message: String,
        contactIds: List<String>,
        callback: (Boolean, String?) -> Unit
    ) {
        if (!_isConnected.value) {
            CoroutineScope(Dispatchers.Main).launch {
                callback(false, "Not connected")
            }
            return
        }

        try {
            val data = JSONObject().apply {
                put("fromUserId", fromUserId)
                put("fromUserName", fromUserName)
                put("message", message)
                put("contactIds", JSONArray(contactIds))
                put("timestamp", System.currentTimeMillis())
            }

            socket?.emit(EVENT_SAFE_ALERT, data, io.socket.client.Ack { args ->
                CoroutineScope(Dispatchers.Main).launch {
                    if (args.isNotEmpty()) {
                        val response = args[0] as? JSONObject
                        val success = response?.optBoolean("success", false) ?: false
                        val responseMsg = response?.optString("message")
                        callback(success, responseMsg)
                    } else {
                        callback(false, "No response")
                    }
                }
            })

            Log.d("SocketIOManager", "✅ Safe alert sent to ${contactIds.size}")

        } catch (e: Exception) {
            Log.e("SocketIOManager", "❌ Error: ${e.message}")
            CoroutineScope(Dispatchers.Main).launch {
                callback(false, e.message)
            }
        }
    }

    fun clearEmergencyAlert() {
        _emergencyAlertReceived.value = null
    }

    fun clearSafeAlert() {
        _safeAlertReceived.value = null
    }

    fun clearContactLocation() {
        _contactLiveLocation.value = null
    }

    fun clearChatMessage() {
        _chatMessageReceived.value = null
    }
}