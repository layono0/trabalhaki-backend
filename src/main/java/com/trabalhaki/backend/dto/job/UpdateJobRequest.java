package com.trabalhaki.backend.dto.job;

import com.trabalhaki.backend.domain.enums.JobStatus;
import com.trabalhaki.backend.domain.enums.WorkModality;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record UpdateJobRequest(
        @Size(max = 150)
        String title,

        String description,

        @DecimalMin("0.0")
        BigDecimal minimumSalary,

        @DecimalMin("0.0")
        BigDecimal maximumSalary,

        WorkModality modality,

        @Size(max = 100)
        String city,

        @Size(max = 50)
        String state,

        @Size(max = 50)
        String country,

        Double latitude,
        Double longitude,

        @Size(max = 50)
        String contractType,

        @Size(max = 50)
        String seniorityLevel,

        @Min(0)
        Integer experienceYears,

        JobStatus status,

        List<String> benefits,

        List<JobSkillRequest> skills
) {}
