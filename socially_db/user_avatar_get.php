<?php
// user_avatar_get.php
include_once __DIR__ . "/config.php";
include_once __DIR__ . "/response.php";
include_once __DIR__ . "/utils.php";

require_method('GET');
require_fields(['uid'], 'GET');

$uid        = get_field('uid');
$fallback   = get_field('fallbackUid', '');  // optional

$uidEsc      = db_escape($conn, $uid);
$fallbackEsc = db_escape($conn, $fallback);

/**
 * Try to load avatar for a single uid.
 * Returns [found:boolean, avatar:string|null, type:string|null]
 */
function load_avatar_for_uid(mysqli $conn, string $uidEsc): array {
    if ($uidEsc === '') {
        return [false, null, null];
    }

    $sql = "
    SELECT 
        profile_picture_url,
        photo
    FROM users
    WHERE firebase_uid = '$uidEsc'
    LIMIT 1
    ";
    $res = $conn->query($sql);
    if (!$res || $res->num_rows === 0) {
        return [false, null, null];
    }

    $row = $res->fetch_assoc();

    $url  = trim((string)($row['profile_picture_url'] ?? ''));
    $photo = trim((string)($row['photo'] ?? ''));

    $avatar = null;
    $type   = null;

    if ($url !== '') {
        // Prefer URL if present
        $avatar = $url;
        $type   = 'url';
    } elseif ($photo !== '') {
        // Fallback to base64/photo
        $avatar = $photo;
        // We treat this as base64 – your client will decode it
        $type   = 'base64';
    }

    if ($avatar === null) {
        return [false, null, null];
    }

    return [true, $avatar, $type];
}

// 1) Try primary uid
[$foundPrimary, $primaryAvatar, $primaryType] = load_avatar_for_uid($conn, $uidEsc);

// 2) If not found and fallbackUid is given, try fallback
$usedUid    = $uid;
$usedType   = $primaryType;
$avatarStr  = $primaryAvatar;
$fromFallback = false;

if (!$foundPrimary && $fallback !== '') {
    [$foundFallback, $fallbackAvatar, $fallbackType] = load_avatar_for_uid($conn, $fallbackEsc);
    if ($foundFallback) {
        $avatarStr   = $fallbackAvatar;
        $usedType    = $fallbackType;
        $usedUid     = $fallback;
        $fromFallback = true;
    }
}

// 3) Build response

if ($avatarStr === null) {
    // No avatar found for primary nor fallback
    json_response(false, "No avatar found for given uid(s)", [
        "primaryUid" => $uid,
        "fallbackUid" => $fallback !== '' ? $fallback : null,
        "usedUid"     => null,
        "avatar"      => null,
        "type"        => null
    ], 404);
}

$data = [
    "primaryUid"   => $uid,
    "fallbackUid"  => $fallback !== '' ? $fallback : null,
    "usedUid"      => $usedUid,          // uid whose avatar we actually returned
    "avatar"       => $avatarStr,        // URL or base64 string
    "type"         => $usedType,         // "url" or "base64"
    "fromFallback" => $fromFallback      // true if we had to use fallbackUid
];

$jsonMsg = $fromFallback ? "Avatar loaded from fallback user" : "Avatar loaded";

json_response(true, $jsonMsg, $data);
