package com.gpr.auth.service;

import com.gpr.auth.entity.User;
import com.gpr.auth.entity.UserInfo;
import com.gpr.auth.repository.UserInfoRepository;
import com.gpr.auth.repository.UserRepository;
import com.gpr.kernel.dto.UserSummaryDto;
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
        // Fetch only the profile rows we need (indexed user_id IN …) rather than scanning the whole
        // user_info table. The user is a LAZY proxy, but reading its id uses the FK without a query.
        Map<Long, UserInfo> infoByUser = userInfoRepository.findByUser_IdIn(ids).stream()
                .collect(Collectors.toMap(i -> i.getUser().getId(), Function.identity(), (a, b) -> a));
        return userRepository.findAllById(ids).stream()
                .map(user -> toSummary(user, infoByUser.get(user.getId())))
                .toList();
    }

    /** Resolves a single identity by login email — used by apps to map an authenticated email to a userId. */
    @Transactional(readOnly = true)
    public UserSummaryDto getByEmail(String email) {
        return userRepository.findByEmail(email)
                .map(u -> toSummary(u, userInfoRepository.findByUserId(u.getId()).orElse(null)))
                .orElse(null);
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
                .displayName(info != null ? info.getDisplayName() : null)
                .bio(info != null ? info.getBio() : null)
                .profilePhoto(info != null ? info.getProfilePhoto() : null)
                .build();
    }
}
