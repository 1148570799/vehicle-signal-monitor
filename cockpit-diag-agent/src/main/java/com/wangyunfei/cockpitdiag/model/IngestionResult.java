package com.wangyunfei.cockpitdiag.model;

public record IngestionResult(
        boolean signalUpdated,
        int alertsImported,
        VehicleSnapshot latestSignal) {
}
