<?php
// stories_get_user.php
include_once __DIR__ . "/config.php";
include_once __DIR__ . "/response.php";
include_once __DIR__ . "/utils.php";

// Allow GET only
require_method('GET');

// Expect user_uid in query ?user_uid=...
if (!isset($_GET['user_uid'])) {
    json_response(false, "user_uid is required.", null, 400);
}

$userUid = $_GET['user_uid'];
if ($userUid === '') {
    json_response(false, "user_uid cannot be empty.", null, 400);
}

$userEsc = db_escape($conn, $userUid);

// Verify user exists
$uRes = $conn->query("SELECT id FROM users WHERE firebase_uid = '$userEsc' LIMIT 1");
if (!$uRes || $uRes->num_rows === 0) {
    json_response(false, "User not found.", null, 404);
}

// Current time in ms
$nowMs = (int) round(microtime(true) * 1000);

$sql = "
    SELECT story_id, user_uid, media_base64, created_at, expires_at
    FROM stories
    WHERE user_uid = '$userEsc'
      AND expires_at > $nowMs
    ORDER BY created_at DESC
";

$res = $conn->query($sql);
if (!$res) {
    json_response(false, "DB error: " . $conn->error, null, 500);
}

$stories = [];
while ($row = $res->fetch_assoc()) {
    $stories[] = [
        "storyId"      => $row['story_id'],
        "uid"          => $row['user_uid'],
        "mediaBase64"  => $row['media_base64'],
        "createdAt"    => (int) $row['created_at'],
        "expiresAt"    => (int) $row['expires_at']
    ];
}

json_response(true, "Stories fetched", [
    "user_uid" => $userUid,
    "count"    => count($stories),
    "stories"  => $stories
]);
