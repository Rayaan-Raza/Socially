<?php
include_once __DIR__ . "/config.php";
include_once __DIR__ . "/response.php";
include_once __DIR__ . "/utils.php";

require_method('POST');
require_fields(['email', 'password'], 'POST');

$email    = post_field('email');
$password = post_field('password');

$emailEsc = db_escape($conn, $email);

$sql = "SELECT * FROM users WHERE email = '$emailEsc' LIMIT 1";
$res = $conn->query($sql);

if (!$res || $res->num_rows === 0) {
    json_response(false, "Invalid email or password", null, 401);
}

$user = $res->fetch_assoc();

if (empty($user['password_hash']) || !password_verify($password, $user['password_hash'])) {
    json_response(false, "Invalid email or password", null, 401);
}

// Mark online
$uidEsc = db_escape($conn, $user['firebase_uid']);
$now    = now_ms();
$conn->query("UPDATE users SET is_online = 1, last_seen = $now WHERE firebase_uid = '$uidEsc'");

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
    "isOnline"        => 1,
    "lastSeen"        => $now,
    "createdAt"       => isset($user['created_at']) ? (int)$user['created_at'] : 0
];

json_response(true, "Login successful", $userData);
