<?php
include_once __DIR__ . "/config.php";
include_once __DIR__ . "/response.php";
include_once __DIR__ . "/utils.php";

require_method('POST');
require_fields(['uid', 'fcmToken'], 'POST');

$uid      = post_field('uid');
$fcmToken = post_field('fcmToken');

$uidEsc  = db_escape($conn, $uid);
$tokenEsc = db_escape($conn, $fcmToken);

// Check user exists
$check = $conn->query("SELECT id FROM users WHERE firebase_uid = '$uidEsc' LIMIT 1");
if (!$check || $check->num_rows === 0) {
    json_response(false, "User not found", null, 404);
}

$sql = "UPDATE users SET fcm_token = '$tokenEsc' WHERE firebase_uid = '$uidEsc'";
if (!$conn->query($sql)) {
    json_response(false, "Failed to update FCM token: " . $conn->error, null, 500);
}

json_response(true, "FCM token updated", null);
