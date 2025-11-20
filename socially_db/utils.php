<?php

function post_field($name, $default = "") {
    return isset($_POST[$name]) ? trim($_POST[$name]) : $default;
}

function get_field($name, $default = "") {
    return isset($_GET[$name]) ? trim($_GET[$name]) : $default;
}

function require_fields($fields, $source = 'POST') {
    $missing = [];
    foreach ($fields as $f) {
        $value = ($source === 'POST') ? ($_POST[$f] ?? "") : ($_GET[$f] ?? "");
        if (trim($value) === "") {
            $missing[] = $f;
        }
    }
    if (!empty($missing)) {
        json_response(false, "Missing required fields: " . implode(", ", $missing), null, 400);
    }
}
