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

    companion object {
        @Volatile
        private var INSTANCE: SocketIOManager? = null

        //server URL
        private const val SERVER_URL = "http://10.10.44.98:3000"

        const val EVENT_CONNECT = Socket.EVENT_CONNECT
        const val EVENT_DISCONNECT = Socket.EVENT_DISCONNECT
        const val EVENT_CONNECT_ERROR = Socket.EVENT_CONNECT_ERROR
        const val EVENT_EMERGENCY_ALERT = "emergency_alert"
        const val EVENT_SAFE_ALERT = "safe_alert"
        const val EVENT_JOIN_ROOM = "join_room" // Corrected this line
        const val EVENT_USER_ONLINE = "user_online"
        const val EVENT_USER_OFFLINE = "user_offline"

        fun getInstance(): SocketIOManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SocketIOManager().also {
                    INSTANCE = it
                }
            }
        }
    }

    private val onConnect = Emitter.Listener {
        _isConnected.value = true
        Log.d("SocketIOManager", "✅ Socket connected successfully")
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

    private val onEmergencyAlert = Emitter.Listener { args ->
        if (args.isNotEmpty()) {
            val data = args[0] as? JSONObject
            Log.d("SocketIOManager", "🚨 Emergency alert received: $data")
            CoroutineScope(Dispatchers.Main).launch {
                // Handle emergency alert
            }
        }
    }

    private val onSafeAlert = Emitter.Listener { args ->
        if (args.isNotEmpty()) {
            val data = args[0] as? JSONObject
            Log.d("SocketIOManager", "✅ Safe alert received: $data")
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
            }

            socket = IO.socket(SERVER_URL, opts)
            setupSocketListeners()
            Log.d("SocketIOManager", "📡 SocketIO initialized with URL: $SERVER_URL")

        } catch (e: URISyntaxException) {
            Log.e("SocketIOManager", "❌ Socket initialization error: ${e.message}")
        }
    }

    private fun setupSocketListeners() {
        socket?.apply {
            on(EVENT_CONNECT, onConnect)
            on(EVENT_DISCONNECT, onDisconnect)
            on(EVENT_CONNECT_ERROR, onConnectError)
            on(EVENT_EMERGENCY_ALERT, onEmergencyAlert)
            on(EVENT_SAFE_ALERT, onSafeAlert)
        }
    }

    fun connect() {
        if (!_isConnected.value) {
            socket?.connect()
            Log.d("SocketIOManager", "🔄 Connecting to server: $SERVER_URL")
        } else {
            Log.d("SocketIOManager", "ℹ️ Already connected")
        }
    }

    fun disconnect() {
        Log.d("SocketIOManager", "🔄 Disconnecting from server...")
        socket?.disconnect()
        _isConnected.value = false
    }

    fun joinUserRoom(userId: String) {
        if (_isConnected.value) {
            val data = JSONObject().apply {
                put("userId", userId)
            }
            socket?.emit(EVENT_JOIN_ROOM, data)
            Log.d("SocketIOManager", "🚪 Joined room: $userId")
        } else {
            Log.w("SocketIOManager", "⚠️ Cannot join room - not connected")
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
                callback(false, "Not connected to server")
            }
            return
        }

        try {
            // Create JSONArray for contactIds
            val contactsArray = JSONArray()
            contactIds.forEach { contactsArray.put(it) }

            val data = JSONObject().apply {
                put("fromUserId", fromUserId)
                put("fromUserName", fromUserName)
                put("message", message)
                put("latitude", latitude ?: 0.0)
                put("longitude", longitude ?: 0.0)
                put("contactIds", contactsArray.toString()) // Convert to string for server
                put("timestamp", System.currentTimeMillis())
            }

            socket?.emit(EVENT_EMERGENCY_ALERT, data, io.socket.client.Ack { args ->
                CoroutineScope(Dispatchers.Main).launch {
                    if (args.isNotEmpty()) {
                        val response = args[0] as? JSONObject
                        val success = response?.optBoolean("success", false) ?: false
                        val responseMsg = response?.optString("message")
                        callback(success, responseMsg)
                        Log.d("SocketIOManager", "📨 Server response: $responseMsg")
                    } else {
                        callback(false, "No response from server")
                    }
                }
            })

            Log.d("SocketIOManager", "🚨 Emergency alert sent to ${contactIds.size} contacts")

        } catch (e: Exception) {
            Log.e("SocketIOManager", "❌ Error sending emergency alert: ${e.message}")
            CoroutineScope(Dispatchers.Main).launch {
                callback(false, e.message)
            }
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
                callback(false, "Not connected to server")
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
                put("type", "safe")
            }

            socket?.emit(EVENT_SAFE_ALERT, data, io.socket.client.Ack { args ->
                CoroutineScope(Dispatchers.Main).launch {
                    if (args.isNotEmpty()) {
                        val response = args[0] as? JSONObject
                        val success = response?.optBoolean("success", false) ?: false
                        val error = response?.optString("error")
                        callback(success, error)
                    } else {
                        callback(false, "No response from server")
                    }
                }
            })

            Log.d("SocketIOManager", "✅ Safe alert sent to ${contactIds.size} contacts")

        } catch (e: Exception) {
            Log.e("SocketIOManager", "❌ Error sending safe alert: ${e.message}")
            CoroutineScope(Dispatchers.Main).launch {
                callback(false, e.message)
            }
        }
    }

    fun setUserOnline(userId: String, userName: String) {
        if (_isConnected.value) {
            val data = JSONObject().apply {
                put("userId", userId)
                put("userName", userName)
            }
            socket?.emit(EVENT_USER_ONLINE, data)
            Log.d("SocketIOManager", "🟢 User $userName is now online")
        } else {
            Log.w("SocketIOManager", "⚠️ Cannot set user online - not connected")
        }
    }

    fun setUserOffline(userId: String) {
        if (_isConnected.value) {
            val data = JSONObject().apply {
                put("userId", userId)
            }
            socket?.emit(EVENT_USER_OFFLINE, data)
            Log.d("SocketIOManager", "🔴 User $userId is now offline")
        }
    }
}
