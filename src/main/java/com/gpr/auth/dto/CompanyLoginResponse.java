package com.gpr.auth.dto;

import java.util.List;

/**
 * Body returned by login / select-company. When {@code requiresCompanySelection} is true the client
 * must pick from {@code companies} and call /auth/select-company; otherwise {@code companyId} is the
 * active tenant and the client proceeds to the WorkOS session.
 */
public record CompanyLoginResponse(
        boolean requiresCompanySelection,
        List<CompanyInfo> companies,
        Long companyId) {}
