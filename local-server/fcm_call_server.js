/**
 * FCM Call & Notification Server
 * * Handles: Audio/Video Calls, Chat Messages, and Screenshot Alerts
 */

import express from 'express';
import admin from 'firebase-admin';
import cors from 'cors';
import bodyParser from 'body-parser';
import { readFileSync } from 'fs';

const app = express();
const PORT = process.env.PORT || 3000;

// Middleware
app.use(cors());
app.use(bodyParser.json());
app.use(bodyParser.urlencoded({ extended: true }));

// Initialize Firebase Admin
try {
    console.log('🔍 Looking for serviceAccountKey.json...');
    const serviceAccountPath = './serviceAccountKey.json';
    
    const serviceAccount = JSON.parse(
        readFileSync(serviceAccountPath, 'utf8')
    );

    admin.initializeApp({
        credential: admin.credential.cert(serviceAccount),
        projectId: serviceAccount.project_id
    });
    
    console.log('✅ Firebase Admin initialized successfully!');
    console.log(`   Project ID: ${serviceAccount.project_id}`);

} catch (error) {
    console.error('\n❌ FIREBASE INIT ERROR:');
    console.error(`   Could not load "serviceAccountKey.json"`);
    console.error(`   Error details: ${error.message}`);
    process.exit(1);
}

// ======================= CALL ENDPOINTS =======================

