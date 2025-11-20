<?php
// chats_list.php
include_once __DIR__ . "/config.php";
include_once __DIR__ . "/response.php";
include_once __DIR__ . "/utils.php";

require_method('GET');
require_fields(['uid'], 'GET');

$uid   = get_field('uid');
$limit = intval(get_field('limit', 50));

if ($limit <= 0 || $limit > 200) {
    $limit = 50;
}

$uidEsc = db_escape($conn, $uid);

// Optional: verify user exists
$userSql = "SELECT 1 FROM users WHERE firebase_uid = '$uidEsc' LIMIT 1";
$userRes = $conn->query($userSql);
if (!$userRes) {
    json_response(false, "User check failed: " . $conn->error, null, 500);
}
if ($userRes->num_rows === 0) {
    json_response(false, "User not found", null, 404);
}

/*
 * Pull directly from 'chats':
 *  - chat_id
 *  - participant1_uid, participant2_uid
 *  - last_message, last_message_sender_uid, last_message_timestamp
 */
$sql = "
SELECT
    chat_id,
    participant1_uid,
    participant2_uid,
    last_message,
    last_message_sender_uid,
    last_message_timestamp
FROM chats
WHERE participant1_uid = '$uidEsc'
   OR participant2_uid = '$uidEsc'
ORDER BY last_message_timestamp DESC, chat_id ASC
LIMIT $limit
";

$res = $conn->query($sql);
if (!$res) {
    json_response(false, "Query failed: " . $conn->error, null, 500);
}

$chats = [];
while ($row = $res->fetch_assoc()) {
    $chatId     = $row['chat_id'];
    $p1         = $row['participant1_uid'];
    $p2         = $row['participant2_uid'];
    $lastMsg    = $row['last_message'] ?? "";
    $lastSender = $row['last_message_sender_uid'] ?? "";
    $lastTs     = isset($row['last_message_timestamp']) ? (int)$row['last_message_timestamp'] : 0;

    // Build participants map: { uid1: true, uid2: true }
    $participants = [
        $p1 => true,
        $p2 => true
    ];

    $chats[] = [
        "chatId"               => $chatId,
        "participants"         => $participants,
        "lastMessage"          => $lastMsg,
        "lastMessageTimestamp" => $lastTs,
        "lastMessageSenderId"  => $lastSender
    ];
}

$data = [
    "uid"   => $uid,
    "count" => count($chats),
    "chats" => $chats
];

json_response(true, "Chats loaded", $data);
