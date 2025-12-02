// Socket.IO Server for ComPow Emergency Alert System
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

// Store connected users and their socket IDs
const connectedUsers = new Map();
const userRooms = new Map();

// Middleware
app.use(express.json());

// Basic health check endpoint
app.get('/', (req, res) => {
    res.json({
        status: 'online',
        service: 'ComPow Socket.IO Server',
        connectedUsers: connectedUsers.size,
        timestamp: new Date().toISOString()
    });
});

// Get server stats
app.get('/stats', (req, res) => {
    res.json({
        connectedUsers: connectedUsers.size,
        rooms: userRooms.size,
        uptime: process.uptime(),
        timestamp: new Date().toISOString()
    });
});

// Socket.IO Connection Handler
io.on('connection', (socket) => {
    console.log(`✅ Client connected: ${socket.id}`);

    // Join user room for private messages
    socket.on('join_room', (data) => {
        const { userId } = data;
        if (userId) {
            socket.join(userId);
            connectedUsers.set(userId, socket.id);
            userRooms.set(socket.id, userId);
            console.log(`👤 User ${userId} joined room`);

            // Notify user they're connected
            socket.emit('connection_status', {
                success: true,
                message: 'Connected to ComPow server',
                userId: userId
            });
        }
    });

    // Leave user room
    socket.on('leave_room', (data) => {
        const { userId } = data;
        if (userId) {
            socket.leave(userId);
            connectedUsers.delete(userId);
            userRooms.delete(socket.id);
            console.log(`👤 User ${userId} left room`);
        }
    });

    // Handle user online status
    socket.on('user_online', (data) => {
        const { userId, userName } = data;
        connectedUsers.set(userId, socket.id);
        console.log(`🟢 ${userName} (${userId}) is now online`);

        // Broadcast to all connected clients
        socket.broadcast.emit('user_status_changed', {
            userId: userId,
            userName: userName,
            status: 'online',
            timestamp: new Date().toISOString()
        });
    });

    // Handle user offline status
    socket.on('user_offline', (data) => {
        const { userId } = data;
        connectedUsers.delete(userId);
        console.log(`🔴 User ${userId} is now offline`);

        // Broadcast to all connected clients
        socket.broadcast.emit('user_status_changed', {
            userId: userId,
            status: 'offline',
            timestamp: new Date().toISOString()
        });
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
        console.log(`👥 Notifying ${JSON.parse(contactIds).length} contacts`);

        const contacts = JSON.parse(contactIds);
        let successCount = 0;
        let failCount = 0;

        // Send alert to each contact
        for (const contactId of contacts) {
            const socketId = connectedUsers.get(contactId);

            if (socketId) {
                // Contact is online, send via Socket.IO
                io.to(contactId).emit('emergency_alert_received', {
                    fromUserId: fromUserId,
                    fromUserName: fromUserName,
                    message: message,
                    latitude: latitude,
                    longitude: longitude,
                    timestamp: timestamp,
                    alertType: 'emergency'
                });
                successCount++;
                console.log(`✅ Sent to ${contactId} (online)`);
            } else {
                // Contact is offline, would send push notification or SMS
                failCount++;
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
                message: `Alert sent to ${successCount}/${contacts.length} contacts`
            });
        }

        // Log alert to database (you can add database integration here)
        console.log(`📊 Alert Summary: ${successCount} delivered, ${failCount} failed`);
    });

    // Handle SAFE ALERT (when danger is over)
    socket.on('safe_alert', async (data, callback) => {
        const {
            fromUserId,
            fromUserName,
            message,
            contactIds,
            timestamp
        } = data;

        console.log(`✅ SAFE ALERT from ${fromUserName} (${fromUserId})`);

        const contacts = JSON.parse(contactIds);
        let successCount = 0;

        // Send safe message to each contact
        for (const contactId of contacts) {
            const socketId = connectedUsers.get(contactId);

            if (socketId) {
                io.to(contactId).emit('safe_alert_received', {
                    fromUserId: fromUserId,
                    fromUserName: fromUserName,
                    message: message,
                    timestamp: timestamp,
                    alertType: 'safe'
                });
                successCount++;
                console.log(`✅ Safe message sent to ${contactId}`);
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

    // Handle regular MESSAGE
    socket.on('message', (data, callback) => {
        const {
            toUserId,
            fromUserId,
            fromUserName,
            message,
            timestamp
        } = data;

        console.log(`💬 Message from ${fromUserName} to ${toUserId}`);

        const recipientSocketId = connectedUsers.get(toUserId);

        if (recipientSocketId) {
            // Send message to recipient
            io.to(toUserId).emit('message_received', {
                fromUserId: fromUserId,
                fromUserName: fromUserName,
                message: message,
                timestamp: timestamp
            });

            if (callback) {
                callback({
                    success: true,
                    message: 'Message delivered'
                });
            }

            console.log(`✅ Message delivered to ${toUserId}`);
        } else {
            if (callback) {
                callback({
                    success: false,
                    message: 'Recipient is offline'
                });
            }
            console.log(`❌ ${toUserId} is offline`);
        }
    });

    // Handle disconnect
    socket.on('disconnect', () => {
        const userId = userRooms.get(socket.id);
        if (userId) {
            connectedUsers.delete(userId);
            userRooms.delete(socket.id);
            console.log(`❌ User ${userId} disconnected (${socket.id})`);

            // Broadcast offline status
            socket.broadcast.emit('user_status_changed', {
                userId: userId,
                status: 'offline',
                timestamp: new Date().toISOString()
            });
        } else {
            console.log(`❌ Client disconnected: ${socket.id}`);
        }
    });

    // Handle errors
    socket.on('error', (error) => {
        console.error(`⚠️ Socket error: ${error.message}`);
    });
});

// Start server
http.listen(PORT, '0.0.0.0', () => {
    console.log('\n=================================');
    console.log('🚀 ComPow Socket.IO Server Started');
    console.log('=================================');
    console.log(`📡 Server running on port ${PORT}`);
    console.log(`🌐 Local: http://localhost:${PORT}`);
    console.log(`🌐 Network: http://YOUR_IP:${PORT}`);
    console.log(`⏰ Started at: ${new Date().toISOString()}`);
    console.log('=================================\n');
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
