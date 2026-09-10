package com.trabalhaki.backend.dto.company;

import com.trabalhaki.backend.domain.enums.CompanyRole;

import java.time.LocalDateTime;

public record CompanyMemberResponse(
        Long userId,
        String email,
        CompanyRole companyRole,
        boolean active,
        LocalDateTime createdAt
) {}
