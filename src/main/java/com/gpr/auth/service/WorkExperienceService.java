package com.gpr.auth.service;

import com.gpr.auth.dto.WorkExperienceDtos;
import com.gpr.auth.entity.UserWorkExperience;
import com.gpr.auth.repository.UserRepository;
import com.gpr.auth.repository.UserWorkExperienceRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Self-service CRUD for a user's identity-level work-experience history. */
@Service
@RequiredArgsConstructor
public class WorkExperienceService {

    private final UserWorkExperienceRepository repo;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<WorkExperienceDtos.Response> list(Long userId) {
        return repo.findByUserIdOrderByStartDateDescIdDesc(userId).stream()
                .map(WorkExperienceDtos.Response::from)
                .toList();
    }

    @Transactional
    public WorkExperienceDtos.Response create(Long userId, WorkExperienceDtos.Request body) {
        UserWorkExperience w = UserWorkExperience.builder()
                .user(userRepository.getReferenceById(userId))
                .title(body.getTitle())
                .company(body.getCompany())
                .employmentType(body.getEmploymentType())
                .location(body.getLocation())
                .startDate(body.getStartDate())
                .endDate(body.getEndDate())
                .description(body.getDescription())
                .build();
        return WorkExperienceDtos.Response.from(repo.save(w));
    }

    @Transactional
    public WorkExperienceDtos.Response update(Long userId, Long id, WorkExperienceDtos.Request body) {
        UserWorkExperience w = ownedOrThrow(userId, id);
        w.setTitle(body.getTitle());
        w.setCompany(body.getCompany());
        w.setEmploymentType(body.getEmploymentType());
        w.setLocation(body.getLocation());
        w.setStartDate(body.getStartDate());
        w.setEndDate(body.getEndDate());
        w.setDescription(body.getDescription());
        return WorkExperienceDtos.Response.from(repo.save(w));
    }

    @Transactional
    public void delete(Long userId, Long id) {
        repo.delete(ownedOrThrow(userId, id));
    }

    private UserWorkExperience ownedOrThrow(Long userId, Long id) {
        return repo.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Work experience entry not found"));
    }
}
