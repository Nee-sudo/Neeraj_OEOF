package com.example.data

import android.util.Log
import com.squareup.moshi.Moshi
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject
import java.net.URISyntaxException

object SocketManager {
    private const val TAG = "SocketManager"
    private var socket: Socket? = null
    private val moshi: Moshi = ApiClient.moshi

    interface SocketEventListener {
        fun onNewMessage(message: ChatMessageEntity)
        fun onMessageDelivered(roomId: Int, timestamp: Long, senderName: String)
        fun onMessageRead(roomId: Int, timestamp: Long, senderName: String)
        fun onTyping(roomId: Int, senderName: String)
        fun onStopTyping(roomId: Int, senderName: String)
        fun onConnect()
        fun onDisconnect()
    }

    private var listener: SocketEventListener? = null
    private val joinedRooms = mutableSetOf<Int>()
    private var currentUrl: String? = null

    fun setListener(listener: SocketEventListener) {
        this.listener = listener
    }

    fun connect(baseUrl: String) {
        if (socket != null && socket!!.connected() && currentUrl == baseUrl) {
            return
        }
        disconnect()

        try {
            val opts = IO.Options()
            opts.forceNew = true
            opts.reconnection = true
            
            socket = IO.socket(baseUrl, opts)
            currentUrl = baseUrl

            socket?.on(Socket.EVENT_CONNECT) {
                Log.d(TAG, "Socket Connected")
                listener?.onConnect()
                synchronized(joinedRooms) {
                    joinedRooms.forEach { roomId ->
                        val data = JSONObject()
                        data.put("roomId", roomId)
                        socket?.emit("join_room", data)
                    }
                }
            }

            socket?.on(Socket.EVENT_DISCONNECT) {
                Log.d(TAG, "Socket Disconnected")
                listener?.onDisconnect()
            }

            socket?.on(Socket.EVENT_CONNECT_ERROR) { args ->
                Log.e(TAG, "Socket Connect Error: ${args.getOrNull(0)}")
            }

            socket?.on("new_message") { args ->
                val data = args[0] as JSONObject
                try {
                    val adapter = moshi.adapter(ChatMessageEntity::class.java)
                    val message = adapter.fromJson(data.toString())
                    if (message != null) {
                        listener?.onNewMessage(message)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing new_message", e)
                }
            }

            socket?.on("message_delivered") { args ->
                val data = args[0] as JSONObject
                val roomId = data.getInt("roomId")
                val timestamp = data.getLong("timestamp")
                val senderName = data.getString("senderName")
                listener?.onMessageDelivered(roomId, timestamp, senderName)
            }

            socket?.on("message_read") { args ->
                val data = args[0] as JSONObject
                val roomId = data.getInt("roomId")
                val timestamp = data.getLong("timestamp")
                val senderName = data.getString("senderName")
                listener?.onMessageRead(roomId, timestamp, senderName)
            }

            socket?.on("typing") { args ->
                val data = args[0] as JSONObject
                val roomId = data.getInt("roomId")
                val senderName = data.getString("senderName")
                listener?.onTyping(roomId, senderName)
            }

            socket?.on("typing_stop") { args ->
                val data = args[0] as JSONObject
                val roomId = data.getInt("roomId")
                val senderName = data.getString("senderName")
                listener?.onStopTyping(roomId, senderName)
            }

            socket?.connect()
        } catch (e: URISyntaxException) {
            Log.e(TAG, "Socket connection failed", e)
        }
    }

    fun disconnect() {
        socket?.disconnect()
        socket?.off()
        socket = null
        currentUrl = null
        synchronized(joinedRooms) {
            joinedRooms.clear()
        }
    }

    fun joinRoom(roomId: Int) {
        synchronized(joinedRooms) {
            joinedRooms.add(roomId)
        }
        val data = JSONObject()
        data.put("roomId", roomId)
        socket?.emit("join_room", data)
        Log.d(TAG, "Joined room: $roomId")
    }

    fun leaveRoom(roomId: Int) {
        synchronized(joinedRooms) {
            joinedRooms.remove(roomId)
        }
        val data = JSONObject()
        data.put("roomId", roomId)
        socket?.emit("leave_room", data)
        Log.d(TAG, "Left room: $roomId")
    }

    fun sendMessage(message: ChatMessageEntity) {
        try {
            val adapter = moshi.adapter(ChatMessageEntity::class.java)
            val jsonStr = adapter.toJson(message)
            socket?.emit("send_message", JSONObject(jsonStr))
            Log.d(TAG, "Sent message: $jsonStr")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending message", e)
        }
    }

    fun sendTyping(roomId: Int, senderName: String) {
        val data = JSONObject()
        data.put("roomId", roomId)
        data.put("senderName", senderName)
        socket?.emit("typing", data)
    }

    fun sendStopTyping(roomId: Int, senderName: String) {
        val data = JSONObject()
        data.put("roomId", roomId)
        data.put("senderName", senderName)
        socket?.emit("typing_stop", data)
    }

    fun sendReadReceipt(roomId: Int, timestamp: Long, senderName: String) {
        val data = JSONObject()
        data.put("roomId", roomId)
        data.put("timestamp", timestamp)
        data.put("senderName", senderName)
        socket?.emit("message_read", data)
    }

    fun sendDeliveryReceipt(roomId: Int, timestamp: Long, senderName: String) {
        val data = JSONObject()
        data.put("roomId", roomId)
        data.put("timestamp", timestamp)
        data.put("senderName", senderName)
        socket?.emit("message_delivered", data)
    }
}
