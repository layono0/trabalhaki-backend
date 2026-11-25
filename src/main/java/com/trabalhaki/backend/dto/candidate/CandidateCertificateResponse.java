package com.trabalhaki.backend.dto.candidate;

import java.time.LocalDateTime;
import java.util.List;

public record CandidateCertificateResponse(
        Long id,
        String courseTitle,
        String institutionName,
        Integer institutionTier,
        List<String> verifiedSkills,
        Integer workloadHours,
        Integer priorityBonus,
        Integer trustScore,
        String credentialCode,
        String verificationSummary,
        LocalDateTime createdAt
) {}
