package com.trabalhaki.backend.dto.like;

import com.trabalhaki.backend.dto.job.JobResponse;

import java.time.LocalDateTime;

public record CandidateLikeResponse(
        Long id,
        Long jobId,
        JobResponse job,
        LocalDateTime likedAt
) {}
