package com.gpr.auth.service;

import com.gpr.auth.entity.User;
import com.gpr.auth.repository.UserRepository;
import com.gpr.common.dto.UserSummaryDto;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only user identity projections for cross-service (app) lookups by id. */
@Service
@RequiredArgsConstructor
public class UserDirectoryService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<UserSummaryDto> getSummaries(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return userRepository.findAllById(ids).stream()
                .map(UserDirectoryService::toSummary)
                .toList();
    }

    private static UserSummaryDto toSummary(User user) {
        return UserSummaryDto.builder()
                .id(user.getId())
                .employeeId(user.getEmployeeId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .build();
    }
}
