<?php
include_once __DIR__ . "/config.php";
include_once __DIR__ . "/response.php";
include_once __DIR__ . "/utils.php";

// Only allow POST
require_method('POST');

// We need at least uid + mediaBase64
require_fields(['uid', 'mediaBase64'], 'POST');

$uid         = post_field('uid');
$mediaBase64 = post_field('mediaBase64');

// Escape for SQL
$uidEsc   = db_escape($conn, $uid);
$mediaBEsc = db_escape($conn, $mediaBase64);

// Check that user exists
$userRes = $conn->query("SELECT 1 FROM users WHERE firebase_uid = '$uidEsc' LIMIT 1");
if (!$userRes || $userRes->num_rows === 0) {
    json_response(false, "User not found", null, 404);
}

// Validate mediaBase64 is not empty
if (trim($mediaBase64) === '') {
    json_response(false, "Story must have mediaBase64", null, 400);
}

// Generate story id and timestamps
$storyId    = generate_id(32);
$storyIdEsc = db_escape($conn, $storyId);
$nowMs      = now_ms();
$expiryMs   = $nowMs + 24 * 60 * 60 * 1000; // 24 hours

// Insert into stories (matching your schema)
$sql = "
INSERT INTO stories (
    story_id,
    user_uid,
    media_base64,
    created_at,
    expires_at
) VALUES (
    '$storyIdEsc',
    '$uidEsc',
    '$mediaBEsc',
    $nowMs,
    $expiryMs
)";
if (!$conn->query($sql)) {
    json_response(false, "Failed to upload story: " . $conn->error, null, 500);
}

// Return response
$data = [
    "storyId"      => $storyId,
    "uid"          => $uid,
    "mediaBase64"  => $mediaBase64,
    "createdAt"    => $nowMs,
    "expiresAt"    => $expiryMs
];

json_response(true, "Story uploaded", $data);
