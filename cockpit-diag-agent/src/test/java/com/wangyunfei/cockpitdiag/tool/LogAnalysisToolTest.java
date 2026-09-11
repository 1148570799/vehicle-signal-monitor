package com.wangyunfei.cockpitdiag.tool;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.wangyunfei.cockpitdiag.model.ParsedAlert;
import com.wangyunfei.cockpitdiag.model.RiskLevel;

class LogAnalysisToolTest {
    private final LogAnalysisTool tool = new LogAnalysisTool();

    @Test
    void recoveredEventClearsEarlierActiveEvent() {
        List<ParsedAlert> history = List.of(
                new ParsedAlert("OVER_TEMPERATURE", true, RiskLevel.CRITICAL, "hot", Instant.EPOCH),
                new ParsedAlert("OVER_TEMPERATURE", false, RiskLevel.INFO, "normal", Instant.EPOCH.plusSeconds(1)));

        assertThat(tool.analyze(history)).isEmpty();
    }

    @Test
    void keepsLatestActiveFault() {
        ParsedAlert active = new ParsedAlert(
                "SIGNAL_TIMEOUT", true, RiskLevel.CRITICAL, "timed out", Instant.EPOCH);

        assertThat(tool.analyze(List.of(active)))
                .singleElement()
                .satisfies(finding -> assertThat(finding.evidence().detail()).isEqualTo("timed out"));
    }
}
