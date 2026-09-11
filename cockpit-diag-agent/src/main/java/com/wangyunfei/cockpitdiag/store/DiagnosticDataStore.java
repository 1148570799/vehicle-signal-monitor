package com.wangyunfei.cockpitdiag.store;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Component;

import com.wangyunfei.cockpitdiag.model.ParsedAlert;
import com.wangyunfei.cockpitdiag.model.VehicleSnapshot;

@Component
public class DiagnosticDataStore {
    private static final int MAX_ALERTS = 100;

    private final AtomicReference<VehicleSnapshot> latestSignal = new AtomicReference<>();
    private final ConcurrentLinkedDeque<ParsedAlert> alerts = new ConcurrentLinkedDeque<>();

    public void updateSignal(VehicleSnapshot snapshot) {
        latestSignal.set(snapshot);
    }

    public Optional<VehicleSnapshot> latestSignal() {
        return Optional.ofNullable(latestSignal.get());
    }

    public void addAlerts(List<ParsedAlert> newAlerts) {
        newAlerts.forEach(alerts::addLast);
        while (alerts.size() > MAX_ALERTS) {
            alerts.pollFirst();
        }
    }

    public List<ParsedAlert> alertHistory() {
        return new ArrayList<>(alerts);
    }
}
