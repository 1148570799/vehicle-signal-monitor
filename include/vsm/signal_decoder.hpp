#pragma once

#include <optional>
#include <string>

#include "vsm/can_frame.hpp"
#include "vsm/vehicle_signals.hpp"

namespace vsm {

constexpr std::uint32_t kVehicleStatusCanId = 0x100;

enum class DecodeStatus {
    Decoded,
    Ignored,
    Malformed,
};

struct DecodeResult {
    DecodeStatus status{DecodeStatus::Ignored};
    std::optional<VehicleSignals> signals;
    std::string error;
};

class SignalDecoder {
public:
    DecodeResult decode(const CanFrame& frame) const;
};

}  // namespace vsm
