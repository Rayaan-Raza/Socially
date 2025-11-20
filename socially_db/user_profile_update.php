<?php
// user_profile_update.php  (Option B - true partial update)
include_once __DIR__ . "/config.php";
include_once __DIR__ . "/response.php";
include_once __DIR__ . "/utils.php";

require_method('POST');
require_fields(['uid'], 'POST');

$uid    = post_field('uid');
$uidEsc = db_escape($conn, $uid);

// Make sure user exists
$checkSql = "SELECT id FROM users WHERE firebase_uid = '$uidEsc' LIMIT 1";
$checkRes = $conn->query($checkSql);
if (!$checkRes) {
    json_response(false, "User check failed: " . $conn->error, null, 500);
}
if ($checkRes->num_rows === 0) {
    json_response(false, "User not found", null, 404);
}

// Build dynamic SET clause like Firebase updateChildren
$setClauses = [];

/**
 * 1) fullName (optional)
 *    - If provided, we also recompute first_name + last_name
 */
if (isset($_POST['fullName'])) {
    $fullName = trim(post_field('fullName', ''));
    $fullNameEsc = db_escape($conn, $fullName);

    // You may want to allow empty fullName to clear it or prevent that; here we allow overwrite
    $parts = preg_split('/\s+/', $fullName, 2);
    $firstName = $parts[0] ?? $fullName;
    $lastName  = $parts[1] ?? "";

    $firstNameEsc = db_escape($conn, $firstName);
    $lastNameEsc  = db_escape($conn, $lastName);

    $setClauses[] = "full_name  = '$fullNameEsc'";
    $setClauses[] = "first_name = '$firstNameEsc'";
    $setClauses[] = "last_name  = '$lastNameEsc'";
}

/**
 * 2) username (optional)
 */
if (isset($_POST['username'])) {
    $username = trim(post_field('username', ''));
    $usernameEsc = db_escape($conn, $username);
    $setClauses[] = "username = '$usernameEsc'";
}

/**
 * 3) Optional text fields: bio, website, email, phoneNumber, gender
 *    - If the key is present in POST, we overwrite the column (even if empty string)
 *    - If the key is NOT present, we leave that column unchanged
 */
$optionalMap = [
    'bio'         => 'bio',
    'website'     => 'website',
    'email'       => 'email',
    'phoneNumber' => 'phone_number',
    'gender'      => 'gender',
];

foreach ($optionalMap as $postKey => $columnName) {
    if (isset($_POST[$postKey])) {
        $val = post_field($postKey, '');
        $valEsc = db_escape($conn, $val);
        $setClauses[] = "$columnName = '$valEsc'";
    }
}

/**
 * 4) profileImageBase64 (optional)
 *    - If present, we store it in `photo` column
 */
if (isset($_POST['profileImageBase64'])) {
    $profileB64 = post_field('profileImageBase64', '');
    $photoEsc   = db_escape($conn, $profileB64);
    $setClauses[] = "photo = '$photoEsc'";
    // Optionally, you could also zero out profile_picture_url or sync it
    // $setClauses[] = "profile_picture_url = ''";
}

// If no updatable fields were provided, return error
if (empty($setClauses)) {
    json_response(false, "No fields to update", null, 400);
}

$setSql = implode(", ", $setClauses);

$updateSql = "
UPDATE users
SET $setSql
WHERE firebase_uid = '$uidEsc'
LIMIT 1
";

if (!$conn->query($updateSql)) {
    json_response(false, "Failed to update profile: " . $conn->error, null, 500);
}

json_response(true, "Profile updated successfully", [
    "uid" => $uid
]);
