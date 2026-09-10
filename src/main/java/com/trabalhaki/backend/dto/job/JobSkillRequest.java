package com.trabalhaki.backend.dto.job;

import com.trabalhaki.backend.domain.enums.SkillType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record JobSkillRequest(
        @NotBlank String skillName,
        @NotNull SkillType skillType
) {}
