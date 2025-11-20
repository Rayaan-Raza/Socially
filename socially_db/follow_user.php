<?php
// follow_user.php
include_once __DIR__ . "/config.php";
include_once __DIR__ . "/response.php";
include_once __DIR__ . "/utils.php";

require_method('POST');
require_fields(['uid', 'targetUid'], 'POST');

$uid       = post_field('uid');        // the user who wants to follow
$targetUid = post_field('targetUid');  // the user being followed

if ($uid === $targetUid) {
    json_response(false, "You cannot follow yourself", null, 400);
}

$uidEsc       = db_escape($conn, $uid);
$targetUidEsc = db_escape($conn, $targetUid);

// 1) Check both users exist and get target's privacy
$userSql = "
    SELECT firebase_uid, account_private
    FROM users
    WHERE firebase_uid IN ('$uidEsc', '$targetUidEsc')
";
$userRes = $conn->query($userSql);
if (!$userRes) {
    json_response(false, "User lookup failed: " . $conn->error, null, 500);
}

$foundUid = false;
$foundTarget = false;
$targetPrivate = 0;

while ($row = $userRes->fetch_assoc()) {
    if ($row['firebase_uid'] === $uid) {
        $foundUid = true;
    }
    if ($row['firebase_uid'] === $targetUid) {
        $foundTarget = true;
        $targetPrivate = isset($row['account_private']) ? (int)$row['account_private'] : 0;
    }
}

if (!$foundUid || !$foundTarget) {
    json_response(false, "One or both users not found", null, 404);
}

// 2) Check if already following
$checkFollowSql = "
    SELECT 1
    FROM followers
    WHERE user_uid = '$targetUidEsc'
      AND follower_uid = '$uidEsc'
    LIMIT 1
";
$cfRes = $conn->query($checkFollowSql);
if (!$cfRes) {
    json_response(false, "DB error checking followers: " . $conn->error, null, 500);
}
if ($cfRes->num_rows > 0) {
    json_response(true, "Already following", [
        "uid"       => $uid,
        "targetUid" => $targetUid,
        "status"    => "already_following"
    ]);
}

// If target is private, create a follow request (pending) instead
if ($targetPrivate === 1) {
    // 3a) Check if a pending request already exists
    $reqCheckSql = "
        SELECT status
        FROM follow_requests
        WHERE sender_uid = '$uidEsc'
          AND receiver_uid = '$targetUidEsc'
        ORDER BY created_at DESC
        LIMIT 1
    ";
    $reqRes = $conn->query($reqCheckSql);
    if ($reqRes && $reqRes->num_rows > 0) {
        $reqRow = $reqRes->fetch_assoc();
        if ($reqRow['status'] === 'pending') {
            json_response(true, "Follow request already pending", [
                "uid"       => $uid,
                "targetUid" => $targetUid,
                "status"    => "request_pending"
            ]);
        }
        // if status is accepted/rejected, we allow a new request or we could block - here we allow new
    }

    // 3b) Insert new request
    $requestId    = generate_id(32);
    $requestIdEsc = db_escape($conn, $requestId);
    $nowMs        = now_ms();

    $insReqSql = "
        INSERT INTO follow_requests (
            request_id,
            sender_uid,
            receiver_uid,
            status,
            created_at
        ) VALUES (
            '$requestIdEsc',
            '$uidEsc',
            '$targetUidEsc',
            'pending',
            $nowMs
        )
    ";
    if (!$conn->query($insReqSql)) {
        json_response(false, "Failed to create follow request: " . $conn->error, null, 500);
    }

    json_response(true, "Follow request sent", [
        "uid"        => $uid,
        "targetUid"  => $targetUid,
        "status"     => "request_sent",
        "requestId"  => $requestId,
        "createdAt"  => $nowMs
    ]);
}

// 4) If target is public, follow immediately
$conn->begin_transaction();

try {
    // Insert into followers (target gains a follower = uid)
    $insFollowers = "
        INSERT INTO followers (user_uid, follower_uid)
        VALUES ('$targetUidEsc', '$uidEsc')
    ";
    if (!$conn->query($insFollowers)) {
        throw new Exception("Failed to insert into followers: " . $conn->error);
    }

    // Insert into following (uid now follows target)
    $insFollowing = "
        INSERT INTO following (user_uid, following_uid)
        VALUES ('$uidEsc', '$targetUidEsc')
    ";
    if (!$conn->query($insFollowing)) {
        throw new Exception("Failed to insert into following: " . $conn->error);
    }

    // Increment counters safely
    $updFollowersCnt = "
        UPDATE users
        SET followers_count = followers_count + 1
        WHERE firebase_uid = '$targetUidEsc'
        LIMIT 1
    ";
    if (!$conn->query($updFollowersCnt)) {
        throw new Exception("Failed to increment followers_count: " . $conn->error);
    }

    $updFollowingCnt = "
        UPDATE users
        SET following_count = following_count + 1
        WHERE firebase_uid = '$uidEsc'
        LIMIT 1
    ";
    if (!$conn->query($updFollowingCnt)) {
        throw new Exception("Failed to increment following_count: " . $conn->error);
    }

    $conn->commit();

    json_response(true, "Now following user", [
        "uid"       => $uid,
        "targetUid" => $targetUid,
        "status"    => "following"
    ]);

} catch (Exception $e) {
    $conn->rollback();
    json_response(false, $e->getMessage(), null, 500);
}
