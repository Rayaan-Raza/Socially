<?php
// Show errors locally so you can debug
error_reporting(E_ALL);
ini_set('display_errors', 1);

header('Content-Type: application/json; charset=utf-8');

// XAMPP MySQL details
$DB_HOST = "fdb1031.runhosting.com";   // from your panel
$DB_USER = "4707261_socially";         // MySQL username
$DB_PASS = "Socially1";    // the password you set for this DB
$DB_NAME = "4707261_socially";         // MySQL database name

$conn = new mysqli($DB_HOST, $DB_USER, $DB_PASS, $DB_NAME);

if ($conn->connect_error) {
    http_response_code(500);
    echo json_encode([
        "success" => false,
        "message" => "Database connection failed: " . $conn->connect_error
    ]);
    exit;
}

// Escape helper
function db_escape($conn, $str) {
    return mysqli_real_escape_string($conn, $str);
}

// Firebase-style timestamp in ms
function now_ms() {
    return (int) (microtime(true) * 1000);
}

// Random ID generator
function generate_id($length = 20) {
    return bin2hex(random_bytes($length / 2));
}
