package com.architect.dto;

import java.util.List;

/**
 * Structured LLM output for PR comments (explanation only).
 */
public record AiRiskExplanation(
        String summary,
        List<String> impact,
        List<String> recommendations,
        String confidenceNote
) {
}
