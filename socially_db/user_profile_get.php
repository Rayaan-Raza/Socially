<?php
// profile_get.php
include_once __DIR__ . "/config.php";
include_once __DIR__ . "/response.php";
include_once __DIR__ . "/utils.php";

require_method('GET');

// current viewer
$uid       = get_field('uid', '');
$targetUid = get_field('targetUid', '');

if ($uid === '' || $targetUid === '') {
    json_response(false, "uid and targetUid are required", null, 400);
}

$uidEsc       = db_escape($conn, $uid);
$targetUidEsc = db_escape($conn, $targetUid);

// 1) Load target user
$userSql = "
    SELECT 
        firebase_uid,
        username,
        full_name,
        bio,
        profile_picture_url,
        is_private
    FROM users
    WHERE firebase_uid = '$targetUidEsc'
    LIMIT 1
";

$userRes = $conn->query($userSql);
if (!$userRes || $userRes->num_rows === 0) {
    json_response(false, "User not found", null, 404);
}

$userRow = $userRes->fetch_assoc();

// 2) Counts: posts, followers, following
$counts = [
    'posts'     => 0,
    'followers' => 0,
    'following' => 0
];

// postsCount
$postRes = $conn->query("
    SELECT COUNT(*) AS c 
    FROM posts 
    WHERE user_uid = '$targetUidEsc'
");
if ($postRes) {
    $r = $postRes->fetch_assoc();
    $counts['posts'] = (int)$r['c'];
}

// followersCount: how many users follow target
$followersRes = $conn->query("
    SELECT COUNT(*) AS c
    FROM following
    WHERE following_uid = '$targetUidEsc'
");
if ($followersRes) {
    $r = $followersRes->fetch_assoc();
    $counts['followers'] = (int)$r['c'];
}

// followingCount: how many users target is following
$followingRes = $conn->query("
    SELECT COUNT(*) AS c
    FROM following
    WHERE user_uid = '$targetUidEsc'
");
if ($followingRes) {
    $r = $followingRes->fetch_assoc();
    $counts['following'] = (int)$r['c'];
}

// 3) Relationship state (viewer -> target)
$isFollowing = false;
$isRequested = false;

// following?
$folRes = $conn->query("
    SELECT COUNT(*) AS c
    FROM following
    WHERE user_uid = '$uidEsc'
      AND following_uid = '$targetUidEsc'
");
if ($folRes) {
    $r = $folRes->fetch_assoc();
    $isFollowing = ((int)$r['c'] > 0);
}

// requested?
$reqRes = $conn->query("
    SELECT COUNT(*) AS c
    FROM follow_requests
    WHERE from_uid = '$uidEsc'
      AND to_uid = '$targetUidEsc'
");
if ($reqRes) {
    $r = $reqRes->fetch_assoc();
    $isRequested = ((int)$r['c'] > 0);
}

// derive state like your Kotlin enum
$state = "NOT_FOLLOWING";
if ($isFollowing) {
    $state = "FOLLOWING";
} elseif ($isRequested) {
    $state = "REQUESTED";
}

$data = [
    "user" => [
        "uid"              => $userRow['firebase_uid'],
        "username"         => $userRow['username'] ?: "",
        "fullName"         => $userRow['full_name'] ?: "",
        "bio"              => $userRow['bio'] ?: "",
        "profilePictureUrl"=> $userRow['profile_picture_url'] ?: "",
        "isPrivate"        => (bool)$userRow['is_private'],
        "postsCount"       => $counts['posts'],
        "followersCount"   => $counts['followers'],
        "followingCount"   => $counts['following'],
    ],
    "relationship" => [
        "isFollowing" => $isFollowing,
        "isRequested" => $isRequested,
        "state"       => $state
    ]
];

json_response(true, "Profile loaded", $data);
