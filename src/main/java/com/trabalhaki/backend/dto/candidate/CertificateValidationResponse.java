package com.trabalhaki.backend.dto.candidate;

import java.util.List;

public record CertificateValidationResponse(
        boolean accepted,
        String status,
        Long certificateId,
        String courseTitle,
        String institutionName,
        Integer institutionTier,
        List<String> verifiedSkills,
        Integer workloadHours,
        int priorityBonus,
        int trustScore,
        int fraudRiskScore,
        String credentialCode,
        List<String> rejectionReasons,
        List<ForensicCheckDto> forensicChecks,
        List<String> whatWasChecked,
        String summary
) {}
