// Socket.IO Server for ComPow Emergency Alert System
// Multi-User Support with Background Connectivity + Chat Feature
// Node.js + Express + Socket.IO

const express = require('express');
const app = express();
const http = require('http').createServer(app);
const io = require('socket.io')(http, {
    cors: {
        origin: "*",
        methods: ["GET", "POST"]
    }
});

const PORT = process.env.PORT || 3000;

// Store connected users: userId -> Set<socketId> (supports multiple devices)
const connectedUsers = new Map();
const userRooms = new Map(); // socketId -> userId
const userInfo = new Map(); // userId -> {userName, lastSeen}

// Middleware
app.use(express.json());

// Health check
app.get('/', (req, res) => {
    res.json({
        status: 'online',
        service: 'ComPow Socket.IO Server',
        connectedUsers: connectedUsers.size,
        totalConnections: Array.from(connectedUsers.values()).reduce((sum, set) => sum + set.size, 0),
        timestamp: new Date().toISOString()
    });
});

// Server stats
app.get('/stats', (req, res) => {
    const stats = {
        connectedUsers: connectedUsers.size,
        totalConnections: Array.from(connectedUsers.values()).reduce((sum, set) => sum + set.size, 0),
        rooms: userRooms.size,
        uptime: process.uptime(),
        timestamp: new Date().toISOString()
    };
    res.json(stats);
});

