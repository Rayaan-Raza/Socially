<?php
// followers_list.php
include_once __DIR__ . "/config.php";
include_once __DIR__ . "/response.php";
include_once __DIR__ . "/utils.php";

require_method('GET');
require_fields(['uid'], 'GET');

$uid    = get_field('uid');
$uidEsc = db_escape($conn, $uid);

// 1) Get follower UIDs for this user
$followerUids = [];

$sqlFollowers = "
    SELECT follower_uid
    FROM followers
    WHERE user_uid = '$uidEsc'
";
$resFollowers = $conn->query($sqlFollowers);

if ($resFollowers) {
    while ($row = $resFollowers->fetch_assoc()) {
        if (!empty($row['follower_uid'])) {
            $followerUids[] = $row['follower_uid'];
        }
    }
}

if (empty($followerUids)) {
    // No followers – return empty list
    json_response(true, "No followers", []);
}

// 2) Fetch user info for all follower UIDs
$followerUids = array_values(array_unique($followerUids));

$uidsEsc = array_map(function($u) use ($conn) {
    return "'" . db_escape($conn, $u) . "'";
}, $followerUids);

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

$followers = [];
while ($row = $resUsers->fetch_assoc()) {
    $followers[] = [
        "uid"             => $row['firebase_uid'],
        "username"        => $row['username'] ?: "",
        "fullName"        => $row['full_name'] ?: "",
        "bio"             => $row['bio'] ?: "",
        "profilePictureUrl" => $row['profile_picture_url'] ?: "",
        "photo"           => $row['photo'] ?: "",
        "followersCount"  => isset($row['followers_count']) ? (int)$row['followers_count'] : 0,
        "followingCount"  => isset($row['following_count']) ? (int)$row['following_count'] : 0,
        "postsCount"      => isset($row['posts_count']) ? (int)$row['posts_count'] : 0,
    ];
}

json_response(true, "Followers loaded", $followers);
