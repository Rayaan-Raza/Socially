<?php

function json_response($success, $message = "", $data = null, $code = 200) {
    http_response_code($code);
    $res = [
        "success" => $success,
        "message" => $message
    ];
    if ($data !== null) {
        $res["data"] = $data;
    }
    echo json_encode($res);
    exit;
}

function require_method($method) {
    if ($_SERVER['REQUEST_METHOD'] !== strtoupper($method)) {
        json_response(false, "Invalid request method", null, 405);
    }
}
