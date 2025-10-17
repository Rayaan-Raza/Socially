const functions = require("firebase-functions");
const admin = require("firebase-admin");
admin.initializeApp();

// Send message notification
exports.sendMessageNotification = functions.database
    .ref("/chats/{chatId}/{messageId}")
    .onCreate(async (snapshot, context) => {
      const message = snapshot.val();
      const chatId = context.params.chatId;

      // Don't send notification if user is sending to themselves
      if (message.senderId === message.receiverId) {
        return null;
      }

      try {
        // Get receiver's FCM token
        const receiverSnapshot = await admin.database()
            .ref(`/users/${message.receiverId}/fcmToken`)
            .once("value");

        const fcmToken = receiverSnapshot.val();
        if (!fcmToken) {
          console.log("No FCM token for user");
          return null;
        }

        // Get sender's username
        const senderSnapshot = await admin.database()
            .ref(`/users/${message.senderId}/username`)
            .once("value");

        const senderName = senderSnapshot.val() || "Someone";

        // Prepare notification
        let notificationBody = "";
        if (message.messageType === "text") {
          notificationBody = message.text;
        } else if (message.messageType === "image") {
          notificationBody = "📷 Photo";
        } else if (message.messageType === "post") {
          notificationBody = "📝 Shared a post";
        }

        const payload = {
          notification: {
            title: senderName,
            body: notificationBody,
            sound: "default",
          },
          data: {
            type: "message",
            chatId: chatId,
            senderId: message.senderId,
            senderName: senderName,
          },
          token: fcmToken,
        };

        // Send notification
        await admin.messaging().send(payload);
        console.log("Notification sent successfully");
      } catch (error) {
        console.error("Error sending notification:", error);
      }

      return null;
    });

// Send like notification
exports.sendLikeNotification = functions.database
    .ref("/postLikes/{postId}/{userId}")
    .onCreate(async (snapshot, context) => {
      const postId = context.params.postId;
      const likerId = context.params.userId;

      try {
        // Get post owner
        const postIndexSnapshot = await admin.database()
            .ref(`/postIndex/${postId}`)
            .once("value");

        const postData = postIndexSnapshot.val();
        if (!postData) return null;

        const postOwnerId = postData.uid;

        // Don't send notification if user likes their own post
        if (likerId === postOwnerId) return null;

        // Get owner's FCM token
        const ownerSnapshot = await admin.database()
            .ref(`/users/${postOwnerId}/fcmToken`)
            .once("value");

        const fcmToken = ownerSnapshot.val();
        if (!fcmToken) return null;

        // Get liker's username
        const likerSnapshot = await admin.database()
            .ref(`/users/${likerId}/username`)
            .once("value");

        const likerName = likerSnapshot.val() || "Someone";

        const payload = {
          notification: {
            title: "New Like",
            body: `${likerName} liked your post`,
            sound: "default",
          },
          data: {
            type: "like",
            postId: postId,
            userId: likerId,
          },
          token: fcmToken,
        };

        await admin.messaging().send(payload);
      } catch (error) {
        console.error("Error sending like notification:", error);
      }

      return null;
    });

// Send comment notification
exports.sendCommentNotification = functions.database
    .ref("/postComments/{postId}/{commentId}")
    .onCreate(async (snapshot, context) => {
      const comment = snapshot.val();
      const postId = context.params.postId;

      try {
        // Get post owner
        const postIndexSnapshot = await admin.database()
            .ref(`/postIndex/${postId}`)
            .once("value");

        const postData = postIndexSnapshot.val();
        if (!postData) return null;

        const postOwnerId = postData.uid;

        // Don't send notification if user comments on their own post
        if (comment.uid === postOwnerId) return null;

        // Get owner's FCM token
        const ownerSnapshot = await admin.database()
            .ref(`/users/${postOwnerId}/fcmToken`)
            .once("value");

        const fcmToken = ownerSnapshot.val();
        if (!fcmToken) return null;

        const payload = {
          notification: {
            title: comment.username || "Someone",
            body: comment.text,
            sound: "default",
          },
          data: {
            type: "comment",
            postId: postId,
            commentId: comment.commentId,
          },
          token: fcmToken,
        };

        await admin.messaging().send(payload);
      } catch (error) {
        console.error("Error sending comment notification:", error);
      }

      return null;
    });

// Send follow notification
exports.sendFollowNotification = functions.database
    .ref("/users/{userId}/followers/{followerId}")
    .onCreate(async (snapshot, context) => {
      const userId = context.params.userId;
      const followerId = context.params.followerId;

      try {
        // Get user's FCM token
        const userSnapshot = await admin.database()
            .ref(`/users/${userId}/fcmToken`)
            .once("value");

        const fcmToken = userSnapshot.val();
        if (!fcmToken) return null;

        // Get follower's username
        const followerSnapshot = await admin.database()
            .ref(`/users/${followerId}/username`)
            .once("value");

        const followerName = followerSnapshot.val() || "Someone";

        const payload = {
          notification: {
            title: "New Follower",
            body: `${followerName} started following you`,
            sound: "default",
          },
          data: {
            type: "follow",
            userId: followerId,
          },
          token: fcmToken,
        };

        await admin.messaging().send(payload);
      } catch (error) {
        console.error("Error sending follow notification:", error);
      }

      return null;
    });
