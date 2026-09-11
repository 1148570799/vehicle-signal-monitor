package com.wangyunfei.cockpitdiag.model;

public record Finding(FaultCode code, RiskLevel risk, Evidence evidence) {
}
