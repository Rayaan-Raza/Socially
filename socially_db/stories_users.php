<?php
include_once __DIR__ . "/config.php";
include_once __DIR__ . "/response.php";
include_once __DIR__ . "/utils.php";

require_method('GET');

$uid = get_field('uid', '');
if ($uid === '') {
    json_response(false, "uid is required", null, 400);
}

$uidEsc = db_escape($conn, $uid);

// collect user + following list
$uids = [];
$uids[] = $uid;

// following table: user_uid → following_uid
$followRes = $conn->query("SELECT following_uid FROM following WHERE user_uid = '$uidEsc'");
if ($followRes) {
    while ($row = $followRes->fetch_assoc()) {
        if (!empty($row['following_uid'])) {
            $uids[] = $row['following_uid'];
        }
    }
}

// remove duplicates
$uids = array_values(array_unique($uids));

if (empty($uids)) {
    json_response(true, "No users found", []);
}

// build IN list
$uidsEsc = array_map(function($u) use ($conn) {
    return "'" . db_escape($conn, $u) . "'";
}, $uids);
$inList = implode(",", $uidsEsc);

// get user info
$userSql = "
SELECT firebase_uid, username, profile_picture_url
FROM users
WHERE firebase_uid IN ($inList)
";
$userRes = $conn->query($userSql);

$usersMap = [];
if ($userRes) {
    while ($row = $userRes->fetch_assoc()) {
        $usersMap[$row['firebase_uid']] = [
            "uid" => $row['firebase_uid'],
            "username" => $row['username'] ?: "user",
            "profilePictureUrl" => $row['profile_picture_url'] ?: ""
        ];
    }
}

// map who has active stories (expires_at > now)
$now = now_ms();
$storyRes = $conn->query("
    SELECT DISTINCT user_uid 
    FROM stories 
    WHERE expires_at IS NULL OR expires_at = 0 OR expires_at > $now
");
$hasStory = [];
if ($storyRes) {
    while ($row = $storyRes->fetch_assoc()) {
        $hasStory[$row['user_uid']] = true;
    }
}

$result = [];
foreach ($uids as $u) {
    if (!isset($usersMap[$u])) continue;
    $info = $usersMap[$u];
    $result[] = [
        "uid" => $info['uid'],
        "username" => $info['username'],
        "profilePictureUrl" => $info['profilePictureUrl'],
        "hasStory" => isset($hasStory[$u])
    ];
}

json_response(true, "Stories users fetched", $result);
