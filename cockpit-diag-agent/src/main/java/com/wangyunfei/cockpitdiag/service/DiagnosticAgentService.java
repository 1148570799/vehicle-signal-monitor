package com.wangyunfei.cockpitdiag.service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.wangyunfei.cockpitdiag.model.DiagnosisReport;
import com.wangyunfei.cockpitdiag.model.DiagnosisRequest;
import com.wangyunfei.cockpitdiag.model.Evidence;
import com.wangyunfei.cockpitdiag.model.FaultCode;
import com.wangyunfei.cockpitdiag.model.Finding;
import com.wangyunfei.cockpitdiag.model.ParsedAlert;
import com.wangyunfei.cockpitdiag.model.RiskLevel;
import com.wangyunfei.cockpitdiag.model.TestSuggestion;
import com.wangyunfei.cockpitdiag.store.DiagnosticDataStore;
import com.wangyunfei.cockpitdiag.tool.KnowledgeLookupTool;
import com.wangyunfei.cockpitdiag.tool.LogAnalysisTool;
import com.wangyunfei.cockpitdiag.tool.SignalInspectionTool;
import com.wangyunfei.cockpitdiag.tool.TestPlanTool;

@Service
public class DiagnosticAgentService {
    private static final String SAFETY_NOTE =
            "本系统仅提供诊断建议，不发送车辆控制命令；清除故障码、ECU 复位和执行器测试必须由人工确认。";

    private final DiagnosticDataStore store;
    private final MonitorLogParser parser;
    private final SignalInspectionTool signalTool;
    private final LogAnalysisTool logTool;
    private final KnowledgeLookupTool knowledgeTool;
    private final TestPlanTool testPlanTool;
    private final Clock clock;

    public DiagnosticAgentService(
            DiagnosticDataStore store,
            MonitorLogParser parser,
            SignalInspectionTool signalTool,
            LogAnalysisTool logTool,
            KnowledgeLookupTool knowledgeTool,
            TestPlanTool testPlanTool,
            Clock clock) {
        this.store = store;
        this.parser = parser;
        this.signalTool = signalTool;
        this.logTool = logTool;
        this.knowledgeTool = knowledgeTool;
        this.testPlanTool = testPlanTool;
        this.clock = clock;
    }

    public DiagnosisReport diagnose(DiagnosisRequest request) {
        List<String> toolsUsed = new ArrayList<>();
        List<Finding> findings = new ArrayList<>();

        findings.addAll(signalTool.inspect(store.latestSignal()));
        toolsUsed.add("vehicle-signal-tool");

        List<ParsedAlert> requestAlerts = request.logLines().stream()
                .flatMap(line -> parser.parse(line).alerts().stream())
                .toList();
        List<ParsedAlert> allAlerts = new ArrayList<>(store.alertHistory());
        allAlerts.addAll(requestAlerts);
        if (!allAlerts.isEmpty()) {
            List<Finding> logFindings = logTool.analyze(allAlerts);
            if (!logFindings.isEmpty()) {
                findings.removeIf(finding -> finding.code() == FaultCode.NO_SIGNAL_DATA);
            }
            findings.addAll(logFindings);
            toolsUsed.add("monitor-log-tool");
        }

        Set<FaultCode> codes = new LinkedHashSet<>();
        findings.forEach(finding -> codes.add(finding.code()));
        KnowledgeLookupTool.KnowledgeResult knowledge = knowledgeTool.lookup(codes);
        toolsUsed.add("diagnostic-knowledge-tool");

        List<TestSuggestion> testPlan = List.of();
        if (Boolean.TRUE.equals(request.includeTestPlan()) || requestsTestPlan(request.question())) {
            testPlan = testPlanTool.generate(codes);
            toolsUsed.add("test-plan-tool");
        }

        RiskLevel risk = findings.stream()
                .map(Finding::risk)
                .reduce(RiskLevel.INFO, RiskLevel::max);
        List<Evidence> evidence = findings.stream().map(Finding::evidence).distinct().toList();
        boolean hasSignalEvidence = evidence.stream().anyMatch(item ->
                item.source().equals("vehicle-signal-tool") && !item.code().equals(FaultCode.NO_SIGNAL_DATA.name()));
        boolean hasLogEvidence = evidence.stream().anyMatch(item -> item.source().equals("monitor-log-tool"));

        return new DiagnosisReport(
                UUID.randomUUID().toString(),
                clock.instant(),
                summarize(codes),
                risk,
                confidence(codes, hasSignalEvidence, hasLogEvidence),
                risk == RiskLevel.CRITICAL,
                evidence,
                knowledge.actions(),
                testPlan,
                List.copyOf(toolsUsed),
                SAFETY_NOTE);
    }

    private boolean requestsTestPlan(String question) {
        String normalized = question.toLowerCase(Locale.ROOT);
        return normalized.contains("测试") || normalized.contains("test");
    }

    private String summarize(Set<FaultCode> codes) {
        if (codes.contains(FaultCode.NO_SIGNAL_DATA) && codes.size() == 1) {
            return "证据不足：尚未获取车辆信号或有效监控日志，无法判断具体故障。";
        }
        if (codes.isEmpty()) {
            return "当前证据未发现超温、信号超时或非法值。";
        }
        return "发现 " + codes.size() + " 类异常：" + codes.stream()
                .map(Enum::name)
                .reduce((left, right) -> left + ", " + right)
                .orElse("") + "。请先按证据和建议完成复核。";
    }

    private double confidence(Set<FaultCode> codes, boolean hasSignal, boolean hasLog) {
        if (codes.contains(FaultCode.NO_SIGNAL_DATA) && !hasLog) {
            return 0.20;
        }
        if (hasSignal && hasLog) {
            return 0.92;
        }
        return 0.72;
    }
}
