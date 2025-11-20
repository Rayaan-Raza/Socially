<?php
// users_search.php
include_once __DIR__ . "/config.php";
include_once __DIR__ . "/response.php";
include_once __DIR__ . "/utils.php";

require_method('GET');

$q = trim(get_field('q', ''));   // the text the user typed

if ($q === '') {
    // Empty search → empty list is fine
    json_response(true, "Empty query", []);
}

$qEsc = db_escape($conn, $q);

// Prefix pattern (username starts with q OR full_name starts with q)
$likePrefix = $qEsc . '%';

// Adjust the column names here to match your actual `users` table
$sql = "
    SELECT
        firebase_uid,
        username,
        first_name,
        last_name,
        full_name,
        email,
        profile_picture_url,
        photo,
        bio,
        website,
        phone_number,
        gender,
        is_online,
        last_seen,
        followers_count,
        following_count,
        posts_count
    FROM users
    WHERE
        username   LIKE '$likePrefix'
        OR full_name LIKE '$likePrefix'
    ORDER BY username ASC
    LIMIT 50
";

$res = $conn->query($sql);
if (!$res) {
    json_response(false, "DB error: " . $conn->error, null, 500);
}

$users = [];
while ($row = $res->fetch_assoc()) {
    $users[] = [
        "uid"             => $row["firebase_uid"]           ?? "",
        "username"        => $row["username"]               ?? "",
        "firstName"       => $row["first_name"]             ?? "",
        "lastName"        => $row["last_name"]              ?? "",
        "fullName"        => $row["full_name"]              ?? "",
        "email"           => $row["email"]                  ?? "",
        "profilePictureUrl" => $row["profile_picture_url"]  ?? "",
        "photo"           => $row["photo"]                  ?? "",
        "bio"             => $row["bio"]                    ?? "",
        "website"         => $row["website"]                ?? "",
        "phoneNumber"     => $row["phone_number"]           ?? "",
        "gender"          => $row["gender"]                 ?? "",
        "isOnline"        => isset($row["is_online"]) ? (bool)$row["is_online"] : false,
        "lastSeen"        => isset($row["last_seen"]) ? (int)$row["last_seen"] : 0,
        "followersCount"  => isset($row["followers_count"]) ? (int)$row["followers_count"] : 0,
        "followingCount"  => isset($row["following_count"]) ? (int)$row["following_count"] : 0,
        "postsCount"      => isset($row["posts_count"])     ? (int)$row["posts_count"]     : 0,
    ];
}

json_response(true, "Users loaded", $users);
