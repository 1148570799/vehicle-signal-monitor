package com.wangyunfei.cockpitdiag.api;

import java.time.Clock;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wangyunfei.cockpitdiag.model.DiagnosisReport;
import com.wangyunfei.cockpitdiag.model.DiagnosisRequest;
import com.wangyunfei.cockpitdiag.model.IngestionResult;
import com.wangyunfei.cockpitdiag.model.VehicleSnapshot;
import com.wangyunfei.cockpitdiag.service.DiagnosticAgentService;
import com.wangyunfei.cockpitdiag.service.MonitorLogIngestionService;
import com.wangyunfei.cockpitdiag.store.DiagnosticDataStore;

@RestController
@RequestMapping("/api/v1")
public class DiagnosticController {
    private final DiagnosticDataStore store;
    private final MonitorLogIngestionService ingestionService;
    private final DiagnosticAgentService agentService;
    private final Clock clock;

    public DiagnosticController(
            DiagnosticDataStore store,
            MonitorLogIngestionService ingestionService,
            DiagnosticAgentService agentService,
            Clock clock) {
        this.store = store;
        this.ingestionService = ingestionService;
        this.agentService = agentService;
        this.clock = clock;
    }

    @PostMapping("/signals")
    public VehicleSnapshot ingestSignal(@Valid @RequestBody SignalInput input) {
        VehicleSnapshot snapshot = new VehicleSnapshot(
                input.speedKph(),
                input.coolantTemperatureC(),
                input.gear(),
                input.capturedAt() == null ? clock.instant() : input.capturedAt());
        store.updateSignal(snapshot);
        return snapshot;
    }

    @PostMapping(value = "/monitor-log", consumes = MediaType.TEXT_PLAIN_VALUE)
    public IngestionResult ingestMonitorLog(@RequestBody String rawLog) {
        return ingestionService.ingest(rawLog);
    }

    @PostMapping("/diagnoses")
    public DiagnosisReport diagnose(@Valid @RequestBody DiagnosisRequest request) {
        return agentService.diagnose(request);
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of(
                "service", "cockpit-diag-agent",
                "latestSignalAvailable", store.latestSignal().isPresent(),
                "alertHistorySize", store.alertHistory().size());
    }

    public record SignalInput(
            double speedKph,
            int coolantTemperatureC,
            @NotBlank String gear,
            java.time.Instant capturedAt) {
    }
}
