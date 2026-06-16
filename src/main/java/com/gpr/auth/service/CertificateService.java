package com.gpr.auth.service;

import com.gpr.auth.dto.CertificateDtos;
import com.gpr.auth.entity.UserCertificate;
import com.gpr.auth.repository.UserCertificateRepository;
import com.gpr.auth.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Self-service CRUD for a user's identity-level certificates. */
@Service
@RequiredArgsConstructor
public class CertificateService {

    private final UserCertificateRepository repo;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<CertificateDtos.Response> list(Long userId) {
        return repo.findByUserIdOrderByIssuedDateDescIdDesc(userId).stream()
                .map(CertificateDtos.Response::from)
                .toList();
    }

    @Transactional
    public CertificateDtos.Response create(Long userId, CertificateDtos.Request body) {
        UserCertificate c = UserCertificate.builder()
                .user(userRepository.getReferenceById(userId))
                .name(body.getName())
                .issuer(body.getIssuer())
                .issuedDate(body.getIssuedDate())
                .expiryDate(body.getExpiryDate())
                .credentialId(body.getCredentialId())
                .credentialUrl(body.getCredentialUrl())
                .build();
        return CertificateDtos.Response.from(repo.save(c));
    }

    @Transactional
    public CertificateDtos.Response update(Long userId, Long id, CertificateDtos.Request body) {
        UserCertificate c = ownedOrThrow(userId, id);
        c.setName(body.getName());
        c.setIssuer(body.getIssuer());
        c.setIssuedDate(body.getIssuedDate());
        c.setExpiryDate(body.getExpiryDate());
        c.setCredentialId(body.getCredentialId());
        c.setCredentialUrl(body.getCredentialUrl());
        return CertificateDtos.Response.from(repo.save(c));
    }

    @Transactional
    public void delete(Long userId, Long id) {
        repo.delete(ownedOrThrow(userId, id));
    }

    private UserCertificate ownedOrThrow(Long userId, Long id) {
        return repo.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Certificate not found"));
    }
}
