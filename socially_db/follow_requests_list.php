<?php
// follow_requests_list.php
include_once __DIR__ . "/config.php";
include_once __DIR__ . "/response.php";
include_once __DIR__ . "/utils.php";

require_method('GET');
require_fields(['uid'], 'GET');

$uid    = get_field('uid');          // receiver (private account)
$uidEsc = db_escape($conn, $uid);

// Check user exists
$checkSql = "SELECT id FROM users WHERE firebase_uid = '$uidEsc' LIMIT 1";
$checkRes = $conn->query($checkSql);
if (!$checkRes) {
    json_response(false, "User check failed: " . $conn->error, null, 500);
}
if ($checkRes->num_rows === 0) {
    json_response(false, "User not found", null, 404);
}

// Pull pending requests for this user
$reqSql = "
    SELECT request_id, sender_uid, created_at
    FROM follow_requests
    WHERE receiver_uid = '$uidEsc'
      AND status = 'pending'
    ORDER BY created_at DESC
";
$reqRes = $conn->query($reqSql);
if (!$reqRes) {
    json_response(false, "DB error: " . $conn->error, null, 500);
}

$requests = [];
$senderUids = [];

while ($row = $reqRes->fetch_assoc()) {
    $requests[] = [
        "requestId" => $row['request_id'],
        "senderUid" => $row['sender_uid'],
        "createdAt" => (int)$row['created_at']
    ];
    $senderUids[] = $row['sender_uid'];
}

if (empty($requests)) {
    json_response(true, "No pending requests", [
        "uid"      => $uid,
        "count"    => 0,
        "requests" => []
    ]);
}

// Load basic info for senders
$senderUids = array_unique($senderUids);
$escaped = array_map(function($u) use ($conn) {
    return "'" . db_escape($conn, $u) . "'";
}, $senderUids);
$inList = implode(",", $escaped);

$uSql = "
    SELECT firebase_uid, username, full_name, profile_picture_url, photo
    FROM users
    WHERE firebase_uid IN ($inList)
";
$uRes = $conn->query($uSql);
$profiles = [];
if ($uRes) {
    while ($uRow = $uRes->fetch_assoc()) {
        $sUid = $uRow['firebase_uid'];
        $profileUrl  = trim((string)($uRow['profile_picture_url'] ?? ''));
        $photoBase64 = trim((string)($uRow['photo'] ?? ''));

        $avatar = null;
        $avatarType = null;
        if ($profileUrl !== '') {
            $avatar = $profileUrl;
            $avatarType = 'url';
        } elseif ($photoBase64 !== '') {
            $avatar = $photoBase64;
            $avatarType = 'base64';
        }

        $profiles[$sUid] = [
            "uid"        => $sUid,
            "username"   => $uRow['username'] ?? "",
            "fullName"   => $uRow['full_name'] ?? "",
            "avatar"     => $avatar,
            "avatarType" => $avatarType
        ];
    }
}

// Attach profile info to each request
foreach ($requests as &$r) {
    $sUid = $r['senderUid'];
    $r['senderProfile'] = $profiles[$sUid] ?? null;
}
unset($r);

json_response(true, "Pending requests loaded", [
    "uid"      => $uid,
    "count"    => count($requests),
    "requests" => $requests
]);
