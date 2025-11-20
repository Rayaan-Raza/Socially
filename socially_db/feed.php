<?php
include_once __DIR__ . "/config.php";
include_once __DIR__ . "/response.php";
include_once __DIR__ . "/utils.php";

require_method('GET');

$uid = get_field('uid', '');
if ($uid === '') {
    json_response(false, "uid is required", null, 400);
}

$uidEsc = db_escape($conn, $uid);

// Build list of uids = me + people I follow
$uids = [];
$uids[] = $uid;

$followRes = $conn->query("SELECT following_uid FROM following WHERE user_uid = '$uidEsc'");
if ($followRes) {
    while ($row = $followRes->fetch_assoc()) {
        if (!empty($row['following_uid'])) {
            $uids[] = $row['following_uid'];
        }
    }
}

$uids = array_values(array_unique($uids));

if (empty($uids)) {
    json_response(true, "No users to load feed from", []);
}

// IN list
$uidsEsc = array_map(function($u) use ($conn) {
    return "'" . db_escape($conn, $u) . "'";
}, $uids);
$inList = implode(",", $uidsEsc);

// Get posts for those users
$postSql = "
SELECT 
    firebase_post_id,
    user_uid,
    caption,
    image_base64,
    created_at,
    like_count,
    comment_count
FROM posts
WHERE user_uid IN ($inList)
ORDER BY created_at DESC
";
$postRes = $conn->query($postSql);

$posts = [];
$postIds = [];
$userUidsForPosts = [];

if ($postRes) {
    while ($row = $postRes->fetch_assoc()) {
        $pid = $row['firebase_post_id'];
        $postIds[] = $pid;
        $userUidsForPosts[] = $row['user_uid'];

        $posts[$pid] = [
            "postId"        => $pid,
            "uid"           => $row['user_uid'],
            "username"      => "", // fill later
            "caption"       => $row['caption'] ?: "",
            "imageUrl"      => "", // if you later add URL column
            "imageBase64"   => $row['image_base64'] ?: "",
            "createdAt"     => (int)($row['created_at'] ?? 0),
            "likeCount"     => (int)($row['like_count'] ?? 0),
            "commentCount"  => (int)($row['comment_count'] ?? 0),
            "iLiked"        => false,
            "latestComments"=> [],
            "totalComments" => (int)($row['comment_count'] ?? 0)
        ];
    }
}

if (empty($posts)) {
    json_response(true, "No posts found", []);
}

// Fetch usernames for all post owners
$userUidsForPosts = array_values(array_unique($userUidsForPosts));
$uuEsc = array_map(function($u) use ($conn) {
    return "'" . db_escape($conn, $u) . "'";
}, $userUidsForPosts);
$userIn = implode(",", $uuEsc);

$userSql = "
SELECT firebase_uid, username
FROM users
WHERE firebase_uid IN ($userIn)
";
$userRes = $conn->query($userSql);
$usernames = [];
if ($userRes) {
    while ($row = $userRes->fetch_assoc()) {
        $usernames[$row['firebase_uid']] = $row['username'] ?: "user";
    }
}

// Fill usernames
foreach ($posts as &$p) {
    $owner = $p['uid'];
    $p['username'] = $usernames[$owner] ?? "user";
}
unset($p);

// For each post: iLiked? & latest comments
$postIds = array_values(array_unique($postIds));

foreach ($postIds as $pid) {
    $pidEsc = db_escape($conn, $pid);

    // iLiked?
    $likeRes = $conn->query("
        SELECT COUNT(*) AS c 
        FROM post_likes 
        WHERE post_id = '$pidEsc' AND user_uid = '$uidEsc'
    ");
    $liked = false;
    if ($likeRes) {
        $row = $likeRes->fetch_assoc();
        $liked = ((int)$row['c'] > 0);
    }
    $posts[$pid]['iLiked'] = $liked;

    // latest 2 comments
    $commentRes = $conn->query("
        SELECT comment_id, username, text, created_at
        FROM post_comments
        WHERE post_id = '$pidEsc'
        ORDER BY created_at DESC
        LIMIT 2
    ");
    $latest = [];
    $totalComments = 0;

    if ($commentRes) {
        while ($crow = $commentRes->fetch_assoc()) {
            $latest[] = [
                "commentId" => $crow['comment_id'],
                "username"  => $crow['username'] ?: "user",
                "text"      => $crow['text'] ?: "",
                "createdAt" => (int)($crow['created_at'] ?? 0)
            ];
        }

        // total comments
        $cntRes = $conn->query("
            SELECT COUNT(*) AS c 
            FROM post_comments 
            WHERE post_id = '$pidEsc'
        ");
        if ($cntRes) {
            $cr = $cntRes->fetch_assoc();
            $totalComments = (int)$cr['c'];
        }
    }

    $posts[$pid]['latestComments'] = $latest;
    $posts[$pid]['totalComments']  = $totalComments;
    $posts[$pid]['commentCount']   = $totalComments;
}

// Convert to indexed array
$result = array_values($posts);

// Sort again by createdAt desc just to be safe
usort($result, function($a, $b) {
    return $b['createdAt'] <=> $a['createdAt'];
});

json_response(true, "Feed loaded", $result);
