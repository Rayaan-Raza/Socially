<?php
// post_comments_get.php
include_once __DIR__ . "/config.php";
include_once __DIR__ . "/response.php";
include_once __DIR__ . "/utils.php";

require_method('GET');
require_fields(['postId'], 'GET');

$postId    = get_field('postId');
$postIdEsc = db_escape($conn, $postId);

// Optional: limit (for pagination)
// ?limit=100 (default 50, max 200)
$limitRaw = get_field('limit', '50');
$limit    = (int)$limitRaw;
if ($limit <= 0)  $limit = 50;
if ($limit > 200) $limit = 200;

// 1) Check that the post exists
$postRes = $conn->query("
    SELECT id 
    FROM posts 
    WHERE firebase_post_id = '$postIdEsc' 
    LIMIT 1
");
if (!$postRes) {
    json_response(false, "DB error (posts): " . $conn->error, null, 500);
}
if ($postRes->num_rows === 0) {
    json_response(false, "Post not found", null, 404);
}

// 2) Load total number of comments
$countRes = $conn->query("
    SELECT COUNT(*) AS c 
    FROM post_comments 
    WHERE post_id = '$postIdEsc'
");
if (!$countRes) {
    json_response(false, "DB error (count): " . $conn->error, null, 500);
}
$countRow   = $countRes->fetch_assoc();
$totalCount = (int)$countRow['c'];

// 3) Load latest comments (or all, up to $limit)
$commentsSql = "
    SELECT 
        comment_id,
        post_id,
        user_uid,
        username,
        text,
        created_at
    FROM post_comments
    WHERE post_id = '$postIdEsc'
    ORDER BY created_at ASC
    LIMIT $limit
";

$cRes = $conn->query($commentsSql);
if (!$cRes) {
    json_response(false, "DB error (comments): " . $conn->error, null, 500);
}

$comments = [];
while ($row = $cRes->fetch_assoc()) {
    $comments[] = [
        "commentId" => $row['comment_id'],
        "postId"    => $row['post_id'],
        "uid"       => $row['user_uid'],
        "username"  => $row['username'] ?: "user",
        "text"      => $row['text'] ?: "",
        "createdAt" => isset($row['created_at']) ? (int)$row['created_at'] : 0
    ];
}

$data = [
    "postId"       => $postId,
    "total"        => $totalCount,   // total comments in DB
    "returned"     => count($comments), // how many we are sending now
    "comments"     => $comments
];

json_response(true, "Comments loaded", $data);
