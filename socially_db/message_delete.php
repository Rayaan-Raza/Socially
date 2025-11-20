<?php
//message_delete.php
include_once __DIR__ . "/config.php";
include_once __DIR__ . "/response.php";
include_once __DIR__ . "/utils.php";

require_method('POST');
require_fields(['uid', 'messageId'], 'POST');

$uid       = post_field('uid');
$messageId = post_field('messageId');

$uidEsc       = db_escape($conn, $uid);
$messageIdEsc = db_escape($conn, $messageId);

// Find message
$sql = "
SELECT 
    message_id,
    chat_id,
    sender_uid,
    receiver_uid,
    created_at
FROM messages
WHERE message_id = '$messageIdEsc'
LIMIT 1
";
$res = $conn->query($sql);

if (!$res || $res->num_rows === 0) {
    json_response(false, "Message not found", null, 404);
}
$row = $res->fetch_assoc();

// Only sender can delete
if ($row['sender_uid'] !== $uidEsc) {
    json_response(false, "You can only delete your own messages", null, 403);
}

// Check 5 minute window
$createdAt = (int)$row['created_at'];
$nowMs     = now_ms();
$limitMs   = 5 * 60 * 1000;

if ($nowMs > $createdAt + $limitMs) {
    json_response(false, "Delete window has expired", null, 400);
}

// Delete message
$chatIdEsc = db_escape($conn, $row['chat_id']);

$delSql = "
DELETE FROM messages
WHERE message_id = '$messageIdEsc'
";
if (!$conn->query($delSql)) {
    json_response(false, "Failed to delete message: " . $conn->error, null, 500);
}

// Recompute last message, if any
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
    // No messages left in chat → clear metadata
    $conn->query("
        DELETE FROM chat_metadata
        WHERE chat_id = '$chatIdEsc'
    ");
}

$data = [
    "messageId" => $messageId,
    "deleted"   => true
];

json_response(true, "Message deleted", $data);
