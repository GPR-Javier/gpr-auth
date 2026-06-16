package com.gpr.auth.controller;

import com.gpr.auth.dto.IdentityCreateRequest;
import com.gpr.auth.service.AuthService;
import com.gpr.auth.service.UserDirectoryService;
import com.gpr.common.dto.UserSummaryDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal cross-service identity API. Apps (e.g. wos-hr) resolve user display data by id, and
 * provision identities for new employees, without owning the {@code users} table.
 *
 * <p>GET  /users/summaries?ids=1,2,3 — display projections by id<br>
 * POST /users — create-or-find identity (returns the projection incl. the new userId)
 */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserDirectoryController {

    private final UserDirectoryService userDirectoryService;
    private final AuthService authService;

    @GetMapping("/summaries")
    public ResponseEntity<List<UserSummaryDto>> getSummaries(@RequestParam List<Long> ids) {
        return ResponseEntity.ok(userDirectoryService.getSummaries(ids));
    }

    /** Resolve a single identity by login email → projection (404 when no such identity). */
    @GetMapping("/by-email")
    public ResponseEntity<UserSummaryDto> getByEmail(@RequestParam String email) {
        UserSummaryDto summary = userDirectoryService.getByEmail(email);
        return summary == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(summary);
    }

    @PostMapping
    public ResponseEntity<UserSummaryDto> createIdentity(
            @Valid @RequestBody IdentityCreateRequest request,
            HttpServletRequest httpRequest
    ) {
        String appId = httpRequest.getHeader("X-App-Id");
        String clientId = (appId == null || appId.isBlank()) ? "workos" : appId.trim();
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.createIdentity(request, clientId));
    }
}
