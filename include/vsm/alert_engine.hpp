#pragma once

#include <array>
#include <chrono>
#include <string>
#include <vector>

#include "vsm/vehicle_signals.hpp"

namespace vsm {

enum class AlertType {
    OverTemperature = 0,
    SignalTimeout = 1,
    IllegalValue = 2,
};

enum class Severity {
    Info,
    Warning,
    Critical,
};

struct AlertEvent {
    AlertType type;
    Severity severity;
    bool active;
    std::string message;
};

struct AlertThresholds {
    double maximum_speed_kph{260.0};
    int minimum_temperature_c{-40};
    int maximum_temperature_c{150};
    int over_temperature_c{110};
    std::chrono::milliseconds signal_timeout{1000};
};

class AlertEngine {
public:
    explicit AlertEngine(
        AlertThresholds thresholds = {},
        std::chrono::steady_clock::time_point started_at =
            std::chrono::steady_clock::now());

    std::vector<AlertEvent> onSignal(const VehicleSignals& signals);
    std::vector<AlertEvent> checkTimeout(
        std::chrono::steady_clock::time_point now);

private:
    std::vector<AlertEvent> updateCondition(
        AlertType type,
        bool should_be_active,
        Severity severity,
        std::string active_message,
        std::string recovery_message);

    static std::size_t alertIndex(AlertType type);

    AlertThresholds thresholds_;
    std::chrono::steady_clock::time_point started_at_;
    std::chrono::steady_clock::time_point last_signal_at_{};
    bool has_received_signal_{false};
    std::array<bool, 3> active_{{false, false, false}};
};

std::string alertTypeToString(AlertType type);
std::string severityToString(Severity severity);

}  // namespace vsm
