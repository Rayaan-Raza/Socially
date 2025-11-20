<?php
//message_seen.php

include_once __DIR__ . "/config.php";
include_once __DIR__ . "/response.php";
include_once __DIR__ . "/utils.php";

require_method('POST');
require_fields(['chatId', 'viewerUid'], 'POST');

$chatId    = post_field('chatId');
$viewerUid = post_field('viewerUid');

$chatIdEsc  = db_escape($conn, $chatId);
$viewerEsc  = db_escape($conn, $viewerUid);

// Mark all messages to this user as seen
$updSql = "
UPDATE messages
SET seen = 1
WHERE chat_id = '$chatIdEsc'
  AND receiver_uid = '$viewerEsc'
";
if (!$conn->query($updSql)) {
    json_response(false, "Failed to mark seen: " . $conn->error, null, 500);
}

// Delete vanish-mode messages that have been seen
$delSql = "
DELETE FROM messages
WHERE chat_id = '$chatIdEsc'
  AND receiver_uid = '$viewerEsc'
  AND vanish_mode = 1
  AND seen = 1
";
if (!$conn->query($delSql)) {
    json_response(false, "Failed to delete vanish messages: " . $conn->error, null, 500);
}

// Recompute last message
$lastSql = "
SELECT 
    message_id,
    text,
    type,
    created_at,
    sender_uid,
    receiver_uid,
    media_base64,
    post_id
FROM messages
WHERE chat_id = '$chatIdEsc'
ORDER BY created_at DESC
LIMIT 1
";
$lastRes = $conn->query($lastSql);

if ($lastRes && $lastRes->num_rows > 0) {
    $last = $lastRes->fetch_assoc();

    if (!empty(trim($last['text']))) {
        $preview = $last['text'];
    } elseif (!empty($last['post_id'])) {
        $preview = "Shared a post";
    } elseif (!empty($last['media_base64'])) {
        $preview = "Sent a media";
    } else {
        $preview = "(no content)";
    }
    $previewEsc = db_escape($conn, $preview);
    $lastTs     = (int)$last['created_at'];
    $senderLast = db_escape($conn, $last['sender_uid']);

    $conn->query("
        INSERT INTO chat_metadata (
            meta_key,
            chat_id,
            participant1_uid,
            participant2_uid,
            last_message,
            last_message_sender_uid,
            last_message_timestamp
        ) VALUES (
            '$chatIdEsc',
            '$chatIdEsc',
            '".db_escape($conn, $last['sender_uid'])."',
            '".db_escape($conn, $last['receiver_uid'])."',
            '$previewEsc',
            '$senderLast',
            $lastTs
        )
        ON DUPLICATE KEY UPDATE
            last_message = VALUES(last_message),
            last_message_sender_uid = VALUES(last_message_sender_uid),
            last_message_timestamp = VALUES(last_message_timestamp)
    ");
} else {
    // No messages left
    $conn->query("
        DELETE FROM chat_metadata
        WHERE chat_id = '$chatIdEsc'
    ");
}

$data = [
    "chatId"    => $chatId,
    "viewerUid" => $viewerUid
];

json_response(true, "Messages marked seen and vanish cleaned", $data);
