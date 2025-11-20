<?php
// chat_get.php
include_once __DIR__ . "/config.php";
include_once __DIR__ . "/response.php";
include_once __DIR__ . "/utils.php";

require_method('GET');
require_fields(['chatId', 'uid'], 'GET');

$chatId = trim(get_field('chatId'));
$uid    = trim(get_field('uid'));

$chatIdEsc = db_escape($conn, $chatId);
$uidEsc    = db_escape($conn, $uid);

// 1) Get chat metadata
$metaSql = "
    SELECT chat_id, participant1_uid, participant2_uid,
           last_message, last_message_sender_uid, last_message_timestamp
    FROM chat_metadata
    WHERE chat_id = '$chatIdEsc'
    LIMIT 1
";
$metaRes = $conn->query($metaSql);
if (!$metaRes || $metaRes->num_rows === 0) {
    json_response(false, "Chat not found", null, 404);
}
$meta = $metaRes->fetch_assoc();

$participant1 = $meta['participant1_uid'];
$participant2 = $meta['participant2_uid'];

// Optional: check viewer is part of this chat
if ($uid !== $participant1 && $uid !== $participant2) {
    json_response(false, "You are not a participant of this chat", null, 403);
}

$participants = [$participant1, $participant2];

// 2) Load all messages for this chat
$msgSql = "
    SELECT 
        message_id,
        firebase_message_id,
        chat_id,
        sender_uid,
        receiver_uid,
        text,
        media_base64,
        display_content,
        type,
        image_url,
        post_id,
        shared_post_id,
        deleted,
        edited,
        editable_until,
        created_at,
        seen,
        vanish_mode
    FROM messages
    WHERE chat_id = '$chatIdEsc'
    ORDER BY created_at ASC
";
$msgRes = $conn->query($msgSql);
if (!$msgRes) {
    json_response(false, "Failed to load messages: " . $conn->error, null, 500);
}

$messages = [];

while ($row = $msgRes->fetch_assoc()) {
    $createdAt = $row['created_at'] ? (int)$row['created_at'] : 0;

    $messageId = $row['firebase_message_id'] ?: $row['message_id'];
    $displayContent = $row['display_content'] !== null && $row['display_content'] !== ''
        ? $row['display_content']
        : $row['text'];

    $editableUntil = $row['editable_until']
        ? (int)$row['editable_until']
        : ($createdAt > 0 ? $createdAt + 5 * 60 * 1000 : 0);

    $messages[] = [
        "messageId"        => $messageId,
        "firebaseMessageId"=> $row['firebase_message_id'],
        "dbMessageId"      => $row['message_id'],
        "chatId"           => $row['chat_id'],
        "content"          => $row['text'],
        "displayContent"   => $displayContent,
        "deleted"          => (bool)$row['deleted'],
        "editableUntil"    => $editableUntil,
        "edited"           => (bool)$row['edited'],
        "imageUrl"         => $row['image_url'],
        "mediaBase64"      => $row['media_base64'],
        "messageType"      => $row['type'],
        "postId"           => $row['post_id'],
        "sharedPostId"     => $row['shared_post_id'],
        "receiverId"       => $row['receiver_uid'],
        "senderId"         => $row['sender_uid'],
        "createdAt"        => $createdAt,
        "seen"             => (int)$row['seen'],
        "vanishMode"       => (int)$row['vanish_mode']
    ];
}

// 3) Build chat object
$chatData = [
    "chatId"               => $meta['chat_id'],
    "participants"         => $participants,
    "lastMessage"          => $meta['last_message'] ?? "",
    "lastMessageSenderId"  => $meta['last_message_sender_uid'] ?? "",
    "lastMessageTimestamp" => $meta['last_message_timestamp'] ? (int)$meta['last_message_timestamp'] : 0
];

// 4) Pack everything
$data = [
    "chat"     => $chatData,
    "messages" => $messages
];

json_response(true, "Chat loaded", $data);
