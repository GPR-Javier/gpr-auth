package com.gpr.auth.controller;

import com.gpr.auth.service.UserDirectoryService;
import com.gpr.common.dto.UserSummaryDto;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal cross-service user lookup. Consumed by other apps (e.g. wos-hr) to resolve user display
 * data by id — the replacement for the old {@code @ManyToOne User} join, now that apps reference a
 * user only by {@code userId}.
 *
 * <p>GET /users/summaries?ids=1,2,3
 */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserDirectoryController {

    private final UserDirectoryService userDirectoryService;

    @GetMapping("/summaries")
    public ResponseEntity<List<UserSummaryDto>> getSummaries(@RequestParam List<Long> ids) {
        return ResponseEntity.ok(userDirectoryService.getSummaries(ids));
    }
}
