<?php
// chat_create.php
include_once __DIR__ . "/config.php";
include_once __DIR__ . "/response.php";
include_once __DIR__ . "/utils.php";

require_method('POST');
require_fields(['uid1', 'uid2'], 'POST');

$uid1 = trim(post_field('uid1'));
$uid2 = trim(post_field('uid2'));

if ($uid1 === $uid2) {
    json_response(false, "Cannot create chat with yourself", null, 400);
}

// Deterministic chat ID
function generate_chat_id($a, $b) {
    return ($a < $b) ? ($a . "_" . $b) : ($b . "_" . $a);
}

$uid1Esc = db_escape($conn, $uid1);
$uid2Esc = db_escape($conn, $uid2);

// 1) Ensure both users exist
$checkSql = "
    SELECT firebase_uid 
    FROM users 
    WHERE firebase_uid IN ('$uid1Esc', '$uid2Esc')
";
$res = $conn->query($checkSql);
if (!$res || $res->num_rows !== 2) {
    json_response(false, "One or both users do not exist", null, 404);
}

$chatId = generate_chat_id($uid1, $uid2);
$chatIdEsc = db_escape($conn, $chatId);

// To keep participant columns consistent with chatId generation:
if ($uid1 < $uid2) {
    $p1 = $uid1;
    $p2 = $uid2;
} else {
    $p1 = $uid2;
    $p2 = $uid1;
}
$p1Esc = db_escape($conn, $p1);
$p2Esc = db_escape($conn, $p2);

// 2) Check if chat already exists in `chats`
$chatSql = "
    SELECT chat_id, participant1_uid, participant2_uid, created_at,
           last_message, last_message_sender_uid, last_message_timestamp
    FROM chats
    WHERE chat_id = '$chatIdEsc'
    LIMIT 1
";
$chatRes = $conn->query($chatSql);

if ($chatRes && $chatRes->num_rows === 1) {
    $row = $chatRes->fetch_assoc();

    $data = [
        "chatId"               => $row['chat_id'],
        "participants"         => [$row['participant1_uid'], $row['participant2_uid']],
        "createdAt"            => $row['created_at'] ? (int)$row['created_at'] : null,
        "lastMessage"          => $row['last_message'] ?? "",
        "lastMessageSenderId"  => $row['last_message_sender_uid'] ?? "",
        "lastMessageTimestamp" => $row['last_message_timestamp'] ? (int)$row['last_message_timestamp'] : 0,
        "createdNow"           => false
    ];

    json_response(true, "Chat already existed", $data);
}

// 3) Create new chat in both `chats` and `chat_metadata`
$now = (int)(microtime(true) * 1000);

// Insert into chats
$insertChatsSql = "
    INSERT INTO chats (
        chat_id, participant1_uid, participant2_uid,
        created_at, last_message, last_message_sender_uid, last_message_timestamp
    ) VALUES (
        '$chatIdEsc', '$p1Esc', '$p2Esc',
        $now, '', NULL, $now
    )
";

if (!$conn->query($insertChatsSql)) {
    json_response(false, "Failed to create chat (chats): " . $conn->error, null, 500);
}

// Insert into chat_metadata
$insertMetaSql = "
    INSERT INTO chat_metadata (
        meta_key, chat_id, participant1_uid, participant2_uid,
        last_message, last_message_sender_uid, last_message_timestamp
    ) VALUES (
        '$chatIdEsc', '$chatIdEsc', '$p1Esc', '$p2Esc',
        '', NULL, $now
    )
";

if (!$conn->query($insertMetaSql)) {
    json_response(false, "Failed to create chat (chat_metadata): " . $conn->error, null, 500);
}

$data = [
    "chatId"               => $chatId,
    "participants"         => [$p1, $p2],
    "createdAt"            => $now,
    "lastMessage"          => "",
    "lastMessageSenderId"  => "",
    "lastMessageTimestamp" => $now,
    "createdNow"           => true
];

json_response(true, "Chat created", $data);
