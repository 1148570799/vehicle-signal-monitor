package com.wangyunfei.cockpitdiag.model;

import java.util.List;

import jakarta.validation.constraints.NotBlank;

public record DiagnosisRequest(
        @NotBlank String question,
        List<String> logLines,
        Boolean includeTestPlan) {

    public DiagnosisRequest {
        logLines = logLines == null ? List.of() : List.copyOf(logLines);
        includeTestPlan = includeTestPlan == null ? Boolean.FALSE : includeTestPlan;
    }
}
