package com.trabalhaki.backend.dto.job;

import com.trabalhaki.backend.domain.enums.WorkModality;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record JobApplicantResponse(
        Long candidateId,
        String name,
        String photoUrl,
        String desiredRole,
        String area,
        Integer experienceYears,
        String education,
        String city,
        String state,
        String country,
        WorkModality preferredModality,
        String preferredContractType,
        BigDecimal minimumSalaryExpectation,
        Integer completionPercentage,
        List<String> skills,
        int compatibilityScore,
        Map<String, String> explanation,
        LocalDateTime appliedAt,
        List<String> verifiedSkills,
        int priorityBonus
) {}
