package com.wangyunfei.cockpitdiag.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.wangyunfei.cockpitdiag.model.ParsedAlert;
import com.wangyunfei.cockpitdiag.model.RiskLevel;
import com.wangyunfei.cockpitdiag.model.VehicleSnapshot;

@Component
public class MonitorLogParser {
    private static final Pattern SIGNAL_PATTERN = Pattern.compile(
            "speed=([+-]?\\d+(?:\\.\\d+)?)km/h\\s+coolant=([+-]?\\d+)C\\s+gear=([^\\s]+)");
    private static final Pattern ALERT_PATTERN = Pattern.compile(
            "alert=([A-Z_]+)\\s+state=(ACTIVE|RECOVERED)\\s+severity=(INFO|WARNING|CRITICAL)\\s+message=\"([^\"]*)\"");

    private final Clock clock;

    public MonitorLogParser(Clock clock) {
        this.clock = clock;
    }

    public ParseResult parse(String rawLog) {
        VehicleSnapshot latestSignal = null;
        List<ParsedAlert> alerts = new ArrayList<>();
        Instant observedAt = clock.instant();

        for (String line : rawLog.lines().toList()) {
            Matcher signalMatcher = SIGNAL_PATTERN.matcher(line);
            if (signalMatcher.find()) {
                latestSignal = new VehicleSnapshot(
                        Double.parseDouble(signalMatcher.group(1)),
                        Integer.parseInt(signalMatcher.group(2)),
                        signalMatcher.group(3),
                        observedAt);
            }

            Matcher alertMatcher = ALERT_PATTERN.matcher(line);
            if (alertMatcher.find()) {
                alerts.add(new ParsedAlert(
                        alertMatcher.group(1),
                        "ACTIVE".equals(alertMatcher.group(2)),
                        RiskLevel.valueOf(alertMatcher.group(3).toUpperCase(Locale.ROOT)),
                        alertMatcher.group(4),
                        observedAt));
            }
        }
        return new ParseResult(Optional.ofNullable(latestSignal), List.copyOf(alerts));
    }

    public record ParseResult(Optional<VehicleSnapshot> latestSignal, List<ParsedAlert> alerts) {
    }
}
