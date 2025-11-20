<?php
// stories_user_latest.php
include_once __DIR__ . "/config.php";
include_once __DIR__ . "/response.php";
include_once __DIR__ . "/utils.php";

require_method('GET');
require_fields(['uid'], 'GET');

$uid    = get_field('uid');
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

// Current time in ms
$nowMs = now_ms();

// Find latest (max created_at) non-expired story for this user
$sql = "
SELECT
    story_id,
    user_uid,
    media_base64,
    created_at,
    expires_at
FROM stories
WHERE user_uid = '$uidEsc'
  AND expires_at > $nowMs
ORDER BY created_at DESC
LIMIT 1
";

$res = $conn->query($sql);
if (!$res) {
    json_response(false, "Query failed: " . $conn->error, null, 500);
}

if ($res->num_rows === 0) {
    // No active story
    json_response(false, "No active stories", [
        "uid" => $uid
    ], 404);
}

$row = $res->fetch_assoc();

// Build response payload
$data = [
    "storyId"      => $row['story_id'],
    "uid"          => $row['user_uid'],
    "mediaBase64"  => $row['media_base64'],
    "mediaType"    => "image", // hard-coded for now
    "createdAt"    => (int)$row['created_at'],
    "expiresAt"    => (int)$row['expires_at']
];

// success
json_response(true, "Latest story loaded", $data);
