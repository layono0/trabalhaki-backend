package com.trabalhaki.backend.dto.like;

public record LikeResponse(
        boolean isMatch,
        Long matchId,
        String message
) {}
