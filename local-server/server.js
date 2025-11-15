import express from "express";
import admin from "firebase-admin";
import fs from "fs";

const app = express();
const PORT = 3000;

// Read your service account key manually (avoids import issues)
const serviceAccount = JSON.parse(fs.readFileSync("./serviceAccountKey.json", "utf8"));

// Initialize Firebase Admin SDK
admin.initializeApp({
  credential: admin.credential.cert(serviceAccount),
  databaseURL: "https://socially-5a61a-default-rtdb.firebaseio.com/",
});

const db = admin.database();
const usersRef = db.ref("users");
const messagesRef = db.ref("messages");
const followRequestsRef = db.ref("follow_requests");
const followersRef = db.ref("followers");
const screenshotsRef = db.ref("screenshots");

// ✅ Keep track of already processed IDs to avoid duplicate notifications
const sentMessages = new Set();
const sentFollowRequests = new Set();
const sentFollowAccepts = new Set();

// 🔔 Listen for NEW messages
messagesRef.on("child_added", (chatSnap) => {
  const chatId = chatSnap.key;
  console.log(`🗨️  Now monitoring chat: ${chatId}`);

  // For each new message in this chat
  chatSnap.ref.on("child_added", async (msgSnap) => {
    const msgId = msgSnap.key;
    if (sentMessages.has(msgId)) return; // Skip if already processed
    sentMessages.add(msgId);

    const msg = msgSnap.val();
    if (!msg) return;

    const { senderId, receiverId, content, displayContent } = msg;
    if (!receiverId || !senderId) return;

    try {
      // Get receiver's FCM token
      const tokenSnap = await usersRef.child(receiverId).child("fcmToken").once("value");
      const token = tokenSnap.val();
      if (!token) return console.log(`⚠️ No FCM token for receiver: ${receiverId}`);

      // Get sender's username
      const senderSnap = await usersRef.child(senderId).child("username").once("value");
      const senderName = senderSnap.val() || "Someone";

      // Create FCM payload
      const payload = {
        notification: {
          title: senderName,
          body: displayContent || content || "Sent you a message 💬",
        },
        data: {
          type: "message",
          senderId,
          chatId,
          senderName,
        },
        token,
      };

      // Send the push notification
      await admin.messaging().send(payload);
      console.log(`✅ Notification sent to ${receiverId} from ${senderName}`);
    } catch (err) {
      console.error("❌ Error sending notification:", err);
    }
  });
});

// 🔔 Listen for NEW follow requests
followRequestsRef.on("child_added", (userSnap) => {
  const receiverId = userSnap.key; // The person receiving follow requests
  console.log(`👥 Now monitoring follow requests for: ${receiverId}`);

  // Listen for each requester
  userSnap.ref.on("child_added", async (reqSnap) => {
    const requesterId = reqSnap.key;
    const isActive = reqSnap.val();
    
    if (!isActive) return; // Skip if request is not active
    
    const notificationId = `${receiverId}_${requesterId}`;
    if (sentFollowRequests.has(notificationId)) return;
    sentFollowRequests.add(notificationId);

    try {
      // Get receiver's FCM token
      const tokenSnap = await usersRef.child(receiverId).child("fcmToken").once("value");
      const token = tokenSnap.val();
      if (!token) return console.log(`⚠️ No FCM token for receiver: ${receiverId}`);

      // Get requester's username
      const requesterSnap = await usersRef.child(requesterId).child("username").once("value");
      const requesterName = requesterSnap.val() || "Someone";

      // Create FCM payload
      const payload = {
        notification: {
          title: "New Follow Request",
          body: `${requesterName} wants to follow you`,
        },
        data: {
          type: "follow_request",
          requesterId,
          requesterName,
        },
        token,
      };

      // Send the push notification
      await admin.messaging().send(payload);
      console.log(`✅ Follow request notification sent to ${receiverId} from ${requesterName}`);
    } catch (err) {
      console.error("❌ Error sending follow request notification:", err);
    }
  });

  // Listen for when requests are removed (accepted or declined)
  userSnap.ref.on("child_removed", async (reqSnap) => {
    const requesterId = reqSnap.key;
    const notificationId = `${receiverId}_${requesterId}`;
    
    // Remove from tracking when request is removed
    sentFollowRequests.delete(notificationId);
    console.log(`🗑️  Follow request removed: ${requesterId} -> ${receiverId}`);
  });
});

// 🔔 Listen for NEW followers (when follow request is accepted)
followersRef.on("child_added", (userSnap) => {
  const userId = userSnap.key; // The person who gained a follower
  console.log(`👤 Now monitoring followers for: ${userId}`);

  userSnap.ref.on("child_added", async (followerSnap) => {
    const followerId = followerSnap.key;
    const isFollowing = followerSnap.val();
    
    if (!isFollowing) return;
    
    const notificationId = `accept_${followerId}_${userId}`;
    if (sentFollowAccepts.has(notificationId)) return;
    sentFollowAccepts.add(notificationId);

    try {
      // Get follower's FCM token (person who sent the original request)
      const tokenSnap = await usersRef.child(followerId).child("fcmToken").once("value");
      const token = tokenSnap.val();
      if (!token) return console.log(`⚠️ No FCM token for follower: ${followerId}`);

      // Get the person's username who accepted
      const userSnap = await usersRef.child(userId).child("username").once("value");
      const userName = userSnap.val() || "Someone";

      // Create FCM payload
      const payload = {
        notification: {
          title: "Follow Request Accepted",
          body: `${userName} accepted your follow request`,
        },
        data: {
          type: "follow_accepted",
          userId,
          userName,
        },
        token,
      };

      // Send the push notification
      await admin.messaging().send(payload);
      console.log(`✅ Follow accepted notification sent to ${followerId} about ${userName}`);
    } catch (err) {
      console.error("❌ Error sending follow accepted notification:", err);
    }
  });
});

// ✅ Listen for new screenshot events
screenshotsRef.on("child_added", async (snap) => {
  const event = snap.val();
  if (!event) return;

  const { chatId, takerId, receiverId } = event;

  try {
    const tokenSnap = await usersRef.child(receiverId).child("fcmToken").once("value");
    const token = tokenSnap.val();
    if (!token) return console.log(`⚠️ No FCM token for receiver: ${receiverId}`);

    const takerSnap = await usersRef.child(takerId).child("username").once("value");
    const takerName = takerSnap.val() || "Someone";

    const payload = {
      notification: {
        title: "📸 Screenshot Alert!",
        body: `${takerName} took a screenshot in your chat.`,
      },
      data: {
        type: "screenshot",
        takerId,
        chatId,
      },
      token,
    };

    await admin.messaging().send(payload);
    console.log(`✅ Screenshot notification sent to ${receiverId}`);
  } catch (err) {
    console.error("❌ Error sending screenshot notification:", err);
  }
});

// 🚀 Start the server
app.listen(PORT, () => {
  console.log(`🚀 Local FCM Server running on port ${PORT}`);
  console.log(`📡 Monitoring:`);
  console.log(`   - Messages (messages/)`);
  console.log(`   - Follow Requests (follow_requests/)`);
  console.log(`   - New Followers (followers/)`);
  console.log(`   - Screenshots (screenshots/)`);
});