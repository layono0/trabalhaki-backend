package com.trabalhaki.backend.dto.match;

import java.time.LocalDateTime;

public record MatchResponse(
        Long id,
        Long jobId,
        String jobTitle,
        String companyName,
        String companyLogoUrl,
        Long candidateId,
        String candidateName,
        String candidatePhotoUrl,
        Integer compatibilityScore,
        String compatibilityExplanationJson,
        LocalDateTime createdAt
) {}
