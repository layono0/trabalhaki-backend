package com.trabalhaki.backend.dto.match;

import java.util.Map;

public record CompatibilityResult(
        int score,
        Map<String, String> explanation,
        String explanationJson
) {}
