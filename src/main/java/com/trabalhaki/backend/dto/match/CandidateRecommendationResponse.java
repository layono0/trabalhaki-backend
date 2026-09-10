package com.trabalhaki.backend.dto.match;

import com.trabalhaki.backend.domain.enums.WorkModality;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record CandidateRecommendationResponse(
        Long candidateId,
        String name,
        String photoUrl,
        String desiredRole,
        String area,
        Integer experienceYears,
        String city,
        String state,
        WorkModality preferredModality,
        BigDecimal minimumSalaryExpectation,
        List<String> skills,
        int compatibilityScore,
        Map<String, String> explanation
) {}
