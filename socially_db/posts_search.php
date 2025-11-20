<?php
// posts_search.php
include_once __DIR__ . "/config.php";
include_once __DIR__ . "/response.php";
include_once __DIR__ . "/utils.php";

require_method('GET');

// Optional search query
$q      = get_field('q', '');      // e.g. ?q=cat
$limit  = (int)get_field('limit', '100'); // default 100 posts
$offset = (int)get_field('offset', '0');  // for paging

if ($limit <= 0 || $limit > 500) {
    $limit = 100;
}
if ($offset < 0) {
    $offset = 0;
}

// Base SQL: all posts, joined with users
$sql = "
    SELECT 
        p.firebase_post_id,
        p.user_uid,
        p.caption,
        p.image_base64,
        p.image_url,
        p.created_at,
        p.like_count,
        p.comment_count,
        u.username
    FROM posts p
    JOIN users u
      ON p.user_uid = u.firebase_uid
";

// If query present, filter by username or caption
if (trim($q) !== '') {
    $qEsc = db_escape($conn, '%' . $q . '%');
    $sql .= "
        WHERE 
            u.username LIKE $qEsc
            OR p.caption LIKE $qEsc
    ";
}

// Order newest first + pagination
$sql .= "
    ORDER BY p.created_at DESC
    LIMIT $limit OFFSET $offset
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
        "username"     => $row['username'] ?: "user",
        "caption"      => $row['caption'] ?: "",
        "imageUrl"     => $row['image_url'] ?: "",
        "imageBase64"  => $row['image_base64'] ?: "",
        "createdAt"    => isset($row['created_at']) ? (int)$row['created_at'] : 0,
        "likeCount"    => isset($row['like_count']) ? (int)$row['like_count'] : 0,
        "commentCount" => isset($row['comment_count']) ? (int)$row['comment_count'] : 0
    ];
}

json_response(true, "Posts search/explore loaded", $posts);
