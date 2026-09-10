#include "vsm/signal_decoder.hpp"

namespace vsm {

DecodeResult SignalDecoder::decode(const CanFrame& frame) const {
    if (frame.id != kVehicleStatusCanId) {
        return {DecodeStatus::Ignored, std::nullopt, {}};
    }

    if (frame.dlc < 4) {
        return {
            DecodeStatus::Malformed,
            std::nullopt,
            "CAN 0x100 requires at least 4 data bytes",
        };
    }

    const auto speed_raw = static_cast<std::uint16_t>(frame.data[0]) |
                           (static_cast<std::uint16_t>(frame.data[1]) << 8U);

    VehicleSignals signals;
    signals.speed_kph = static_cast<double>(speed_raw) / 100.0;
    signals.coolant_temperature_c = static_cast<int>(frame.data[2]) - 40;
    signals.raw_gear = frame.data[3];
    signals.received_at = frame.received_at;

    return {DecodeStatus::Decoded, signals, {}};
}

}  // namespace vsm
