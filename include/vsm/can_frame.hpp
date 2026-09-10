#pragma once

#include <array>
#include <chrono>
#include <cstddef>
#include <cstdint>

namespace vsm {

struct CanFrame {
    std::uint32_t id{0};
    std::uint8_t dlc{0};
    std::array<std::uint8_t, 8> data{};
    std::chrono::steady_clock::time_point received_at{
        std::chrono::steady_clock::now()};
};

}  // namespace vsm
