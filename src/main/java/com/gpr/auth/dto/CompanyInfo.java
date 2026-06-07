package com.gpr.auth.dto;

/** A company the user may act within (returned at login / company-selection). */
public record CompanyInfo(Long id, String name, String slug) {}
