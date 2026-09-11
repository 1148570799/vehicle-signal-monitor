package com.wangyunfei.cockpitdiag.service;

import org.springframework.stereotype.Service;

import com.wangyunfei.cockpitdiag.model.IngestionResult;
import com.wangyunfei.cockpitdiag.store.DiagnosticDataStore;

@Service
public class MonitorLogIngestionService {
    private final MonitorLogParser parser;
    private final DiagnosticDataStore store;

    public MonitorLogIngestionService(MonitorLogParser parser, DiagnosticDataStore store) {
        this.parser = parser;
        this.store = store;
    }

    public IngestionResult ingest(String rawLog) {
        MonitorLogParser.ParseResult result = parser.parse(rawLog);
        result.latestSignal().ifPresent(store::updateSignal);
        store.addAlerts(result.alerts());
        return new IngestionResult(
                result.latestSignal().isPresent(),
                result.alerts().size(),
                result.latestSignal().orElse(null));
    }
}
