<?php
// stories_get_feed.php
include_once __DIR__ . "/config.php";
include_once __DIR__ . "/response.php";
include_once __DIR__ . "/utils.php";

require_method('GET');

if (!isset($_GET['user_uid'])) {
    json_response(false, "user_uid is required.", null, 400);
}

$userUid = $_GET['user_uid'];
if ($userUid === '') {
    json_response(false, "user_uid cannot be empty.", null, 400);
}

$userEsc = db_escape($conn, $userUid);

// Check user exists
$uRes = $conn->query("SELECT id FROM users WHERE firebase_uid = '$userEsc' LIMIT 1");
if (!$uRes || $uRes->num_rows === 0) {
    json_response(false, "User not found.", null, 404);
}

// Build list of user_uids: self + following
$uids = [$userEsc];

$fRes = $conn->query("
    SELECT following_uid 
    FROM following 
    WHERE user_uid = '$userEsc'
");
if ($fRes) {
    while ($row = $fRes->fetch_assoc()) {
        if (!empty($row['following_uid'])) {
            $uids[] = db_escape($conn, $row['following_uid']);
        }
    }
}

// Remove duplicates
$uids = array_unique($uids);

// If somehow empty (shouldn’t happen), just return empty list
if (count($uids) === 0) {
    json_response(true, "No stories found.", [
        "viewer_uid"  => $userUid,
        "total_count" => 0,
        "stories"     => []
    ]);
    exit;
}

// Create IN ('u1','u2','u3')
$inList = "'" . implode("','", $uids) . "'";

// Current time in ms
$nowMs = (int) round(microtime(true) * 1000);

$sql = "
    SELECT story_id, user_uid, media_base64, created_at, expires_at
    FROM stories
    WHERE user_uid IN ($inList)
      AND expires_at > $nowMs
    ORDER BY user_uid, created_at DESC
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
    "viewer_uid"  => $userUid,
    "total_count" => count($stories),
    "stories"     => $stories
]);
