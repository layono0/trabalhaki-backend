package com.trabalhaki.backend.dto.match;

import com.trabalhaki.backend.dto.job.JobResponse;

import java.util.Map;

public record JobRecommendationResponse(
        JobResponse job,
        int compatibilityScore,
        Map<String, String> explanation
) {}
