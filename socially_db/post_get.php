<?php
// post_get.php
include_once __DIR__ . "/config.php";
include_once __DIR__ . "/response.php";
include_once __DIR__ . "/utils.php";

require_method('GET');
require_fields(['postId'], 'GET');

$postId   = get_field('postId');
$postIdEsc = db_escape($conn, $postId);

/*
 * We assume:
 *   posts.firebase_post_id = postId
 *   posts.user_uid references users.firebase_uid
 */
$sql = "
    SELECT 
        p.firebase_post_id,
        p.user_uid,
        p.image_base64,
        p.image_url,
        p.media_type,
        p.caption,
        p.created_at,
        p.like_count,
        p.comment_count,
        u.username,
        u.full_name,
        u.profile_picture_url,
        u.photo
    FROM posts p
    JOIN users u
      ON p.user_uid = u.firebase_uid
    WHERE p.firebase_post_id = '$postIdEsc'
    LIMIT 1
";

$res = $conn->query($sql);
if (!$res) {
    json_response(false,"DB error: " . $conn->error, null, 500);
}

if ($res->num_rows === 0) {
    json_response(false, "Post not found", null, 404);
}

$row = $res->fetch_assoc();

// Decide avatar
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
    "postId"       => $row['firebase_post_id'],
    "uid"          => $row['user_uid'],
    "username"     => $row['username'] ?? "",
    "fullName"     => $row['full_name'] ?? "",
    "avatar"       => $avatar,
    "avatarType"   => $avatarType,
    "imageBase64"  => $row['image_base64'] ?? "",
    "imageUrl"     => $row['image_url'] ?? "",
    "mediaType"    => $row['media_type'] ?? "image",
    "caption"      => $row['caption'] ?? "",
    "createdAt"    => isset($row['created_at']) ? (int)$row['created_at'] : 0,
    "likeCount"    => isset($row['like_count']) ? (int)$row['like_count'] : 0,
    "commentCount" => isset($row['comment_count']) ? (int)$row['comment_count'] : 0
];

json_response(true, "Post loaded", $data);
