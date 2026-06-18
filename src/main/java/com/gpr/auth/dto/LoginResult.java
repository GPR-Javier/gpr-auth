package com.gpr.auth.dto;

import com.gpr.kernel.dto.AuthResponse;

public record LoginResult(AuthResponse response, String accessToken, String refreshToken) {}
