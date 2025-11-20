<?php
// stories_bubbles_get.php
include_once __DIR__ . "/config.php";
include_once __DIR__ . "/response.php";
include_once __DIR__ . "/utils.php";

require_method('GET');
require_fields(['uid'], 'GET');

$uid    = get_field('uid');
$uidEsc = db_escape($conn, $uid);
$nowMs  = now_ms();

// 1) Make sure user exists
$userRes = $conn->query("
    SELECT firebase_uid, username, profile_picture_url, photo
    FROM users
    WHERE firebase_uid = '$uidEsc'
    LIMIT 1
");
if (!$userRes || $userRes->num_rows === 0) {
    json_response(false, "User not found", null, 404);
}
$selfRow = $userRes->fetch_assoc();

// 2) Build list of uids = me + people I follow
$uids = [$uid];

$followRes = $conn->query("
    SELECT following_uid
    FROM following
    WHERE user_uid = '$uidEsc'
");
if ($followRes) {
    while ($row = $followRes->fetch_assoc()) {
        if (!empty($row['following_uid'])) {
            $uids[] = $row['following_uid'];
        }
    }
}
$uids = array_values(array_unique($uids));

if (empty($uids)) {
    // You only see your own bubble
    $data = [[
        "uid"        => $selfRow['firebase_uid'],
        "username"   => $selfRow['username'] ?: "You",
        "profileUrl" => choose_avatar($selfRow),
        "hasStories" => false
    ]];
    json_response(true, "Story bubbles loaded", $data);
}

// Prepare IN list
$uidsEsc = array_map(function($u) use ($conn) {
    return "'" . db_escape($conn, $u) . "'";
}, $uids);
$inList = implode(",", $uidsEsc);

// 3) Find which of those users currently have active stories
$storyRes = $conn->query("
    SELECT DISTINCT user_uid
    FROM stories
    WHERE user_uid IN ($inList)
      AND expires_at > $nowMs
");
$haveStory = [];
if ($storyRes) {
    while ($row = $storyRes->fetch_assoc()) {
        $haveStory[$row['user_uid']] = true;
    }
}

// 4) Fetch basic user info for all those uids
$userSql = "
    SELECT firebase_uid, username, profile_picture_url, photo
    FROM users
    WHERE firebase_uid IN ($inList)
";
$usersRes = $conn->query($userSql);

$usersInfo = [];
if ($usersRes) {
    while ($row = $usersRes->fetch_assoc()) {
        $fu = $row['firebase_uid'];
        $usersInfo[$fu] = [
            "uid"        => $fu,
            "username"   => $row['username'] ?: "user",
            "profileUrl" => choose_avatar($row),
            "hasStories" => !empty($haveStory[$fu])
        ];
    }
}

// 5) Build final bubble list
$bubbles = [];

// Always put self first
if (isset($usersInfo[$uid])) {
    $self = $usersInfo[$uid];
    $self['username'] = $self['username'] ?: "You";
    $bubbles[] = $self;
} else {
    // fallback if somehow missing
    $bubbles[] = [
        "uid"        => $selfRow['firebase_uid'],
        "username"   => $selfRow['username'] ?: "You",
        "profileUrl" => choose_avatar($selfRow),
        "hasStories" => !empty($haveStory[$uid])
    ];
}

// Then all other users (people I follow)
foreach ($usersInfo as $u => $info) {
    if ($u === $uid) continue;
    $bubbles[] = $info;
}

json_response(true, "Story bubbles loaded", $bubbles);


/**
 * Helper: choose avatar field (URL > base64)
 */
function choose_avatar(array $row): ?string {
    $url   = trim((string)($row['profile_picture_url'] ?? ''));
    $photo = trim((string)($row['photo'] ?? ''));

    if ($url !== '') return $url;
    if ($photo !== '') return $photo; // base64
    return null;
}
