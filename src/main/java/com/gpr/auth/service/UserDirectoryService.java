package com.gpr.auth.service;

import com.gpr.auth.entity.User;
import com.gpr.auth.entity.UserInfo;
import com.gpr.auth.repository.UserInfoRepository;
import com.gpr.auth.repository.UserRepository;
import com.gpr.common.dto.UserSummaryDto;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only canonical identity projections for cross-service (app) lookups by id. */
@Service
@RequiredArgsConstructor
public class UserDirectoryService {

    private final UserRepository userRepository;
    private final UserInfoRepository userInfoRepository;

    @Transactional(readOnly = true)
    public List<UserSummaryDto> getSummaries(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        Map<Long, UserInfo> infoByUser = userInfoRepository.findAll().stream()
                .filter(i -> i.getUser() != null)
                .collect(Collectors.toMap(i -> i.getUser().getId(), Function.identity(), (a, b) -> a));
        return userRepository.findAllById(ids).stream()
                .map(user -> toSummary(user, infoByUser.get(user.getId())))
                .toList();
    }

    public UserSummaryDto toSummary(User user, UserInfo info) {
        return UserSummaryDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .phone(user.getPhone())
                .firstName(info != null ? info.getFirstName() : null)
                .lastName(info != null ? info.getLastName() : null)
                .middleName(info != null ? info.getMiddleName() : null)
                .birthday(info != null ? info.getBirthday() : null)
                .address(info != null ? info.getAddress() : null)
                .gender(info != null ? info.getGender() : null)
                .build();
    }
}
