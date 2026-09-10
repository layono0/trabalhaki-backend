package com.trabalhaki.backend.dto.process;

import com.trabalhaki.backend.domain.enums.ProcessStatus;
import jakarta.validation.constraints.NotNull;

public record AdvanceProcessRequest(
        @NotNull ProcessStatus newStatus,
        String note
) {}
