package com.wangyunfei.cockpitdiag.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.wangyunfei.cockpitdiag.model.DiagnosisReport;
import com.wangyunfei.cockpitdiag.model.DiagnosisRequest;
import com.wangyunfei.cockpitdiag.model.FaultCode;
import com.wangyunfei.cockpitdiag.store.DiagnosticDataStore;
import com.wangyunfei.cockpitdiag.tool.KnowledgeLookupTool;
import com.wangyunfei.cockpitdiag.tool.LogAnalysisTool;
import com.wangyunfei.cockpitdiag.tool.SignalInspectionTool;
import com.wangyunfei.cockpitdiag.tool.TestPlanTool;

class DiagnosticAgentServiceTest {

    @Test
    void activeLogIsUsefulEvidenceEvenWithoutLiveSignal() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-11T08:00:00Z"), ZoneOffset.UTC);
        MonitorLogParser parser = new MonitorLogParser(clock);
        DiagnosticAgentService service = new DiagnosticAgentService(
                new DiagnosticDataStore(),
                parser,
                new SignalInspectionTool(clock),
                new LogAnalysisTool(),
                new KnowledgeLookupTool(),
                new TestPlanTool(),
                clock);
        DiagnosisRequest request = new DiagnosisRequest(
                "分析日志",
                List.of("alert=SIGNAL_TIMEOUT state=ACTIVE severity=CRITICAL message=\"timeout\""),
                false);

        DiagnosisReport report = service.diagnose(request);

        assertThat(report.evidence())
                .extracting(item -> item.code())
                .containsExactly(FaultCode.SIGNAL_TIMEOUT.name());
        assertThat(report.confidence()).isEqualTo(0.72);
    }
}
