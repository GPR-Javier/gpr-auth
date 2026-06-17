package com.gpr.auth.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Calls wos-hr's internal OAuth bridge. wos-hr owns the per-company provider config and the
 * decryptable client secret, so it builds the non-secret authorize config and performs the
 * code→token exchange; gpr-auth never sees the secret.
 */
@Slf4j
@Component
public class WosHrOAuthClient {

    private final RestClient http;
    private final String internalToken;

    public WosHrOAuthClient(
            @Value("${wos-hr.base-url:http://localhost:8083/api/hr}") String baseUrl,
            @Value("${internal.service-token:wos-internal-dev-token}") String internalToken) {
        this.http = RestClient.builder().baseUrl(baseUrl).build();
        this.internalToken = internalToken;
    }

    public record AuthorizeConfig(
            String provider,
            String clientId,
            String scopes,
            String authorizationUri,
            String redirectUri) {}

    public record OAuthProfile(
            String sub, String email, boolean emailVerified, String name, String picture) {}

    record ExchangeRequest(Long companyId, String provider, String code, String redirectUri) {}

    public AuthorizeConfig authorizeConfig(Long companyId, String provider) {
        return http.get()
                .uri(b -> b.path("/internal/oauth/config")
                        .queryParam("companyId", companyId)
                        .queryParam("provider", provider)
                        .build())
                .header("X-Internal-Token", internalToken)
                .retrieve()
                .body(AuthorizeConfig.class);
    }

    public OAuthProfile exchange(Long companyId, String provider, String code, String redirectUri) {
        return http.post()
                .uri("/internal/oauth/exchange")
                .header("X-Internal-Token", internalToken)
                .body(new ExchangeRequest(companyId, provider, code, redirectUri))
                .retrieve()
                .body(OAuthProfile.class);
    }
}
