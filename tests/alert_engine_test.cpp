#include <chrono>

#include <gtest/gtest.h>

#include "vsm/alert_engine.hpp"

namespace vsm {
namespace {

using namespace std::chrono_literals;

VehicleSignals makeSignals(
    std::chrono::steady_clock::time_point time,
    double speed = 60.0,
    int temperature = 90,
    std::uint8_t gear = 3) {
    return {speed, temperature, gear, time};
}

TEST(AlertEngineTest, EmitsOverTemperatureOnlyOnStateTransitions) {
    const auto start = std::chrono::steady_clock::time_point{};
    AlertEngine engine({}, start);

    auto activated = engine.onSignal(makeSignals(start + 100ms, 60.0, 120));
    ASSERT_EQ(activated.size(), 1U);
    EXPECT_EQ(activated[0].type, AlertType::OverTemperature);
    EXPECT_TRUE(activated[0].active);

    EXPECT_TRUE(
        engine.onSignal(makeSignals(start + 200ms, 60.0, 121)).empty());

    auto recovered = engine.onSignal(makeSignals(start + 300ms, 60.0, 100));
    ASSERT_EQ(recovered.size(), 1U);
    EXPECT_EQ(recovered[0].type, AlertType::OverTemperature);
    EXPECT_FALSE(recovered[0].active);
}

TEST(AlertEngineTest, ReportsIllegalSpeedAndGear) {
    const auto start = std::chrono::steady_clock::time_point{};
    AlertEngine engine({}, start);

    auto events = engine.onSignal(makeSignals(start + 10ms, 300.0, 90, 9));

    ASSERT_EQ(events.size(), 1U);
    EXPECT_EQ(events[0].type, AlertType::IllegalValue);
    EXPECT_TRUE(events[0].active);
    EXPECT_NE(events[0].message.find("speed="), std::string::npos);
    EXPECT_NE(events[0].message.find("gear="), std::string::npos);
}

TEST(AlertEngineTest, ReportsIllegalTemperatureWithoutOverTemperatureAlert) {
    const auto start = std::chrono::steady_clock::time_point{};
    AlertEngine engine({}, start);

    auto events = engine.onSignal(makeSignals(start + 10ms, 60.0, 180, 3));

    ASSERT_EQ(events.size(), 1U);
    EXPECT_EQ(events[0].type, AlertType::IllegalValue);
    EXPECT_TRUE(events[0].active);
    EXPECT_NE(events[0].message.find("temperature="), std::string::npos);
}

TEST(AlertEngineTest, ActivatesTimeoutOnceAndRecoversOnNextSignal) {
    const auto start = std::chrono::steady_clock::time_point{};
    AlertThresholds thresholds;
    thresholds.signal_timeout = 500ms;
    AlertEngine engine(thresholds, start);

    EXPECT_TRUE(engine.checkTimeout(start + 499ms).empty());

    auto timeout = engine.checkTimeout(start + 500ms);
    ASSERT_EQ(timeout.size(), 1U);
    EXPECT_EQ(timeout[0].type, AlertType::SignalTimeout);
    EXPECT_TRUE(timeout[0].active);

    EXPECT_TRUE(engine.checkTimeout(start + 900ms).empty());

    auto recovered = engine.onSignal(makeSignals(start + 950ms));
    ASSERT_EQ(recovered.size(), 1U);
    EXPECT_EQ(recovered[0].type, AlertType::SignalTimeout);
    EXPECT_FALSE(recovered[0].active);
}

}  // namespace
}  // namespace vsm
