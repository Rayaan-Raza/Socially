/**
 * FCM Call Notification Server (ES6 Module Version)
 * 
 * This Node.js server handles sending push notifications for calls
 * using Firebase Cloud Messaging (FCM).
 * 
 * Setup:
 * 1. npm init -y
 * 2. npm install express firebase-admin cors body-parser
 * 3. Download your Firebase service account JSON from Firebase Console
 * 4. Set environment variable: GOOGLE_APPLICATION_CREDENTIALS=path/to/serviceAccount.json
 * 5. node fcm_call_server.js
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

// Initialize Firebase Admin SDK
// Option 1: Using service account file (RECOMMENDED)
// Place your serviceAccountKey.json in the same folder as this file
try {
    const serviceAccount = JSON.parse(
        readFileSync('./serviceAccountKey.json', 'utf8')
    );
    admin.initializeApp({
        credential: admin.credential.cert(serviceAccount)
    });
    console.log('✅ Firebase Admin initialized with service account');
} catch (error) {
    console.log('⚠️  Service account file not found, trying environment variable...');
    // Option 2: Using environment variable (fallback)
    try {
        admin.initializeApp({
            credential: admin.credential.applicationDefault()
        });
        console.log('✅ Firebase Admin initialized with application default credentials');
    } catch (err) {
        console.error('❌ Failed to initialize Firebase Admin:', err.message);
        console.log('\n📋 Setup Instructions:');
        console.log('1. Go to Firebase Console → Project Settings → Service Accounts');
        console.log('2. Click "Generate new private key"');
        console.log('3. Save the JSON file as "serviceAccountKey.json" in this directory');
        console.log('4. Or set GOOGLE_APPLICATION_CREDENTIALS environment variable\n');
        process.exit(1);
    }
}

/**
 * Send a call notification to a user
 * 
 * POST /call/initiate
 * Body: {
 *   callerUid: string,
 *   callerName: string,
 *   receiverFcmToken: string,
 *   callType: "audio" | "video",
 *   channelName: string (optional),
 *   callId: string (optional, will be generated if not provided)
 * }
 */
