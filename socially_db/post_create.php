<?php
include_once __DIR__ . "/config.php";
include_once __DIR__ . "/response.php";
include_once __DIR__ . "/utils.php";

require_method('POST');
require_fields(['uid'], 'POST'); // caption and image are optional

$uid         = post_field('uid');
$caption     = post_field('caption', '');
$imageBase64 = post_field('imageBase64', '');
$imageUrl    = post_field('imageUrl', '');

$uidEsc     = db_escape($conn, $uid);
$captionEsc = db_escape($conn, $caption);
$imageBEsc  = db_escape($conn, $imageBase64);
$imageUEsc  = db_escape($conn, $imageUrl);

// make sure user exists
$userRes = $conn->query("SELECT username FROM users WHERE firebase_uid = '$uidEsc' LIMIT 1");
if (!$userRes || $userRes->num_rows === 0) {
    json_response(false, "User not found", null, 404);
}
$userRow  = $userRes->fetch_assoc();
$username = $userRow['username'] ?: "user";

// require at least something (caption or image)
if (trim($caption) === "" && trim($imageBase64) === "" && trim($imageUrl) === "") {
    json_response(false, "Post must have caption or image", null, 400);
}

$firebasePostId   = generate_id(32);
$firebasePostIdEsc = db_escape($conn, $firebasePostId);
$nowMs            = now_ms();

// Insert into posts
$sql = "
INSERT INTO posts (
    firebase_post_id,
    user_uid,
    caption,
    image_base64,
    image_url,
    created_at,
    like_count,
    comment_count
) VALUES (
    '$firebasePostIdEsc',
    '$uidEsc',
    '$captionEsc',
    '$imageBEsc',
    '$imageUEsc',
    $nowMs,
    0,
    0
)";
if (!$conn->query($sql)) {
    json_response(false, "Failed to create post: " . $conn->error, null, 500);
}

// Insert into post_index
$idxSql = "
INSERT INTO post_index (
    post_id,
    user_uid,
    created_at,
    like_count,
    comment_count
) VALUES (
    '$firebasePostIdEsc',
    '$uidEsc',
    $nowMs,
    0,
    0
)
ON DUPLICATE KEY UPDATE
    created_at = VALUES(created_at)
";
$conn->query($idxSql);

// Increment user's postsCount (if column exists)
$conn->query("
    UPDATE users 
    SET posts_count = COALESCE(posts_count, 0) + 1 
    WHERE firebase_uid = '$uidEsc'
");

$data = [
    "postId"       => $firebasePostId,
    "uid"          => $uid,
    "username"     => $username,
    "caption"      => $caption,
    "imageUrl"     => $imageUrl,
    "imageBase64"  => $imageBase64,
    "createdAt"    => $nowMs,
    "likeCount"    => 0,
    "commentCount" => 0
];

json_response(true, "Post created", $data);
