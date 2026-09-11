package com.wangyunfei.cockpitdiag.model;

import java.time.Instant;

public record VehicleSnapshot(
        double speedKph,
        int coolantTemperatureC,
        String gear,
        Instant capturedAt) {
}
