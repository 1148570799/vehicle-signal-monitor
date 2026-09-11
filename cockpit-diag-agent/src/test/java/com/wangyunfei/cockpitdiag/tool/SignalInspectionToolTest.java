package com.wangyunfei.cockpitdiag.tool;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.wangyunfei.cockpitdiag.model.FaultCode;
import com.wangyunfei.cockpitdiag.model.VehicleSnapshot;

class SignalInspectionToolTest {
    private static final Instant NOW = Instant.parse("2026-09-11T08:00:00Z");
    private final SignalInspectionTool tool = new SignalInspectionTool(Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void reportsNoDataInsteadOfGuessing() {
        assertThat(tool.inspect(Optional.empty()))
                .extracting(finding -> finding.code())
                .containsExactly(FaultCode.NO_SIGNAL_DATA);
    }

    @Test
    void detectsOverTemperature() {
        VehicleSnapshot snapshot = new VehicleSnapshot(60, 120, "D", NOW);

        assertThat(tool.inspect(Optional.of(snapshot)))
                .extracting(finding -> finding.code())
                .containsExactly(FaultCode.OVER_TEMPERATURE);
    }

    @Test
    void detectsTimeout() {
        VehicleSnapshot snapshot = new VehicleSnapshot(0, 90, "P", NOW.minusSeconds(2));

        assertThat(tool.inspect(Optional.of(snapshot)))
                .extracting(finding -> finding.code())
                .contains(FaultCode.SIGNAL_TIMEOUT);
    }

    @Test
    void detectsIllegalGearAndSpeed() {
        VehicleSnapshot snapshot = new VehicleSnapshot(300, 90, "9", NOW);

        assertThat(tool.inspect(Optional.of(snapshot)))
                .extracting(finding -> finding.code())
                .containsExactly(FaultCode.ILLEGAL_VALUE);
    }

    @Test
    void returnsNoFindingForHealthySignal() {
        VehicleSnapshot snapshot = new VehicleSnapshot(80, 90, "D", NOW);

        assertThat(tool.inspect(Optional.of(snapshot))).isEmpty();
    }
}
