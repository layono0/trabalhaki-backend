package com.trabalhaki.backend.dto.process;

import com.trabalhaki.backend.domain.enums.ProcessStatus;

import java.time.LocalDateTime;
import java.util.List;

public record SelectionProcessResponse(
        Long id,
        Long matchId,
        ProcessStatus currentStatus,
        List<HistoryEntry> history,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public record HistoryEntry(
            ProcessStatus fromStatus,
            ProcessStatus toStatus,
            String note,
            Long changedByUserId,
            LocalDateTime changedAt
    ) {}
}
