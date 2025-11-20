<?php
// profile_posts_get.php
include_once __DIR__ . "/config.php";
include_once __DIR__ . "/response.php";
include_once __DIR__ . "/utils.php";

require_method('GET');

$targetUid = get_field('targetUid', '');
if ($targetUid === '') {
    json_response(false, "targetUid is required", null, 400);
}

$targetUidEsc = db_escape($conn, $targetUid);

// all posts by this user, newest first
$sql = "
    SELECT
        firebase_post_id,
        user_uid,
        caption,
        image_url,
        image_base64,
        created_at,
        like_count,
        comment_count
    FROM posts
    WHERE user_uid = '$targetUidEsc'
    ORDER BY created_at DESC
";

$res = $conn->query($sql);
$posts = [];

if ($res) {
    while ($row = $res->fetch_assoc()) {
        $posts[] = [
            "postId"       => $row['firebase_post_id'],
            "uid"          => $row['user_uid'],
            "username"     => "", // can fill on client or join with users if you want
            "caption"      => $row['caption'] ?: "",
            "imageUrl"     => $row['image_url'] ?: "",
            "imageBase64"  => $row['image_base64'] ?: "",
            "createdAt"    => (int)($row['created_at'] ?? 0),
            "likeCount"    => (int)($row['like_count'] ?? 0),
            "commentCount" => (int)($row['comment_count'] ?? 0)
        ];
    }
}

json_response(true, "User posts loaded", $posts);
