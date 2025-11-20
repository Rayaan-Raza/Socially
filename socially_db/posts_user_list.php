<?php
include_once __DIR__ . "/config.php";
include_once __DIR__ . "/response.php";
include_once __DIR__ . "/utils.php";

require_method('GET');
require_fields(['uid'], 'GET');

$uid    = get_field('uid');
$uidEsc = db_escape($conn, $uid);

// get all posts for this user, newest first
$sql = "
    SELECT 
        firebase_post_id,
        user_uid,
        caption,
        image_base64,
        image_url,
        created_at,
        like_count,
        comment_count
    FROM posts
    WHERE user_uid = '$uidEsc'
    ORDER BY created_at DESC
";

$res = $conn->query($sql);
if (!$res) {
    json_response(false, "DB error: " . $conn->error, null, 500);
}

$posts = [];
while ($row = $res->fetch_assoc()) {
    $posts[] = [
        "postId"       => $row['firebase_post_id'],
        "uid"          => $row['user_uid'],
        "username"     => "", // optional: fill via join if you want
        "caption"      => $row['caption'] ?: "",
        "imageUrl"     => $row['image_url'] ?: "",
        "imageBase64"  => $row['image_base64'] ?: "",
        "createdAt"    => (int)($row['created_at'] ?? 0),
        "likeCount"    => (int)($row['like_count'] ?? 0),
        "commentCount" => (int)($row['comment_count'] ?? 0),
    ];
}

json_response(true, "User posts loaded", $posts);