app.post('/call/initiate', async (req, res) => {
    try {
        const { callerUid, callerName, receiverFcmToken, callType, channelName, callId } = req.body;
        
        if (!callerUid || !callerName || !receiverFcmToken || !callType) {
            return res.status(400).json({
                success: false,
                message: 'Missing required fields: callerUid, callerName, receiverFcmToken, callType'
            });
        }
        
        const generatedCallId = callId || `call_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
        const generatedChannel = channelName || generatedCallId;
        
        console.log(`📞 Initiating ${callType} call from ${callerName} (${callerUid})`);
        console.log(`Call ID: ${generatedCallId}`);
        console.log(`Channel: ${generatedChannel}`);
        
        // FCM message payload
        const message = {
            token: receiverFcmToken,
            
            // Data payload - received by your app even in background
            data: {
                type: 'INCOMING_CALL',
                callId: generatedCallId,
                callerUid: callerUid,
                callerName: callerName,
                callType: callType,
                channelName: generatedChannel,
                timestamp: Date.now().toString()
            },
            
            // Android specific configuration
            android: {
                priority: 'high',
                ttl: 60 * 1000, // 60 seconds - call expires after this
                notification: {
                    title: `Incoming ${callType === 'video' ? 'Video' : 'Voice'} Call`,
                    body: `${callerName} is calling you`,
                    channelId: 'calls',
                    priority: 'max',
                    defaultSound: true,
                    defaultVibrateTimings: true,
                    visibility: 'public'
                }
            },
            
            // Optional: APNS for iOS (if you ever add iOS support)
            apns: {
                payload: {
                    aps: {
                        alert: {
                            title: `Incoming ${callType === 'video' ? 'Video' : 'Voice'} Call`,
                            body: `${callerName} is calling you`
                        },
                        sound: 'default',
                        badge: 1
                    }
                },
                headers: {
                    'apns-priority': '10',
                    'apns-expiration': Math.floor(Date.now() / 1000) + 60
                }
            }
        };
        
        // Send the message
        const response = await admin.messaging().send(message);
        
        console.log('✅ Call notification sent successfully:', response);
        
        res.json({
            success: true,
            message: 'Call notification sent',
            data: {
                callId: generatedCallId,
                channelName: generatedChannel,
                messageId: response,
                timestamp: Date.now()
            }
        });
        
    } catch (error) {
        console.error('❌ Error sending call notification:', error);
        
        // Handle specific FCM errors
        let errorMessage = 'Failed to send call notification';
        if (error.code === 'messaging/invalid-registration-token') {
            errorMessage = 'Invalid FCM token - user may need to re-register';
        } else if (error.code === 'messaging/registration-token-not-registered') {
            errorMessage = 'FCM token not registered - user may have uninstalled app';
        }
        
        res.status(500).json({
            success: false,
            message: errorMessage,
            error: error.message
        });
    }
});

/**
 * End a call notification (cancel incoming call)
 * 
 * POST /call/end
 * Body: {
 *   receiverFcmToken: string,
 *   callId: string,
 *   reason: string (optional: "cancelled", "declined", "timeout", "answered_elsewhere")
 * }
 */
app.post('/call/end', async (req, res) => {
    try {
        const { receiverFcmToken, callId, reason } = req.body;
        
        if (!receiverFcmToken || !callId) {
            return res.status(400).json({
                success: false,
                message: 'Missing required fields: receiverFcmToken, callId'
            });
        }
        
        console.log(`📴 Ending call ${callId}, reason: ${reason || 'unknown'}`);
        
        const message = {
            token: receiverFcmToken,
            data: {
                type: 'CALL_ENDED',
                callId: callId,
                reason: reason || 'cancelled',
                timestamp: Date.now().toString()
            },
            android: {
                priority: 'high'
            }
        };
        
        const response = await admin.messaging().send(message);
        
        console.log('✅ Call end notification sent:', response);
        
        res.json({
            success: true,
            message: 'Call end notification sent',
            data: {
                callId: callId,
                messageId: response
            }
        });
        
    } catch (error) {
        console.error('❌ Error sending call end notification:', error);
        res.status(500).json({
            success: false,
            message: 'Failed to send call end notification',
            error: error.message
        });
    }
});

/**
 * Call answered notification
 * 
 * POST /call/answered
 * Body: {
 *   callerFcmToken: string,
 *   callId: string
 * }
 */
app.post('/call/answered', async (req, res) => {
    try {
        const { callerFcmToken, callId } = req.body;
        
        if (!callerFcmToken || !callId) {
            return res.status(400).json({
                success: false,
                message: 'Missing required fields: callerFcmToken, callId'
            });
        }
        
        console.log(`✅ Call ${callId} answered`);
        
        const message = {
            token: callerFcmToken,
            data: {
                type: 'CALL_ANSWERED',
                callId: callId,
                timestamp: Date.now().toString()
            },
            android: {
                priority: 'high'
            }
        };
        
        const response = await admin.messaging().send(message);
        
        console.log('✅ Call answered notification sent:', response);
        
        res.json({
            success: true,
            message: 'Call answered notification sent',
            data: {
                callId: callId,
                messageId: response
            }
        });
        
    } catch (error) {
        console.error('❌ Error sending call answered notification:', error);
        res.status(500).json({
            success: false,
            message: 'Failed to send call answered notification',
            error: error.message
        });
    }
});

/**
 * Call declined notification
 * 
 * POST /call/declined
 * Body: {
 *   callerFcmToken: string,
 *   callId: string
 * }
 */
app.post('/call/declined', async (req, res) => {
    try {
        const { callerFcmToken, callId } = req.body;
        
        if (!callerFcmToken || !callId) {
            return res.status(400).json({
                success: false,
                message: 'Missing required fields: callerFcmToken, callId'
            });
        }
        
        console.log(`❌ Call ${callId} declined`);
        
        const message = {
            token: callerFcmToken,
            data: {
                type: 'CALL_DECLINED',
                callId: callId,
                timestamp: Date.now().toString()
            },
            android: {
                priority: 'high'
            }
        };
        
        const response = await admin.messaging().send(message);
        
        res.json({
            success: true,
            message: 'Call declined notification sent',
            data: {
                callId: callId,
                messageId: response
            }
        });
        
    } catch (error) {
        console.error('❌ Error sending call declined notification:', error);
        res.status(500).json({
            success: false,
            message: 'Failed to send call declined notification',
            error: error.message
        });
    }
});

/**
 * Health check endpoint
 */
app.get('/health', (req, res) => {
    res.json({
        success: true,
        message: 'FCM Call Server is running',
        timestamp: Date.now()
    });
});

/**
 * Root endpoint with API documentation
 */
app.get('/', (req, res) => {
    res.json({
        message: 'FCM Call Notification Server',
        version: '1.0.0',
        endpoints: {
            'POST /call/initiate': 'Initiate a call and send FCM notification',
            'POST /call/end': 'End/cancel a call',
            'POST /call/answered': 'Notify caller that call was answered',
            'POST /call/declined': 'Notify caller that call was declined',
            'GET /health': 'Health check'
        }
    });
});

// Start server
// ✅ NEW: Listen on 0.0.0.0 so the Emulator can see it
app.listen(PORT, '0.0.0.0', () => {
    console.log(`\n🚀 Server running on port ${PORT} (Accessible to Emulator)`);
});