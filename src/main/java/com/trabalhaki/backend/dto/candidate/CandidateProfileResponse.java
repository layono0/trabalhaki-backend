package com.trabalhaki.backend.dto.candidate;

import com.trabalhaki.backend.domain.enums.WorkModality;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record CandidateProfileResponse(
        Long id,
        Long userId,
        String name,
        String photoUrl,
        String desiredRole,
        String area,
        Integer experienceYears,
        String education,
        String city,
        String state,
        String country,
        BigDecimal minimumSalaryExpectation,
        WorkModality preferredModality,
        String preferredContractType,
        Integer completionPercentage,
        List<String> skills,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
