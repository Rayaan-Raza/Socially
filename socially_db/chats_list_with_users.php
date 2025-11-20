<?php
// chats_list_with_users.php
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

// 1) Verify user exists
$userSql = "SELECT 1 FROM users WHERE firebase_uid = '$uidEsc' LIMIT 1";
$userRes = $conn->query($userSql);
if (!$userRes) {
    json_response(false, "User check failed: " . $conn->error, null, 500);
}
if ($userRes->num_rows === 0) {
    json_response(false, "User not found", null, 404);
}

// 2) Load chats where this user is a participant
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
$otherUids = []; // collect all "other" user IDs to fetch profiles in one query

while ($row = $res->fetch_assoc()) {
    $chatId     = $row['chat_id'];
    $p1         = $row['participant1_uid'];
    $p2         = $row['participant2_uid'];
    $lastMsg    = $row['last_message'] ?? "";
    $lastSender = $row['last_message_sender_uid'] ?? "";
    $lastTs     = isset($row['last_message_timestamp']) ? (int)$row['last_message_timestamp'] : 0;

    // participants map like your ChatSession expects
    $participants = [
        $p1 => true,
        $p2 => true
    ];

    // determine "other" user (not the viewer)
    $otherUid = ($p1 === $uid) ? $p2 : $p1;
    if ($otherUid !== '' && $otherUid !== $uid) {
        $otherUids[] = $otherUid;
    }

    $chats[] = [
        "chatId"               => $chatId,
        "participants"         => $participants,
        "lastMessage"          => $lastMsg,
        "lastMessageTimestamp" => $lastTs,
        "lastMessageSenderId"  => $lastSender,
        // placeholder to be filled later
        "otherUser"            => null
    ];
}

// 3) If there are no chats, just return empty list
if (empty($chats)) {
    json_response(true, "No chats found", [
        "uid"   => $uid,
        "count" => 0,
        "chats" => []
    ]);
}

// 4) Fetch profile data for all distinct "other" UIDs in one query
$otherUids = array_unique($otherUids);

$profiles = [];
if (!empty($otherUids)) {
    $escaped = array_map(function($u) use ($conn) {
        return "'" . db_escape($conn, $u) . "'";
    }, $otherUids);
    $inList = implode(",", $escaped);

    $uSql = "
        SELECT
            firebase_uid,
            username,
            full_name,
            profile_picture_url,
            photo,
            is_online,
            last_seen
        FROM users
        WHERE firebase_uid IN ($inList)
    ";
    $uRes = $conn->query($uSql);
    if ($uRes) {
        while ($uRow = $uRes->fetch_assoc()) {
            $otherUid = $uRow['firebase_uid'];

            $profileUrl  = trim((string)($uRow['profile_picture_url'] ?? ''));
            $photoBase64 = trim((string)($uRow['photo'] ?? ''));

            $avatar     = null;
            $avatarType = null;

            if ($profileUrl !== '') {
                $avatar     = $profileUrl;
                $avatarType = 'url';
            } elseif ($photoBase64 !== '') {
                $avatar     = $photoBase64;
                $avatarType = 'base64';
            }

            $profiles[$otherUid] = [
                "uid"        => $otherUid,
                "username"   => $uRow['username'] ?? "",
                "fullName"   => $uRow['full_name'] ?? "",
                "avatar"     => $avatar,
                "avatarType" => $avatarType,
                "isOnline"   => isset($uRow['is_online']) ? (bool)$uRow['is_online'] : false,
                "lastSeen"   => isset($uRow['last_seen']) ? (int)$uRow['last_seen'] : 0
            ];
        }
    }
}

// 5) Attach otherUser profile to each chat (if found)
foreach ($chats as &$chat) {
    $participants = $chat['participants'];
    $otherUid = null;
    foreach ($participants as $pid => $_) {
        if ($pid !== $uid) {
            $otherUid = $pid;
            break;
        }
    }
    if ($otherUid !== null && isset($profiles[$otherUid])) {
        $chat['otherUser'] = $profiles[$otherUid];
    }
}
unset($chat); // break ref

// 6) Final response
$data = [
    "uid"   => $uid,
    "count" => count($chats),
    "chats" => $chats
];

json_response(true, "Chats loaded", $data);
