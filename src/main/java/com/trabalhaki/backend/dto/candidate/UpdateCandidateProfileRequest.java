package com.trabalhaki.backend.dto.candidate;

import com.trabalhaki.backend.domain.enums.WorkModality;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record UpdateCandidateProfileRequest(

        @Size(max = 150)
        String name,

        @Size(max = 500)
        String photoUrl,

        @Size(max = 100)
        String desiredRole,

        @Size(max = 100)
        String area,

        @Min(0) @Max(50)
        Integer experienceYears,

        @Size(max = 150)
        String education,

        @Size(max = 100)
        String city,

        @Size(max = 50)
        String state,

        @Size(max = 50)
        String country,

        @DecimalMin("0.0")
        BigDecimal minimumSalaryExpectation,

        WorkModality preferredModality,

        @Size(max = 50)
        String preferredContractType,

        // List of skill names to attach (existing skills or new)
        List<String> skillNames
) {}
