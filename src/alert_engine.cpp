#include "vsm/alert_engine.hpp"

#include <sstream>
#include <stdexcept>
#include <utility>

#include "vsm/vehicle_signals.hpp"

namespace vsm {

AlertEngine::AlertEngine(
    AlertThresholds thresholds,
    std::chrono::steady_clock::time_point started_at)
    : thresholds_(thresholds), started_at_(started_at) {
    if (thresholds_.signal_timeout.count() <= 0) {
        throw std::invalid_argument("signal timeout must be positive");
    }
    if (thresholds_.minimum_temperature_c >=
        thresholds_.maximum_temperature_c) {
        throw std::invalid_argument("temperature range is invalid");
    }
}

std::vector<AlertEvent> AlertEngine::onSignal(
    const VehicleSignals& signals) {
    std::vector<AlertEvent> events;
    last_signal_at_ = signals.received_at;
    has_received_signal_ = true;

    auto timeout_events = updateCondition(
        AlertType::SignalTimeout,
        false,
        Severity::Critical,
        {},
        "vehicle status signal recovered");
    events.insert(
        events.end(), timeout_events.begin(), timeout_events.end());

    const bool invalid_speed =
        signals.speed_kph < 0.0 ||
        signals.speed_kph > thresholds_.maximum_speed_kph;
    const bool invalid_temperature =
        signals.coolant_temperature_c < thresholds_.minimum_temperature_c ||
        signals.coolant_temperature_c > thresholds_.maximum_temperature_c;
    const bool invalid_gear = signals.raw_gear > static_cast<std::uint8_t>(Gear::Sport);
    const bool has_illegal_value =
        invalid_speed || invalid_temperature || invalid_gear;

    std::ostringstream illegal_message;
    illegal_message << "illegal vehicle signal:";
    if (invalid_speed) {
        illegal_message << " speed=" << signals.speed_kph << "km/h";
    }
    if (invalid_temperature) {
        illegal_message << " temperature="
                        << signals.coolant_temperature_c << "C";
    }
    if (invalid_gear) {
        illegal_message << " gear="
                        << static_cast<int>(signals.raw_gear);
    }

    auto illegal_events = updateCondition(
        AlertType::IllegalValue,
        has_illegal_value,
        Severity::Warning,
        illegal_message.str(),
        "vehicle signal values returned to the valid range");
    events.insert(
        events.end(), illegal_events.begin(), illegal_events.end());

    const bool temperature_is_valid = !invalid_temperature;
    const bool over_temperature =
        temperature_is_valid &&
        signals.coolant_temperature_c > thresholds_.over_temperature_c;

    auto temperature_events = updateCondition(
        AlertType::OverTemperature,
        over_temperature,
        Severity::Critical,
        "coolant temperature exceeded threshold: " +
            std::to_string(signals.coolant_temperature_c) + "C > " +
            std::to_string(thresholds_.over_temperature_c) + "C",
        "coolant temperature returned to normal");
    events.insert(
        events.end(), temperature_events.begin(), temperature_events.end());

    return events;
}

std::vector<AlertEvent> AlertEngine::checkTimeout(
    std::chrono::steady_clock::time_point now) {
    const auto reference = has_received_signal_ ? last_signal_at_ : started_at_;
    const auto elapsed =
        std::chrono::duration_cast<std::chrono::milliseconds>(now - reference);
    const bool timed_out = elapsed >= thresholds_.signal_timeout;

    return updateCondition(
        AlertType::SignalTimeout,
        timed_out,
        Severity::Critical,
        "vehicle status signal timed out after " +
            std::to_string(elapsed.count()) + "ms",
        "vehicle status signal recovered");
}

std::vector<AlertEvent> AlertEngine::updateCondition(
    AlertType type,
    bool should_be_active,
    Severity severity,
    std::string active_message,
    std::string recovery_message) {
    const auto index = alertIndex(type);
    if (active_[index] == should_be_active) {
        return {};
    }

    active_[index] = should_be_active;
    if (should_be_active) {
        return {{type, severity, true, std::move(active_message)}};
    }
    return {{type, Severity::Info, false, std::move(recovery_message)}};
}

std::size_t AlertEngine::alertIndex(AlertType type) {
    return static_cast<std::size_t>(type);
}

std::string alertTypeToString(AlertType type) {
    switch (type) {
        case AlertType::OverTemperature:
            return "OVER_TEMPERATURE";
        case AlertType::SignalTimeout:
            return "SIGNAL_TIMEOUT";
        case AlertType::IllegalValue:
            return "ILLEGAL_VALUE";
    }
    return "UNKNOWN_ALERT";
}

std::string severityToString(Severity severity) {
    switch (severity) {
        case Severity::Info:
            return "INFO";
        case Severity::Warning:
            return "WARNING";
        case Severity::Critical:
            return "CRITICAL";
    }
    return "UNKNOWN";
}

}  // namespace vsm
