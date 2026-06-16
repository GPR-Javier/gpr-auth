package com.gpr.auth.service;

import com.gpr.auth.dto.EducationDtos;
import com.gpr.auth.entity.UserEducation;
import com.gpr.auth.repository.UserEducationRepository;
import com.gpr.auth.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Self-service CRUD for a user's identity-level education history. */
@Service
@RequiredArgsConstructor
public class EducationService {

    private final UserEducationRepository repo;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<EducationDtos.Response> list(Long userId) {
        return repo.findByUserIdOrderByStartDateDescIdDesc(userId).stream()
                .map(EducationDtos.Response::from)
                .toList();
    }

    @Transactional
    public EducationDtos.Response create(Long userId, EducationDtos.Request body) {
        UserEducation e = UserEducation.builder()
                .user(userRepository.getReferenceById(userId))
                .school(body.getSchool())
                .degree(body.getDegree())
                .fieldOfStudy(body.getFieldOfStudy())
                .startDate(body.getStartDate())
                .endDate(body.getEndDate())
                .honor(body.getHonor())
                .description(body.getDescription())
                .build();
        return EducationDtos.Response.from(repo.save(e));
    }

    @Transactional
    public EducationDtos.Response update(Long userId, Long id, EducationDtos.Request body) {
        UserEducation e = ownedOrThrow(userId, id);
        e.setSchool(body.getSchool());
        e.setDegree(body.getDegree());
        e.setFieldOfStudy(body.getFieldOfStudy());
        e.setStartDate(body.getStartDate());
        e.setEndDate(body.getEndDate());
        e.setHonor(body.getHonor());
        e.setDescription(body.getDescription());
        return EducationDtos.Response.from(repo.save(e));
    }

    @Transactional
    public void delete(Long userId, Long id) {
        repo.delete(ownedOrThrow(userId, id));
    }

    private UserEducation ownedOrThrow(Long userId, Long id) {
        return repo.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Education entry not found"));
    }
}
