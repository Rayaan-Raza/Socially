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
  databaseURL: "https://socially-5a61a-default-rtdb.firebaseio.com",

});

const db = admin.database();
const usersRef = db.ref("users");
const messagesRef = db.ref("messages");

// ✅ Keep track of already processed message IDs to avoid duplicate notifications
const sentMessages = new Set();

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

app.listen(PORT, () => console.log(`🚀 Local FCM Server running on port ${PORT}`));
