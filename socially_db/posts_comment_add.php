<?php
include_once __DIR__ . "/config.php";
include_once __DIR__ . "/response.php";
include_once __DIR__ . "/utils.php";

require_method('POST');
require_fields(['uid', 'postId', 'text'], 'POST');

$uid    = post_field('uid');
$postId = post_field('postId');
$text   = post_field('text');

if (trim($text) === "") {
    json_response(false, "Comment text cannot be empty", null, 400);
}

$uidEsc    = db_escape($conn, $uid);
$postIdEsc = db_escape($conn, $postId);
$textEsc   = db_escape($conn, $text);

// Check post exists
$postRes = $conn->query("SELECT id FROM posts WHERE firebase_post_id = '$postIdEsc' LIMIT 1");
if (!$postRes || $postRes->num_rows === 0) {
    json_response(false, "Post not found", null, 404);
}

// Get username
$userRes = $conn->query("SELECT username FROM users WHERE firebase_uid = '$uidEsc' LIMIT 1");
$username = "user";
if ($userRes && $userRes->num_rows > 0) {
    $row = $userRes->fetch_assoc();
    $username = $row['username'] ?: "user";
}

$usernameEsc = db_escape($conn, $username);
$commentId   = generate_id(32);
$commentIdEsc = db_escape($conn, $commentId);
$createdAt   = now_ms();

// Insert comment
$sql = "
INSERT INTO post_comments (
    comment_id, post_id, user_uid, username, text, created_at
) VALUES (
    '$commentIdEsc', '$postIdEsc', '$uidEsc', '$usernameEsc', '$textEsc', $createdAt
)";
if (!$conn->query($sql)) {
    json_response(false, "Failed to add comment: " . $conn->error, null, 500);
}

// Recalculate comment count
$countRes = $conn->query("SELECT COUNT(*) AS c FROM post_comments WHERE post_id = '$postIdEsc'");
$row = $countRes->fetch_assoc();
$newCount = (int)$row['c'];

$conn->query("UPDATE posts SET comment_count = $newCount WHERE firebase_post_id = '$postIdEsc'");
$conn->query("UPDATE post_index SET comment_count = $newCount WHERE post_id = '$postIdEsc'");

$data = [
    "commentId"       => $commentId,
    "postId"          => $postId,
    "uid"             => $uid,
    "username"        => $username,
    "text"            => $text,
    "createdAt"       => $createdAt,
    "newCommentCount" => $newCount
];

json_response(true, "Comment added", $data);
