package com.wangyunfei.cockpitdiag.model;

import java.time.Instant;
import java.util.List;

public record DiagnosisReport(
        String diagnosisId,
        Instant generatedAt,
        String summary,
        RiskLevel risk,
        double confidence,
        boolean controlActionApprovalRequired,
        List<Evidence> evidence,
        List<String> recommendedActions,
        List<TestSuggestion> testPlan,
        List<String> toolsUsed,
        String safetyNote) {
}
