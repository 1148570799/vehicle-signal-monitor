package com.wangyunfei.cockpitdiag.model;

public enum RiskLevel {
    INFO,
    WARNING,
    CRITICAL;

    public static RiskLevel max(RiskLevel left, RiskLevel right) {
        return left.ordinal() >= right.ordinal() ? left : right;
    }
}
