package com.wangyunfei.cockpitdiag.model;

import java.time.Instant;

public record ParsedAlert(
        String type,
        boolean active,
        RiskLevel severity,
        String message,
        Instant observedAt) {
}
