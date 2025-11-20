<?php
include_once __DIR__ . "/config.php";
include_once __DIR__ . "/response.php";
include_once __DIR__ . "/utils.php";

require_method('GET');

// You can fetch by uid OR email
$uid   = get_field('uid', '');
$email = get_field('email', '');

if ($uid === '' && $email === '') {
    json_response(false, "Provide uid or email", null, 400);
}

if ($uid !== '') {
    $uidEsc = db_escape($conn, $uid);
    $sql = "SELECT * FROM users WHERE firebase_uid = '$uidEsc' LIMIT 1";
} else {
    $emailEsc = db_escape($conn, $email);
    $sql = "SELECT * FROM users WHERE email = '$emailEsc' LIMIT 1";
}

$res = $conn->query($sql);

if (!$res || $res->num_rows === 0) {
    json_response(false, "User not found", null, 404);
}

$user = $res->fetch_assoc();

$userData = [
    "id"              => (int)$user['id'],
    "uid"             => $user['firebase_uid'],
    "firebase_uid"    => $user['firebase_uid'],
    "email"           => $user['email'],
    "username"        => $user['username'],
    "firstName"       => $user['first_name'] ?? "",
    "lastName"        => $user['last_name'] ?? "",
    "fullName"        => $user['full_name'] ?? "",
    "dob"             => $user['dob'] ?? "",
    "bio"             => $user['bio'] ?? "",
    "website"         => $user['website'] ?? "",
    "phoneNumber"     => $user['phone_number'] ?? "",
    "gender"          => $user['gender'] ?? "",
    "profilePictureUrl" => $user['profile_picture_url'] ?? "",
    "photo"             => $user['photo'] ?? "",
    "fcmToken"        => $user['fcm_token'] ?? "",
    "followersCount"  => (int)($user['followers_count'] ?? 0),
    "followingCount"  => (int)($user['following_count'] ?? 0),
    "postsCount"      => (int)($user['posts_count'] ?? 0),
    "accountPrivate"  => (int)($user['account_private'] ?? 0),
    "profileCompleted"=> (int)($user['profile_completed'] ?? 0),
    "isOnline"        => (int)($user['is_online'] ?? 0),
    "lastSeen"        => isset($user['last_seen']) ? (int)$user['last_seen'] : 0,
    "createdAt"       => isset($user['created_at']) ? (int)$user['created_at'] : 0
];

json_response(true, "User profile fetched", $userData);
