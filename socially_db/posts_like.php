<?php
include_once __DIR__ . "/config.php";
include_once __DIR__ . "/response.php";
include_once __DIR__ . "/utils.php";

require_method('POST');
require_fields(['uid', 'postId', 'liked'], 'POST');

$uid    = post_field('uid');
$postId = post_field('postId');
$liked  = post_field('liked'); // "1" or "0"

$uidEsc    = db_escape($conn, $uid);
$postIdEsc = db_escape($conn, $postId);
$likedBool = ($liked === "1" || $liked === "true" || $liked === "on");

// Check post exists
$postRes = $conn->query("SELECT id FROM posts WHERE firebase_post_id = '$postIdEsc' LIMIT 1");
if (!$postRes || $postRes->num_rows === 0) {
    json_response(false, "Post not found", null, 404);
}

if ($likedBool) {
    // Insert like (avoid duplicates)
    $sqlLike = "
        INSERT IGNORE INTO post_likes (post_id, user_uid)
        VALUES ('$postIdEsc', '$uidEsc')
    ";
    if (!$conn->query($sqlLike)) {
        json_response(false, "Failed to like post: " . $conn->error, null, 500);
    }
} else {
    // Remove like
    $sqlUnlike = "
        DELETE FROM post_likes
        WHERE post_id = '$postIdEsc' AND user_uid = '$uidEsc'
    ";
    if (!$conn->query($sqlUnlike)) {
        json_response(false, "Failed to unlike post: " . $conn->error, null, 500);
    }
}

// Recalculate like count
$countRes = $conn->query("SELECT COUNT(*) AS c FROM post_likes WHERE post_id = '$postIdEsc'");
$row = $countRes->fetch_assoc();
$newCount = (int)$row['c'];

// Update posts & post_index
$conn->query("UPDATE posts SET like_count = $newCount WHERE firebase_post_id = '$postIdEsc'");
$conn->query("UPDATE post_index SET like_count = $newCount WHERE post_id = '$postIdEsc'");

$data = [
    "postId"    => $postId,
    "liked"     => $likedBool,
    "likeCount" => $newCount
];

json_response(true, "Like updated", $data);
