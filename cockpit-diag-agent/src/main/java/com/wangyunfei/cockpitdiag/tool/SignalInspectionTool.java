package com.wangyunfei.cockpitdiag.tool;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.wangyunfei.cockpitdiag.model.Evidence;
import com.wangyunfei.cockpitdiag.model.FaultCode;
import com.wangyunfei.cockpitdiag.model.Finding;
import com.wangyunfei.cockpitdiag.model.RiskLevel;
import com.wangyunfei.cockpitdiag.model.VehicleSnapshot;

@Component
public class SignalInspectionTool {
    static final int OVER_TEMPERATURE_C = 110;
    static final Duration SIGNAL_TIMEOUT = Duration.ofMillis(1500);
    private static final Set<String> VALID_GEARS = Set.of("P", "R", "N", "D", "S");

    private final Clock clock;

    public SignalInspectionTool(Clock clock) {
        this.clock = clock;
    }

    public List<Finding> inspect(Optional<VehicleSnapshot> snapshot) {
        if (snapshot.isEmpty()) {
            return List.of(finding(
                    FaultCode.NO_SIGNAL_DATA,
                    RiskLevel.WARNING,
                    "当前没有可用于诊断的车辆信号"));
        }

        VehicleSnapshot signal = snapshot.get();
        List<Finding> findings = new ArrayList<>();
        Duration age = Duration.between(signal.capturedAt(), clock.instant());
        if (age.compareTo(SIGNAL_TIMEOUT) > 0) {
            findings.add(finding(
                    FaultCode.SIGNAL_TIMEOUT,
                    RiskLevel.CRITICAL,
                    "最后一帧信号距今 " + age.toMillis() + " ms，超过 1500 ms 阈值"));
        }
        if (signal.coolantTemperatureC() > OVER_TEMPERATURE_C) {
            findings.add(finding(
                    FaultCode.OVER_TEMPERATURE,
                    RiskLevel.CRITICAL,
                    "冷却液温度 " + signal.coolantTemperatureC() + " °C，超过 110 °C 阈值"));
        }
        if (signal.speedKph() < 0 || signal.speedKph() > 260
                || signal.coolantTemperatureC() < -40 || signal.coolantTemperatureC() > 150
                || signal.gear() == null || !VALID_GEARS.contains(signal.gear())) {
            findings.add(finding(
                    FaultCode.ILLEGAL_VALUE,
                    RiskLevel.WARNING,
                    "发现越界信号：speed=" + signal.speedKph()
                            + " km/h, coolant=" + signal.coolantTemperatureC()
                            + " °C, gear=" + signal.gear()));
        }
        return List.copyOf(findings);
    }

    private Finding finding(FaultCode code, RiskLevel risk, String detail) {
        return new Finding(
                code,
                risk,
                new Evidence("vehicle-signal-tool", code.name(), detail, "live://vehicle/latest"));
    }
}
