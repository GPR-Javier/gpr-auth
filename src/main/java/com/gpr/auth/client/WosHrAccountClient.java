package com.gpr.auth.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Calls wos-hr's internal account-maintenance bridge. Used to wipe WorkOS-side data when an identity
 * is hard-deleted, so it isn't left orphaned in the {@code workos} DB. Best-effort: failures are
 * logged and swallowed — the caller re-provisions the identity under a new userId regardless.
 */
@Slf4j
@Component
public class WosHrAccountClient {

    private final RestClient http;
    private final String internalToken;

    public WosHrAccountClient(
            @Value("${wos-hr.base-url:http://localhost:8083/api/hr}") String baseUrl,
            @Value("${internal.service-token:wos-internal-dev-token}") String internalToken) {
        this.http = RestClient.builder().baseUrl(baseUrl).build();
        this.internalToken = internalToken;
    }

    /** Erase all WorkOS data keyed by this identity. Never throws — cleanup is best-effort. */
    public void purgeUserData(Long userId) {
        try {
            http.delete()
                    .uri("/internal/accounts/{userId}/data", userId)
                    .header("X-Internal-Token", internalToken)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Requested WorkOS data purge for identity {}", userId);
        } catch (Exception e) {
            log.warn("WorkOS data purge for identity {} failed (left orphaned): {}", userId, e.getMessage());
        }
    }
}
