<?php
// following_list.php
include_once __DIR__ . "/config.php";
include_once __DIR__ . "/response.php";
include_once __DIR__ . "/utils.php";

require_method('GET');
require_fields(['uid'], 'GET');

$uid    = get_field('uid');
$uidEsc = db_escape($conn, $uid);

// 1) Get all UIDs I am following
$followingUids = [];

$sqlFollowing = "
    SELECT following_uid
    FROM following
    WHERE user_uid = '$uidEsc'
";
$resFollowing = $conn->query($sqlFollowing);

if ($resFollowing) {
    while ($row = $resFollowing->fetch_assoc()) {
        if (!empty($row['following_uid'])) {
            $followingUids[] = $row['following_uid'];
        }
    }
}

if (empty($followingUids)) {
    // No friends / following → return empty list
    json_response(true, "No friends found", []);
}

// 2) Fetch user info for those UIDs
$followingUids = array_values(array_unique($followingUids));

$uidsEsc = array_map(function($u) use ($conn) {
    return "'" . db_escape($conn, $u) . "'";
}, $followingUids);

$inList = implode(",", $uidsEsc);

$sqlUsers = "
    SELECT
        firebase_uid,
        username,
        full_name,
        bio,
        profile_picture_url,
        photo,
        followers_count,
        following_count,
        posts_count
    FROM users
    WHERE firebase_uid IN ($inList)
";

$resUsers = $conn->query($sqlUsers);
if (!$resUsers) {
    json_response(false, "DB error: " . $conn->error, null, 500);
}

$friends = [];
while ($row = $resUsers->fetch_assoc()) {
    $friends[] = [
        "uid"             => $row['firebase_uid'],
        "username"        => $row['username']           ?? "",
        "fullName"        => $row['full_name']          ?? "",
        "bio"             => $row['bio']                ?? "",
        "profilePictureUrl" => $row['profile_picture_url'] ?? "",
        "photo"           => $row['photo']              ?? "",
        "followersCount"  => isset($row['followers_count'])  ? (int)$row['followers_count']  : 0,
        "followingCount"  => isset($row['following_count'])  ? (int)$row['following_count']  : 0,
        "postsCount"      => isset($row['posts_count'])      ? (int)$row['posts_count']      : 0,
    ];
}

json_response(true, "Friends loaded", $friends);
