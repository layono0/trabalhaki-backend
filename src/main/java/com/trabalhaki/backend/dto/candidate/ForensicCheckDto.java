package com.trabalhaki.backend.dto.candidate;

public record ForensicCheckDto(
        String code,
        String severity,
        boolean passed,
        int points,
        String detail
) {}
