package com.trabalhaki.backend.dto.job;

import com.trabalhaki.backend.domain.enums.JobStatus;
import com.trabalhaki.backend.domain.enums.SkillType;
import com.trabalhaki.backend.domain.enums.WorkModality;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record JobResponse(
        Long id,
        Long companyId,
        String companyName,
        String companyLogoUrl,
        String title,
        String description,
        BigDecimal minimumSalary,
        BigDecimal maximumSalary,
        WorkModality modality,
        String city,
        String state,
        String country,
        String contractType,
        String seniorityLevel,
        Integer experienceYears,
        JobStatus status,
        List<String> benefits,
        List<JobSkillResponse> skills,
        Integer version,
        LocalDateTime createdAt
) {
    public record JobSkillResponse(String skillName, SkillType skillType) {}
}
