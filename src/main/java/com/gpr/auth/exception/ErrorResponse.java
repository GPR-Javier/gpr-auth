package com.gpr.auth.exception;

import java.time.LocalDateTime;

/** Standard error body for gpr-auth responses ({@code {status, error, message, timestamp}}). */
public record ErrorResponse(int status, String error, String message, LocalDateTime timestamp) {}
