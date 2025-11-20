<?php
include_once __DIR__ . "/config.php";
include_once __DIR__ . "/response.php";
include_once __DIR__ . "/utils.php";

require_method('POST');

// These are exactly what your Android signup collects
require_fields(['email', 'password', 'username', 'firstName', 'lastName', 'dob'], 'POST');

$email    = post_field('email');
$password = post_field('password');
$username = post_field('username');
$first    = post_field('firstName');
$last     = post_field('lastName');
$dob      = post_field('dob');

// Optional extras
$gender      = post_field('gender', '');
$phoneNumber = post_field('phoneNumber', '');
$website     = post_field('website', '');
$bio         = post_field('bio', "Hey there! I'm using Socially");
$profileB64  = post_field('profilePictureBase64', '');

// Escape for SQL
$emailEsc    = db_escape($conn, $email);
$usernameEsc = db_escape($conn, $username);

// Check if email/username already exist
$checkSql = "
    SELECT id FROM users
    WHERE email = '$emailEsc' OR username = '$usernameEsc'
    LIMIT 1
";
$res = $conn->query($checkSql);
if ($res && $res->num_rows > 0) {
    json_response(false, "Email or username already in use", null, 409);
}

// Generate IDs, hash, timestamps
$firebase_uid  = generate_id(32);
$firebaseEsc   = db_escape($conn, $firebase_uid);
$password_hash = password_hash($password, PASSWORD_BCRYPT);
$passEsc       = db_escape($conn, $password_hash);

$firstEsc      = db_escape($conn, $first);
$lastEsc       = db_escape($conn, $last);
$fullName      = "$first $last";
$fullNameEsc   = db_escape($conn, $fullName);
$dobEsc        = db_escape($conn, $dob);
$bioEsc        = db_escape($conn, $bio);
$websiteEsc    = db_escape($conn, $website);
$phoneEsc      = db_escape($conn, $phoneNumber);
$genderEsc     = db_escape($conn, $gender);
$profileEsc    = db_escape($conn, $profileB64);

$created_at    = now_ms();
$last_seen     = $created_at;

// Default counters/flags
$followersCount   = 0;
$followingCount   = 0;
$postsCount       = 0;
$accountPrivate   = 1; // false
$profileCompleted = 1; // true
$isOnline         = 1; // true
$fcmToken         = "";
$fcmTokenEsc      = db_escape($conn, $fcmToken);

// Insert user
$insertSql = "
INSERT INTO users (
    firebase_uid,
    email,
    password_hash,
    username,
    first_name,
    last_name,
    full_name,
    dob,
    bio,
    website,
    phone_number,
    gender,
    profile_picture_url,
    photo,
    fcm_token,
    followers_count,
    following_count,
    posts_count,
    account_private,
    profile_completed,
    is_online,
    last_seen,
    created_at
) VALUES (
    '$firebaseEsc',
    '$emailEsc',
    '$passEsc',
    '$usernameEsc',
    '$firstEsc',
    '$lastEsc',
    '$fullNameEsc',
    '$dobEsc',
    '$bioEsc',
    '$websiteEsc',
    '$phoneEsc',
    '$genderEsc',
    '$profileEsc',
    '$profileEsc',
    '$fcmTokenEsc',
    $followersCount,
    $followingCount,
    $postsCount,
    $accountPrivate,
    $profileCompleted,
    $isOnline,
    $last_seen,
    $created_at
)";
if (!$conn->query($insertSql)) {
    json_response(false, "Failed to create user: " . $conn->error, null, 500);
}

$newId = $conn->insert_id;

// Response object
$userData = [
    "id"              => (int)$newId,
    "uid"             => $firebase_uid,
    "firebase_uid"    => $firebase_uid,
    "email"           => $email,
    "username"        => $username,
    "firstName"       => $first,
    "lastName"        => $last,
    "fullName"        => $fullName,
    "dob"             => $dob,
    "bio"             => $bio,
    "website"         => $website,
    "phoneNumber"     => $phoneNumber,
    "gender"          => $gender,
    "profilePictureUrl" => $profileB64,
    "photo"             => $profileB64,
    "fcmToken"        => $fcmToken,
    "followersCount"  => $followersCount,
    "followingCount"  => $followingCount,
    "postsCount"      => $postsCount,
    "accountPrivate"  => $accountPrivate,
    "profileCompleted"=> $profileCompleted,
    "isOnline"        => $isOnline,
    "lastSeen"        => $last_seen,
    "createdAt"       => $created_at
];

json_response(true, "Signup successful", $userData);
