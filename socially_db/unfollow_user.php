<?php
// unfollow_user.php
include_once __DIR__ . "/config.php";
include_once __DIR__ . "/response.php";
include_once __DIR__ . "/utils.php";

require_method('POST');
require_fields(['uid', 'targetUid'], 'POST');

$uid       = post_field('uid');
$targetUid = post_field('targetUid');

if ($uid === $targetUid) {
    json_response(false, "You cannot unfollow yourself", null, 400);
}

$uidEsc       = db_escape($conn, $uid);
$targetUidEsc = db_escape($conn, $targetUid);

// 1) Check both users exist
$userSql = "
    SELECT firebase_uid
    FROM users
    WHERE firebase_uid IN ('$uidEsc', '$targetUidEsc')
";
$userRes = $conn->query($userSql);
if (!$userRes) {
    json_response(false, "User lookup failed: " . $conn->error, null, 500);
}

$foundUid = false;
$foundTarget = false;
while ($row = $userRes->fetch_assoc()) {
    if ($row['firebase_uid'] === $uid) {
        $foundUid = true;
    }
    if ($row['firebase_uid'] === $targetUid) {
        $foundTarget = true;
    }
}

if (!$foundUid || !$foundTarget) {
    json_response(false, "One or both users not found", null, 404);
}

$conn->begin_transaction();

try {
    // 2) Check if there is a follow relationship
    $checkSql = "
        SELECT 1
        FROM followers
        WHERE user_uid = '$targetUidEsc'
          AND follower_uid = '$uidEsc'
        LIMIT 1
    ";
    $checkRes = $conn->query($checkSql);
    if (!$checkRes) {
        throw new Exception("DB error checking follow: " . $conn->error);
    }

    $wasFollowing = ($checkRes->num_rows > 0);

    // 3) Delete from followers
    $delFollowers = "
        DELETE FROM followers
        WHERE user_uid = '$targetUidEsc'
          AND follower_uid = '$uidEsc'
    ";
    if (!$conn->query($delFollowers)) {
        throw new Exception("Failed to delete from followers: " . $conn->error);
    }

    // 4) Delete from following
    $delFollowing = "
        DELETE FROM following
        WHERE user_uid = '$uidEsc'
          AND following_uid = '$targetUidEsc'
    ";
    if (!$conn->query($delFollowing)) {
        throw new Exception("Failed to delete from following: " . $conn->error);
    }

    // 5) Decrement counters only if they were actually following
    if ($wasFollowing) {
        $updFollowersCnt = "
            UPDATE users
            SET followers_count = GREATEST(followers_count - 1, 0)
            WHERE firebase_uid = '$targetUidEsc'
            LIMIT 1
        ";
        if (!$conn->query($updFollowersCnt)) {
            throw new Exception("Failed to decrement followers_count: " . $conn->error);
        }

        $updFollowingCnt = "
            UPDATE users
            SET following_count = GREATEST(following_count - 1, 0)
            WHERE firebase_uid = '$uidEsc'
            LIMIT 1
        ";
        if (!$conn->query($updFollowingCnt)) {
            throw new Exception("Failed to decrement following_count: " . $conn->error);
        }
    }

    // 6) Also remove any pending follow request from uid → target (cancel request)
    $delReqSql = "
        DELETE FROM follow_requests
        WHERE sender_uid = '$uidEsc'
          AND receiver_uid = '$targetUidEsc'
          AND status = 'pending'
    ";
    if (!$conn->query($delReqSql)) {
        throw new Exception("Failed to delete follow request: " . $conn->error);
    }

    $conn->commit();

    json_response(true, "Unfollow operation completed", [
        "uid"         => $uid,
        "targetUid"   => $targetUid,
        "wasFollowing"=> $wasFollowing
    ]);

} catch (Exception $e) {
    $conn->rollback();
    json_response(false, $e->getMessage(), null, 500);
}
