<?php
// user_basic_get.php
include_once __DIR__ . "/config.php";
include_once __DIR__ . "/response.php";
include_once __DIR__ . "/utils.php";

require_method('GET');
require_fields(['uid'], 'GET');

$uid = get_field('uid');
$uidEsc = db_escape($conn, $uid);

$sql = "
SELECT
    firebase_uid,
    username,
    full_name,
    profile_picture_url,
    photo,
    is_online,
    last_seen
FROM users
WHERE firebase_uid = '$uidEsc'
LIMIT 1
";

$res = $conn->query($sql);
if (!$res) {
    json_response(false, "Query failed: " . $conn->error, null, 500);
}

if ($res->num_rows === 0) {
    json_response(false, "User not found", null, 404);
}

$row = $res->fetch_assoc();

// Decide avatar: prefer URL, else base64/photo
$avatar       = null;
$avatarType   = null;
$profileUrl   = trim((string)($row['profile_picture_url'] ?? ''));
$photoBase64  = trim((string)($row['photo'] ?? ''));

if ($profileUrl !== '') {
    $avatar     = $profileUrl;
    $avatarType = 'url';
} elseif ($photoBase64 !== '') {
    $avatar     = $photoBase64;
    $avatarType = 'base64';
}

$data = [
    "uid"        => $row['firebase_uid'],
    "username"   => $row['username'] ?? "",
    "fullName"   => $row['full_name'] ?? "",
    "avatar"     => $avatar,        // can be URL or base64 or null
    "avatarType" => $avatarType,    // "url", "base64" or null
    "isOnline"   => isset($row['is_online']) ? (bool)$row['is_online'] : false,
    "lastSeen"   => isset($row['last_seen']) ? (int)$row['last_seen'] : 0
];

json_response(true, "User loaded", $data);
