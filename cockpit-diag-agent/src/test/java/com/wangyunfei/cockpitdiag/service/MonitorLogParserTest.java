package com.wangyunfei.cockpitdiag.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class MonitorLogParserTest {
    private static final Instant NOW = Instant.parse("2026-09-11T08:00:00Z");
    private final MonitorLogParser parser = new MonitorLogParser(Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void parsesLatestSignalAndAlert() {
        String log = """
                [2026-09-11 16:00:00.000][INFO] speed=88.50km/h coolant=120C gear=D
                [2026-09-11 16:00:00.010][ERROR] alert=OVER_TEMPERATURE state=ACTIVE severity=CRITICAL message="coolant too hot"
                """;

        MonitorLogParser.ParseResult result = parser.parse(log);

        assertThat(result.latestSignal()).isPresent();
        assertThat(result.latestSignal().orElseThrow().speedKph()).isEqualTo(88.5);
        assertThat(result.alerts()).hasSize(1);
        assertThat(result.alerts().getFirst().active()).isTrue();
    }

    @Test
    void ignoresUnrelatedLines() {
        MonitorLogParser.ParseResult result = parser.parse("monitor started\nunknown line");

        assertThat(result.latestSignal()).isEmpty();
        assertThat(result.alerts()).isEmpty();
    }
}
