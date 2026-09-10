#include "vsm/socket_can.hpp"

#include <algorithm>
#include <cerrno>
#include <cstring>
#include <stdexcept>
#include <system_error>
#include <utility>

#include <linux/can.h>
#include <linux/can/raw.h>
#include <net/if.h>
#include <poll.h>
#include <sys/ioctl.h>
#include <sys/socket.h>
#include <unistd.h>

namespace vsm {

SocketCan::SocketCan(const std::string& interface_name) {
    file_descriptor_ = ::socket(PF_CAN, SOCK_RAW, CAN_RAW);
    if (file_descriptor_ < 0) {
        throw std::system_error(errno, std::generic_category(), "socket(PF_CAN)");
    }

    ifreq interface_request{};
    if (interface_name.size() >= sizeof(interface_request.ifr_name)) {
        ::close(file_descriptor_);
        file_descriptor_ = -1;
        throw std::invalid_argument("CAN interface name is too long");
    }
    std::strncpy(
        interface_request.ifr_name,
        interface_name.c_str(),
        sizeof(interface_request.ifr_name) - 1);

    if (::ioctl(file_descriptor_, SIOCGIFINDEX, &interface_request) < 0) {
        const int error = errno;
        ::close(file_descriptor_);
        file_descriptor_ = -1;
        throw std::system_error(
            error,
            std::generic_category(),
            "ioctl(SIOCGIFINDEX) for " + interface_name);
    }

    sockaddr_can address{};
    address.can_family = AF_CAN;
    address.can_ifindex = interface_request.ifr_ifindex;
    if (::bind(
            file_descriptor_,
            reinterpret_cast<sockaddr*>(&address),
            sizeof(address)) < 0) {
        const int error = errno;
        ::close(file_descriptor_);
        file_descriptor_ = -1;
        throw std::system_error(
            error,
            std::generic_category(),
            "bind(" + interface_name + ")");
    }
}

SocketCan::~SocketCan() {
    if (file_descriptor_ >= 0) {
        ::close(file_descriptor_);
    }
}

SocketCan::SocketCan(SocketCan&& other) noexcept
    : file_descriptor_(std::exchange(other.file_descriptor_, -1)) {}

SocketCan& SocketCan::operator=(SocketCan&& other) noexcept {
    if (this == &other) {
        return *this;
    }
    if (file_descriptor_ >= 0) {
        ::close(file_descriptor_);
    }
    file_descriptor_ = std::exchange(other.file_descriptor_, -1);
    return *this;
}

bool SocketCan::send(const CanFrame& frame) const {
    if (frame.dlc > CAN_MAX_DLEN) {
        throw std::invalid_argument("classic CAN frame cannot exceed 8 bytes");
    }

    can_frame raw{};
    raw.can_id = frame.id & CAN_SFF_MASK;
    raw.can_dlc = frame.dlc;
    std::copy_n(frame.data.begin(), frame.dlc, raw.data);

    const auto written = ::write(file_descriptor_, &raw, sizeof(raw));
    if (written < 0) {
        throw std::system_error(errno, std::generic_category(), "write(CAN frame)");
    }
    return written == static_cast<ssize_t>(sizeof(raw));
}

std::optional<CanFrame> SocketCan::receiveFor(
    std::chrono::milliseconds timeout) const {
    pollfd descriptor{};
    descriptor.fd = file_descriptor_;
    descriptor.events = POLLIN;

    const int poll_result = ::poll(&descriptor, 1, static_cast<int>(timeout.count()));
    if (poll_result == 0) {
        return std::nullopt;
    }
    if (poll_result < 0) {
        if (errno == EINTR) {
            return std::nullopt;
        }
        throw std::system_error(errno, std::generic_category(), "poll(CAN socket)");
    }

    can_frame raw{};
    const auto bytes_read = ::read(file_descriptor_, &raw, sizeof(raw));
    if (bytes_read < 0) {
        throw std::system_error(errno, std::generic_category(), "read(CAN frame)");
    }
    if (bytes_read != static_cast<ssize_t>(sizeof(raw))) {
        throw std::runtime_error("received an incomplete CAN frame");
    }
    if ((raw.can_id & (CAN_RTR_FLAG | CAN_ERR_FLAG)) != 0U) {
        return std::nullopt;
    }

    CanFrame frame;
    frame.id = raw.can_id & CAN_EFF_MASK;
    frame.dlc = raw.can_dlc;
    std::copy_n(raw.data, frame.dlc, frame.data.begin());
    frame.received_at = std::chrono::steady_clock::now();
    return frame;
}

}  // namespace vsm
