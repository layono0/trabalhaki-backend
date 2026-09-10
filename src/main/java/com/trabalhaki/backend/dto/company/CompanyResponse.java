package com.trabalhaki.backend.dto.company;

import com.trabalhaki.backend.domain.enums.PlanType;
import com.trabalhaki.backend.domain.enums.VerificationStatus;

import java.time.LocalDateTime;

public record CompanyResponse(
        Long id,
        String name,
        String cnpj,
        String description,
        String industry,
        String companySize,
        String website,
        String logoUrl,
        String city,
        String state,
        String country,
        Double latitude,
        Double longitude,
        VerificationStatus verificationStatus,
        PlanType planType,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
