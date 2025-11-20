<?php
//message_send.php

include_once __DIR__ . "/config.php";
include_once __DIR__ . "/response.php";
include_once __DIR__ . "/utils.php";

require_method('POST');
require_fields(['senderUid', 'receiverUid', 'messageType', 'content'], 'POST');

$senderUid   = post_field('senderUid');
$receiverUid = post_field('receiverUid');
$messageType = post_field('messageType'); // text/post/image/video/file
$content     = post_field('content');
$imageB64    = post_field('imageBase64', '');
$postId      = post_field('postId', '');

// Escape inputs
$senderEsc   = db_escape($conn, $senderUid);
$receiverEsc = db_escape($conn, $receiverUid);
$typeEsc     = db_escape($conn, $messageType);
$contentEsc  = db_escape($conn, $content);
$imageEsc    = db_escape($conn, $imageB64);
$postIdEsc   = db_escape($conn, $postId);

// Basic check that both users exist
$checkUsers = $conn->query("
    SELECT COUNT(*) AS c 
    FROM users 
    WHERE firebase_uid IN ('$senderEsc','$receiverEsc')
");
if (!$checkUsers) {
    json_response(false, "User check failed: " . $conn->error, null, 500);
}
$row = $checkUsers->fetch_assoc();
if ((int)$row['c'] < 2) {
    json_response(false, "One or both users not found", null, 404);
}

// Deterministic chatId
$chatId = ($senderUid < $receiverUid)
    ? ($senderUid . "_" . $receiverUid)
    : ($receiverUid . "_" . $senderUid);
$chatIdEsc = db_escape($conn, $chatId);

// Message id + timestamp
$messageId    = generate_id(32);
$messageIdEsc = db_escape($conn, $messageId);
$createdAt    = now_ms();

// Insert into messages
$sql = "
INSERT INTO messages (
    message_id,
    chat_id,
    sender_uid,
    receiver_uid,
    text,
    media_base64,
    post_id,
    type,
    created_at,
    seen,
    vanish_mode,
    edited
) VALUES (
    '$messageIdEsc',
    '$chatIdEsc',
    '$senderEsc',
    '$receiverEsc',
    '$contentEsc',
    '$imageEsc',
    " . ($postId === "" ? "NULL" : "'$postIdEsc'") . ",
    '$typeEsc',
    $createdAt,
    0,
    0,
    0
)";
if (!$conn->query($sql)) {
    json_response(false, "Failed to send message: " . $conn->error, null, 500);
}

// Decide what to show as last_message in chats list
$displayText = $content;
if ($messageType === 'image') {
    $displayText = "📷 Photo";
} elseif ($messageType === 'post') {
    $displayText = "📝 Shared a post";
    // extend later for video/file if you want
}
$displayEsc = db_escape($conn, $displayText);

// Upsert chat row with last-message fields
$chatSql = "
INSERT INTO chats (
    chat_id,
    participant1_uid,
    participant2_uid,
    created_at,
    last_message,
    last_message_sender_uid,
    last_message_timestamp
) VALUES (
    '$chatIdEsc',
    '$senderEsc',
    '$receiverEsc',
    $createdAt,
    '$displayEsc',
    '$senderEsc',
    $createdAt
)
ON DUPLICATE KEY UPDATE
    last_message             = VALUES(last_message),
    last_message_sender_uid  = VALUES(last_message_sender_uid),
    last_message_timestamp   = VALUES(last_message_timestamp)
";
$conn->query($chatSql); // you can check errors here if you want

// Update last message meta in chat_metadata (optional but you wanted this table)
$metaKey    = $chatId; // same as chatId
$metaKeyEsc = db_escape($conn, $metaKey);

$metaSql = "
INSERT INTO chat_metadata (
    meta_key,
    chat_id,
    participant1_uid,
    participant2_uid,
    last_message,
    last_message_sender_uid,
    last_message_timestamp
) VALUES (
    '$metaKeyEsc',
    '$chatIdEsc',
    '$senderEsc',
    '$receiverEsc',
    '$displayEsc',
    '$senderEsc',
    $createdAt
)
ON DUPLICATE KEY UPDATE
    last_message             = VALUES(last_message),
    last_message_sender_uid  = VALUES(last_message_sender_uid),
    last_message_timestamp   = VALUES(last_message_timestamp)
";
$conn->query($metaSql);

$data = [
    "chatId"    => $chatId,
    "messageId" => $messageId,
    "timestamp" => $createdAt
];

json_response(true, "Message sent", $data);
