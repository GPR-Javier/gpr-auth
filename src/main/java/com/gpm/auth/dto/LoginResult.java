package com.gpm.auth.dto;

import com.gpm.common.dto.AuthResponse;

public record LoginResult(AuthResponse response, String accessToken, String refreshToken) {}
