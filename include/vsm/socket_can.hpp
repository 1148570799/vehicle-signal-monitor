#pragma once

#include <chrono>
#include <optional>
#include <string>

#include "vsm/can_frame.hpp"

namespace vsm {

class SocketCan {
public:
    explicit SocketCan(const std::string& interface_name);
    ~SocketCan();

    SocketCan(const SocketCan&) = delete;
    SocketCan& operator=(const SocketCan&) = delete;

    SocketCan(SocketCan&& other) noexcept;
    SocketCan& operator=(SocketCan&& other) noexcept;

    bool send(const CanFrame& frame) const;
    std::optional<CanFrame> receiveFor(
        std::chrono::milliseconds timeout) const;

private:
    int file_descriptor_{-1};
};

}  // namespace vsm
