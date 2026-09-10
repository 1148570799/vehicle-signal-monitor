#pragma once

#include <chrono>
#include <cstdint>
#include <string>

namespace vsm {

enum class Gear : std::uint8_t {
    Park = 0,
    Reverse = 1,
    Neutral = 2,
    Drive = 3,
    Sport = 4,
};

inline std::string gearToString(std::uint8_t raw_gear) {
    switch (static_cast<Gear>(raw_gear)) {
        case Gear::Park:
            return "P";
        case Gear::Reverse:
            return "R";
        case Gear::Neutral:
            return "N";
        case Gear::Drive:
            return "D";
        case Gear::Sport:
            return "S";
    }
    return "UNKNOWN(" + std::to_string(raw_gear) + ")";
}

struct VehicleSignals {
    double speed_kph{0.0};
    int coolant_temperature_c{0};
    std::uint8_t raw_gear{0};
    std::chrono::steady_clock::time_point received_at{};
};

}  // namespace vsm
