<?php
// screenshot_log.php
include_once __DIR__ . "/config.php";
include_once __DIR__ . "/response.php";
include_once __DIR__ . "/utils.php";

require_method('POST');
require_fields(['chatId', 'takerUid', 'receiverUid'], 'POST');

$chatId      = post_field('chatId');
$takerUid    = post_field('takerUid');
$receiverUid = post_field('receiverUid');
$ts          = post_field('timestamp', ''); // optional

$chatIdEsc    = db_escape($conn, $chatId);
$takerEsc     = db_escape($conn, $takerUid);
$receiverEsc  = db_escape($conn, $receiverUid);

// Basic validation
if (trim($chatId) === '' || trim($takerUid) === '' || trim($receiverUid) === '') {
    json_response(false, "chatId, takerUid and receiverUid are required", null, 400);
}

// Check that taker & receiver exist (optional but good)
$userSql = "
SELECT firebase_uid 
FROM users 
WHERE firebase_uid IN ('$takerEsc', '$receiverEsc')
";
$userRes = $conn->query($userSql);
if (!$userRes) {
    json_response(false, "User check failed: " . $conn->error, null, 500);
}
if ($userRes->num_rows < 2) {
    json_response(false, "One or both users not found", null, 404);
}

// Determine timestamp
if ($ts === '') {
    $timestamp = now_ms();
} else {
    $timestamp = (int)$ts;
}
$tsEsc = db_escape($conn, (string)$timestamp);

// Generate a firebase_screenshot_id (can be any unique string)
$firebaseScreenshotId   = generate_id(32);
$firebaseScreenshotIdEsc = db_escape($conn, $firebaseScreenshotId);

// Insert into your screenshots table
$sql = "
INSERT INTO screenshots (
    firebase_screenshot_id,
    chat_id,
    receiver_uid,
    taker_uid,
    timestamp
) VALUES (
    '$firebaseScreenshotIdEsc',
    '$chatIdEsc',
    '$receiverEsc',
    '$takerEsc',
    $tsEsc
)
";

if (!$conn->query($sql)) {
    json_response(false, "Failed to log screenshot: " . $conn->error, null, 500);
}

$data = [
    "firebaseScreenshotId" => $firebaseScreenshotId,
    "chatId"               => $chatId,
    "takerUid"             => $takerUid,
    "receiverUid"          => $receiverUid,
    "timestamp"            => $timestamp
];

json_response(true, "Screenshot event logged", $data);
