<?php
// messages_get.php
include_once __DIR__ . "/config.php";
include_once __DIR__ . "/response.php";
include_once __DIR__ . "/utils.php";

require_method('GET');
require_fields(['chatId', 'uid'], 'GET');

$chatId = get_field('chatId');
$uid    = get_field('uid');
$limit  = intval(get_field('limit', 50));
$before = get_field('before', ''); // optional timestamp (ms) for pagination

if ($limit <= 0 || $limit > 200) {
    $limit = 50;
}

$chatIdEsc = db_escape($conn, $chatId);
$uidEsc    = db_escape($conn, $uid);

// 1) Optional: verify user exists
$userSql = "SELECT firebase_uid FROM users WHERE firebase_uid = '$uidEsc' LIMIT 1";
$userRes = $conn->query($userSql);
if (!$userRes) {
    json_response(false, "User check failed: " . $conn->error, null, 500);
}
if ($userRes->num_rows === 0) {
    json_response(false, "User not found", null, 404);
}

// 2) Optional: verify the user is participant in this chat
$chatSql = "
    SELECT participant1_uid, participant2_uid
    FROM chats
    WHERE chat_id = '$chatIdEsc'
    LIMIT 1
";
$chatRes = $conn->query($chatSql);
if (!$chatRes) {
    json_response(false, "Chat check failed: " . $conn->error, null, 500);
}
if ($chatRes->num_rows === 0) {
    json_response(false, "Chat not found", null, 404);
}
$chatRow  = $chatRes->fetch_assoc();
$p1       = $chatRow['participant1_uid'];
$p2       = $chatRow['participant2_uid'];

if ($p1 !== $uid && $p2 !== $uid) {
    // user is not participant – block access
    json_response(false, "Forbidden: not a participant of this chat", null, 403);
}

// 3) Build WHERE for messages with optional "before" (for pagination)
$where = "chat_id = '$chatIdEsc'";
if ($before !== '') {
    $beforeTs = (int)$before;
    if ($beforeTs > 0) {
        $where .= " AND created_at < $beforeTs";
    }
}

// 4) Fetch messages (newest first)
$sql = "
    SELECT
        message_id,
        chat_id,
        sender_uid,
        receiver_uid,
        text,
        media_base64,
        post_id,
        type,
        created_at,
        edited
    FROM messages
    WHERE $where
    ORDER BY created_at DESC, message_id DESC
    LIMIT $limit
";

$res = $conn->query($sql);
if (!$res) {
    json_response(false, "Failed to load messages: " . $conn->error, null, 500);
}

$messages = [];
while ($row = $res->fetch_assoc()) {
    $createdAt = isset($row['created_at']) ? (int)$row['created_at'] : 0;
    $editableUntil = $createdAt > 0 ? ($createdAt + 300000) : 0; // +5 min

    $messages[] = [
        "messageId"      => $row['message_id'],
        "senderId"       => $row['sender_uid'],
        "receiverId"     => $row['receiver_uid'],
        "messageType"    => $row['type'] ?? "text",     // "text", "image", "post"
        "content"        => $row['text'] ?? "",
        "imageUrl"       => $row['media_base64'] ?? "", // Base64 or URL, your choice
        "postId"         => $row['post_id'] ?? "",
        "sharedPostId"   => "",                         // unused in your current data class, keep empty
        "text"           => $row['text'] ?? "",         // for backward compat if needed
        "timestamp"      => $createdAt,
        "isEdited"       => !empty($row['edited']) && ((int)$row['edited'] === 1),
        "isDeleted"      => false,                      // no column yet; always false
        "editableUntil"  => $editableUntil
    ];
}

// 5) Return in oldest → newest order if you prefer
// Currently we fetched newest first. If your adapter expects chronological list,
// reverse it here:
$messages = array_reverse($messages);

$data = [
    "chatId"   => $chatId,
    "uid"      => $uid,
    "count"    => count($messages),
    "messages" => $messages
];

json_response(true, "Messages loaded", $data);
