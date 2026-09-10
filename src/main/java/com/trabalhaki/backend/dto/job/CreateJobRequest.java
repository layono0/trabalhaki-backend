package com.trabalhaki.backend.dto.job;

import com.trabalhaki.backend.domain.enums.WorkModality;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;

public record CreateJobRequest(

        @NotBlank @Size(max = 150)
        String title,

        @NotBlank
        String description,

        @NotNull @DecimalMin("0.0")
        BigDecimal minimumSalary,

        @NotNull @DecimalMin("0.0")
        BigDecimal maximumSalary,

        @NotNull
        WorkModality modality,

        // Location (required for HYBRID and ONSITE, optional for REMOTE)
        @Size(max = 100)
        String city,

        @Size(max = 50)
        String state,

        @Size(max = 50)
        String country,

        Double latitude,
        Double longitude,

        @NotBlank @Size(max = 50)
        String contractType,

        @NotBlank @Size(max = 50)
        String seniorityLevel,

        @NotNull @Min(0)
        Integer experienceYears,

        List<String> benefits,

        // Skills with REQUIRED or PREFERRED type
        List<JobSkillRequest> skills
) {}
