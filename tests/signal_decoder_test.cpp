#include <chrono>

#include <gtest/gtest.h>

#include "vsm/can_frame.hpp"
#include "vsm/signal_decoder.hpp"

namespace vsm {
namespace {

TEST(SignalDecoderTest, DecodesVehicleStatusFrame) {
    CanFrame frame;
    frame.id = kVehicleStatusCanId;
    frame.dlc = 4;
    frame.data = {0x39, 0x30, 130, 3, 0, 0, 0, 0};

    const auto result = SignalDecoder{}.decode(frame);

    ASSERT_EQ(result.status, DecodeStatus::Decoded);
    ASSERT_TRUE(result.signals.has_value());
    EXPECT_DOUBLE_EQ(result.signals->speed_kph, 123.45);
    EXPECT_EQ(result.signals->coolant_temperature_c, 90);
    EXPECT_EQ(result.signals->raw_gear, 3);
}

TEST(SignalDecoderTest, IgnoresUnknownCanIdentifier) {
    CanFrame frame;
    frame.id = 0x321;
    frame.dlc = 8;

    const auto result = SignalDecoder{}.decode(frame);

    EXPECT_EQ(result.status, DecodeStatus::Ignored);
    EXPECT_FALSE(result.signals.has_value());
}

TEST(SignalDecoderTest, RejectsShortVehicleStatusFrame) {
    CanFrame frame;
    frame.id = kVehicleStatusCanId;
    frame.dlc = 3;

    const auto result = SignalDecoder{}.decode(frame);

    EXPECT_EQ(result.status, DecodeStatus::Malformed);
    EXPECT_FALSE(result.signals.has_value());
    EXPECT_FALSE(result.error.empty());
}

}  // namespace
}  // namespace vsm