app.post('/call/initiate', async (req, res) => {
    try {
        const { callerUid, callerName, receiverFcmToken, callType, channelName, callId } = req.body;
        
        if (!callerUid || !callerName || !receiverFcmToken || !callType) {
            return res.status(400).json({ success: false, message: 'Missing fields' });
        }
        
        const generatedCallId = callId || `call_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
        const generatedChannel = channelName || generatedCallId;
        
        console.log(`📞 Initiating ${callType} call from ${callerName}`);

        const message = {
            token: receiverFcmToken,
            data: {
                type: 'INCOMING_CALL',
                callId: generatedCallId,
                callerUid: callerUid,
                callerName: callerName,
                callType: callType,
                channelName: generatedChannel,
                timestamp: Date.now().toString()
            },
            android: {
                priority: 'high',
                ttl: 60 * 1000,
                notification: {
                    title: `Incoming ${callType === 'video' ? 'Video' : 'Voice'} Call`,
                    body: `${callerName} is calling you`,
                    channelId: 'calls',
                    priority: 'max',
                    defaultSound: true,
                    visibility: 'public'
                }
            }
        };
        
        const response = await admin.messaging().send(message);
        res.json({
            success: true,
            data: { callId: generatedCallId, channelName: generatedChannel, messageId: response }
        });
        
    } catch (error) {
        console.error('❌ Error sending call notification:', error);
        res.status(500).json({ success: false, error: error.message });
    }
});

app.post('/call/end', async (req, res) => {
    try {
        const { receiverFcmToken, callId, reason } = req.body;
        
        if (!receiverFcmToken || !callId) return res.status(400).json({ success: false });
        
        console.log(`📴 Ending call ${callId}, reason: ${reason}`);
        
        const message = {
            token: receiverFcmToken,
            data: {
                type: 'CALL_ENDED',
                callId: callId,
                reason: reason || 'cancelled',
                timestamp: Date.now().toString()
            },
            android: { priority: 'high' }
        };
        
        const response = await admin.messaging().send(message);
        res.json({ success: true, data: { messageId: response } });
        
    } catch (error) {
        console.error('❌ Error ending call:', error);
        res.status(500).json({ success: false });
    }
});

app.post('/call/answered', async (req, res) => {
    try {
        const { callerFcmToken, callId } = req.body;
        if (!callerFcmToken || !callId) return res.status(400).json({ success: false });

        console.log(`✅ Call ${callId} answered`);

        const message = {
            token: callerFcmToken,
            data: {
                type: 'CALL_ANSWERED',
                callId: callId,
                timestamp: Date.now().toString()
            },
            android: { priority: 'high' }
        };

        const response = await admin.messaging().send(message);
        res.json({ success: true, data: { messageId: response } });
    } catch (error) {
        console.error('❌ Error sending answer:', error);
        res.status(500).json({ success: false });
    }
});

app.post('/call/declined', async (req, res) => {
    try {
        const { callerFcmToken, callId } = req.body;
        if (!callerFcmToken || !callId) return res.status(400).json({ success: false });

        console.log(`❌ Call ${callId} declined`);

        const message = {
            token: callerFcmToken,
            data: {
                type: 'CALL_DECLINED',
                callId: callId,
                timestamp: Date.now().toString()
            },
            android: { priority: 'high' }
        };

        const response = await admin.messaging().send(message);
        res.json({ success: true, data: { messageId: response } });
    } catch (error) {
        console.error('❌ Error sending decline:', error);
        res.status(500).json({ success: false });
    }
});

// ======================= NOTIFICATION ENDPOINTS =======================

/**
 * Send Chat Message Notification
 * POST /message/send
 */
app.post('/message/send', async (req, res) => {
    try {
        const { receiverFcmToken, senderName, content, chatId, messageType } = req.body;

        if (!receiverFcmToken || !chatId) {
            return res.status(400).json({ success: false, message: 'Missing fields' });
        }

        // Determine body text based on type
        let bodyText = content;
        if (messageType === 'image') bodyText = '📷 Sent a photo';
        if (messageType === 'video') bodyText = '🎥 Sent a video';
        if (messageType === 'audio') bodyText = '🎤 Sent a voice message';

        const message = {
            token: receiverFcmToken,
            notification: {
                title: senderName || 'New Message',
                body: bodyText
            },
            data: {
                type: 'NEW_MESSAGE',
                chatId: chatId,
                senderName: senderName || 'User',
                messageType: messageType || 'text',
                timestamp: Date.now().toString()
            },
            android: { 
                priority: 'high',
                notification: {
                    channelId: 'messages',
                    defaultSound: true
                }
            }
        };

        const response = await admin.messaging().send(message);
        console.log(`📩 Message notification sent to ${senderName}`);
        res.json({ success: true, messageId: response });

    } catch (error) {
        console.error('❌ Error sending message notification:', error);
        res.status(500).json({ success: false, error: error.message });
    }
});

/**
 * Send Screenshot Notification
 * POST /screenshot/notify
 */
app.post('/screenshot/notify', async (req, res) => {
    try {
        const { receiverFcmToken, takerName, chatId } = req.body;

        if (!receiverFcmToken || !takerName) {
            return res.status(400).json({ success: false, message: 'Missing fields' });
        }

        console.log(`📸 Screenshot detected by ${takerName}`);

        const message = {
            token: receiverFcmToken,
            notification: {
                title: 'Screenshot Detected',
                body: `👁️ ${takerName} took a screenshot of the chat!`
            },
            data: {
                type: 'SCREENSHOT_TAKEN',
                chatId: chatId,
                takerName: takerName,
                timestamp: Date.now().toString()
            },
            android: { priority: 'high' }
        };

        const response = await admin.messaging().send(message);
        res.json({ success: true, messageId: response });

    } catch (error) {
        console.error('❌ Error sending screenshot notification:', error);
        res.status(500).json({ success: false, error: error.message });
    }
});

app.get('/health', (req, res) => res.json({ success: true, message: 'Server running' }));

app.listen(PORT, () => {
    console.log('\n🚀 ════════════════════════════════════════════════════');
    console.log(`   FCM Call Server running on port ${PORT}`);
    console.log('   ════════════════════════════════════════════════════');
    console.log(`   Health check: http://localhost:${PORT}/health`);
    console.log(`   API docs:     http://localhost:${PORT}/`);
    console.log('   ════════════════════════════════════════════════════\n');
});