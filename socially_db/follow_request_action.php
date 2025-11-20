<?php
// follow_request_action.php
include_once __DIR__ . "/config.php";
include_once __DIR__ . "/response.php";
include_once __DIR__ . "/utils.php";

require_method('POST');
require_fields(['uid', 'senderUid', 'action'], 'POST');

$uid       = post_field('uid');        // receiver (private account)
$senderUid = post_field('senderUid');  // who requested
$action    = strtolower(post_field('action')); // 'accept' or 'reject'

if (!in_array($action, ['accept', 'reject'], true)) {
    json_response(false, "Invalid action", null, 400);
}

if ($uid === $senderUid) {
    json_response(false, "Invalid request", null, 400);
}

$uidEsc       = db_escape($conn, $uid);
$senderUidEsc = db_escape($conn, $senderUid);

// Check there is a pending request
$reqSql = "
    SELECT request_id
    FROM follow_requests
    WHERE sender_uid = '$senderUidEsc'
      AND receiver_uid = '$uidEsc'
      AND status = 'pending'
    ORDER BY created_at DESC
    LIMIT 1
";
$reqRes = $conn->query($reqSql);
if (!$reqRes) {
    json_response(false, "DB error: " . $conn->error, null, 500);
}
if ($reqRes->num_rows === 0) {
    json_response(false, "No pending request found", null, 404);
}
$reqRow = $reqRes->fetch_assoc();
$requestId = $reqRow['request_id'];
$requestIdEsc = db_escape($conn, $requestId);

if ($action === 'reject') {
    // Just mark as rejected (or delete)
    $updReq = "
        UPDATE follow_requests
        SET status = 'rejected'
        WHERE request_id = '$requestIdEsc'
        LIMIT 1
    ";
    if (!$conn->query($updReq)) {
        json_response(false, "Failed to update request: " . $conn->error, null, 500);
    }

    json_response(true, "Request rejected", [
        "uid"       => $uid,
        "senderUid" => $senderUid,
        "status"    => "rejected"
    ]);
}

// action = accept
$conn->begin_transaction();

try {
    // 1) Update request status
    $updReq = "
        UPDATE follow_requests
        SET status = 'accepted'
        WHERE request_id = '$requestIdEsc'
        LIMIT 1
    ";
    if (!$conn->query($updReq)) {
        throw new Exception("Failed to update request: " . $conn->error);
    }

    // 2) Insert into followers (receiver gains follower = sender)
    $insFollowers = "
        INSERT IGNORE INTO followers (user_uid, follower_uid)
        VALUES ('$uidEsc', '$senderUidEsc')
    ";
    if (!$conn->query($insFollowers)) {
        throw new Exception("Failed to insert into followers: " . $conn->error);
    }

    // 3) Insert into following (sender now follows receiver)
    $insFollowing = "
        INSERT IGNORE INTO following (user_uid, following_uid)
        VALUES ('$senderUidEsc', '$uidEsc')
    ";
    if (!$conn->query($insFollowing)) {
        throw new Exception("Failed to insert into following: " . $conn->error);
    }

    // 4) Increment counters
    $updFollowersCnt = "
        UPDATE users
        SET followers_count = followers_count + 1
        WHERE firebase_uid = '$uidEsc'
        LIMIT 1
    ";
    if (!$conn->query($updFollowersCnt)) {
        throw new Exception("Failed to increment followers_count: " . $conn->error);
    }

    $updFollowingCnt = "
        UPDATE users
        SET following_count = following_count + 1
        WHERE firebase_uid = '$senderUidEsc'
        LIMIT 1
    ";
    if (!$conn->query($updFollowingCnt)) {
        throw new Exception("Failed to increment following_count: " . $conn->error);
    }

    $conn->commit();

    json_response(true, "Request accepted, now following", [
        "uid"       => $uid,
        "senderUid" => $senderUid,
        "status"    => "accepted_following"
    ]);

} catch (Exception $e) {
    $conn->rollback();
    json_response(false, $e->getMessage(), null, 500);
}
