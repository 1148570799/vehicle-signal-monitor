package com.wangyunfei.cockpitdiag.tool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.wangyunfei.cockpitdiag.model.Evidence;
import com.wangyunfei.cockpitdiag.model.FaultCode;
import com.wangyunfei.cockpitdiag.model.Finding;
import com.wangyunfei.cockpitdiag.model.ParsedAlert;

@Component
public class LogAnalysisTool {

    public List<Finding> analyze(List<ParsedAlert> history) {
        Map<String, ParsedAlert> latestByType = new LinkedHashMap<>();
        history.forEach(alert -> latestByType.put(alert.type(), alert));

        return latestByType.values().stream()
                .filter(ParsedAlert::active)
                .map(this::toFinding)
                .toList();
    }

    private Finding toFinding(ParsedAlert alert) {
        FaultCode code;
        try {
            code = FaultCode.valueOf(alert.type());
        } catch (IllegalArgumentException ignored) {
            code = FaultCode.ILLEGAL_VALUE;
        }
        return new Finding(
                code,
                alert.severity(),
                new Evidence(
                        "monitor-log-tool",
                        alert.type(),
                        alert.message(),
                        "log://vehicle-signal-monitor"));
    }
}