// Socket.IO Connection Handler
io.on('connection', (socket) => {
    console.log(`✅ Client connected: ${socket.id}`);

    // Join user room (supports multiple devices per user)
    socket.on('join_room', (data) => {
        const { userId, userName } = data;
        if (userId) {
            socket.join(userId);

            // Add socket to user's connection set
            if (!connectedUsers.has(userId)) {
                connectedUsers.set(userId, new Set());
            }
            connectedUsers.get(userId).add(socket.id);
            userRooms.set(socket.id, userId);

            // Store user info
            userInfo.set(userId, {
                userName: userName || 'User',
                lastSeen: new Date().toISOString()
            });

            const deviceCount = connectedUsers.get(userId).size;
            console.log(`👤 ${userName} (${userId}) joined - ${deviceCount} device(s) connected`);

            // Notify this socket
            socket.emit('connection_status', {
                success: true,
                message: 'Connected to ComPow server',
                userId: userId,
                deviceCount: deviceCount
            });

            // Broadcast online status to all OTHER users
            socket.broadcast.emit('user_status_changed', {
                userId: userId,
                userName: userName,
                status: 'online',
                timestamp: new Date().toISOString()
            });
        }
    });

    // Leave user room
    socket.on('leave_room', (data) => {
        const { userId } = data;
        if (userId && connectedUsers.has(userId)) {
            socket.leave(userId);
            connectedUsers.get(userId).delete(socket.id);

            if (connectedUsers.get(userId).size === 0) {
                connectedUsers.delete(userId);
            }

            userRooms.delete(socket.id);
            console.log(`👤 User ${userId} left room`);
        }
    });

    // Handle CHAT MESSAGE
    socket.on('chat_message', async (data, callback) => {
        const {
            fromUserId,
            fromUserName,
            message,
            contactIds,
            timestamp
        } = data;

        console.log(`💬 Chat message from ${fromUserName} (${fromUserId})`);

        // Parse contactIds (handle both array and JSON string)
        const contacts = Array.isArray(contactIds) ? contactIds : JSON.parse(contactIds);
        console.log(`👥 Sending to ${contacts.length} contacts`);

        let successCount = 0;
        let failCount = 0;
        const deliveryStatus = [];

        // Send message to each contact (all their devices)
        for (const contactId of contacts) {
            if (connectedUsers.has(contactId) && connectedUsers.get(contactId).size > 0) {
                // Contact is online - send to ALL their devices
                io.to(contactId).emit('chat_message_received', {
                    fromUserId: fromUserId,
                    fromUserName: fromUserName,
                    message: message,
                    timestamp: timestamp
                });

                const deviceCount = connectedUsers.get(contactId).size;
                successCount++;
                deliveryStatus.push({
                    contactId: contactId,
                    status: 'delivered',
                    devices: deviceCount
                });
                console.log(`✅ Message sent to ${contactId} (${deviceCount} devices online)`);
            } else {
                // Contact is offline
                failCount++;
                deliveryStatus.push({
                    contactId: contactId,
                    status: 'offline'
                });
                console.log(`❌ ${contactId} is offline`);
            }
        }

        // Send response back to sender
        if (callback) {
            callback({
                success: true,
                delivered: successCount,
                failed: failCount,
                total: contacts.length,
                deliveryStatus: deliveryStatus,
                message: `Message sent to ${successCount}/${contacts.length} contacts`
            });
        }

        console.log(`📊 Chat Summary: ${successCount} delivered, ${failCount} failed`);
    });

    // Handle EMERGENCY ALERT
    socket.on('emergency_alert', async (data, callback) => {
        const {
            fromUserId,
            fromUserName,
            message,
            latitude,
            longitude,
            contactIds,
            timestamp
        } = data;

        console.log(`🚨 EMERGENCY ALERT from ${fromUserName} (${fromUserId})`);
        console.log(`📍 Location: ${latitude}, ${longitude}`);

        // Parse contactIds (handle both array and JSON string)
        const contacts = Array.isArray(contactIds) ? contactIds : JSON.parse(contactIds);
        console.log(`👥 Notifying ${contacts.length} contacts`);

        let successCount = 0;
        let failCount = 0;
        const deliveryStatus = [];

        // Send alert to each contact (all their devices)
        for (const contactId of contacts) {
            if (connectedUsers.has(contactId) && connectedUsers.get(contactId).size > 0) {
                // Contact is online - send to ALL their devices
                io.to(contactId).emit('emergency_alert_received', {
                    fromUserId: fromUserId,
                    fromUserName: fromUserName,
                    message: message,
                    latitude: latitude,
                    longitude: longitude,
                    timestamp: timestamp,
                    alertType: 'emergency'
                });

                const deviceCount = connectedUsers.get(contactId).size;
                successCount++;
                deliveryStatus.push({
                    contactId: contactId,
                    status: 'delivered',
                    devices: deviceCount
                });
                console.log(`✅ Sent to ${contactId} (${deviceCount} devices online)`);
            } else {
                // Contact is offline
                failCount++;
                deliveryStatus.push({
                    contactId: contactId,
                    status: 'offline'
                });
                console.log(`❌ ${contactId} is offline`);
            }
        }

        // Send response back to sender
        if (callback) {
            callback({
                success: true,
                delivered: successCount,
                failed: failCount,
                total: contacts.length,
                deliveryStatus: deliveryStatus,
                message: `Alert sent to ${successCount}/${contacts.length} contacts`
            });
        }

        console.log(`📊 Alert Summary: ${successCount} delivered, ${failCount} failed`);
    });

    // Handle LIVE LOCATION UPDATES
    socket.on('live_location_update', (data) => {
        const { fromUserId, fromUserName, latitude, longitude, contactIds } = data;

        const contacts = Array.isArray(contactIds) ? contactIds : JSON.parse(contactIds);

        // Forward live location to all intended recipients (all their devices)
        for (const contactId of contacts) {
            if (connectedUsers.has(contactId) && connectedUsers.get(contactId).size > 0) {
                io.to(contactId).emit('contact_live_location', {
                    fromUserId: fromUserId,
                    fromUserName: fromUserName,
                    latitude: latitude,
                    longitude: longitude,
                    timestamp: new Date().getTime()
                });
            }
        }
    });

    // Handle SAFE ALERT
    socket.on('safe_alert', async (data, callback) => {
        const {
            fromUserId,
            fromUserName,
            message,
            contactIds,
            timestamp
        } = data;

        console.log(`✅ SAFE ALERT from ${fromUserName} (${fromUserId})`);

        const contacts = Array.isArray(contactIds) ? contactIds : JSON.parse(contactIds);
        let successCount = 0;

        // Send safe message to each contact (all their devices)
        for (const contactId of contacts) {
            if (connectedUsers.has(contactId) && connectedUsers.get(contactId).size > 0) {
                io.to(contactId).emit('safe_alert_received', {
                    fromUserId: fromUserId,
                    fromUserName: fromUserName,
                    message: message,
                    timestamp: timestamp,
                    alertType: 'safe'
                });
                successCount++;
                console.log(`✅ Safe message sent to ${contactId} (${connectedUsers.get(contactId).size} devices)`);
            }
        }

        if (callback) {
            callback({
                success: true,
                delivered: successCount,
                total: contacts.length,
                message: `Safe alert sent to ${successCount}/${contacts.length} contacts`
            });
        }
    });

    // Handle disconnect
    socket.on('disconnect', () => {
        const userId = userRooms.get(socket.id);

        if (userId) {
            userRooms.delete(socket.id);

            // Remove specific socket ID from user's connection set
            if (connectedUsers.has(userId)) {
                connectedUsers.get(userId).delete(socket.id);

                // Only mark user as offline if ALL their devices disconnected
                if (connectedUsers.get(userId).size === 0) {
                    connectedUsers.delete(userId);

                    const info = userInfo.get(userId);
                    const userName = info ? info.userName : userId;

                    console.log(`❌ ${userName} (${userId}) fully disconnected. All devices offline.`);

                    // Broadcast offline status only when truly offline
                    socket.broadcast.emit('user_status_changed', {
                        userId: userId,
                        userName: userName,
                        status: 'offline',
                        timestamp: new Date().toISOString()
                    });
                } else {
                    console.log(`❌ Socket ${socket.id} disconnected. User ${userId} still has ${connectedUsers.get(userId).size} device(s) online.`);
                }
            }
        } else {
            console.log(`❌ Client disconnected: ${socket.id}`);
        }
    });

    // Handle errors
    socket.on('error', (error) => {
        console.error(`⚠️ Socket error for ${socket.id}: ${error.message}`);
    });
});

// Start server
http.listen(PORT, '0.0.0.0', () => {
    console.log('\n==========================================');
    console.log('🚀 ComPow Socket.IO Server Started');
    console.log('==========================================');
    console.log(`📡 Server running on port ${PORT}`);
    console.log(`🌐 Local: http://localhost:${PORT}`);
    console.log(`🌐 Network: http://YOUR_IP:${PORT}`);
    console.log(`⏰ Started at: ${new Date().toISOString()}`);
    console.log(`✨ Multi-user, chat & background support enabled`);
    console.log('==========================================\n');
});

// Graceful shutdown
process.on('SIGTERM', () => {
    console.log('⚠️ SIGTERM received, closing server...');
    http.close(() => {
        console.log('✅ Server closed');
        process.exit(0);
    });
});

process.on('SIGINT', () => {
    console.log('\n⚠️ SIGINT received, closing server...');
    http.close(() => {
        console.log('✅ Server closed');
        process.exit(0);
    });
});