#include <chrono>
#include <cmath>
#include <cstdlib>
#include <iostream>
#include <stdexcept>
#include <string>
#include <thread>

#include "vsm/can_frame.hpp"
#include "vsm/logger.hpp"
#include "vsm/signal_decoder.hpp"
#include "vsm/socket_can.hpp"

namespace {

struct Options {
    std::string interface_name{"vcan0"};
    std::string scenario{"mixed"};
    int count{20};
    int interval_ms{200};
    int timeout_gap_ms{1600};
};

int parsePositiveInt(const std::string& value, const std::string& option_name) {
    const int parsed = std::stoi(value);
    if (parsed <= 0) {
        throw std::invalid_argument(option_name + " must be positive");
    }
    return parsed;
}

Options parseOptions(int argc, char* argv[]) {
    Options options;
    for (int index = 1; index < argc; ++index) {
        const std::string argument = argv[index];
        if (argument == "--interface" && index + 1 < argc) {
            options.interface_name = argv[++index];
        } else if (argument == "--scenario" && index + 1 < argc) {
            options.scenario = argv[++index];
        } else if (argument == "--count" && index + 1 < argc) {
            options.count = parsePositiveInt(argv[++index], argument);
        } else if (argument == "--interval-ms" && index + 1 < argc) {
            options.interval_ms = parsePositiveInt(argv[++index], argument);
        } else if (argument == "--timeout-gap-ms" && index + 1 < argc) {
            options.timeout_gap_ms = parsePositiveInt(argv[++index], argument);
        } else if (argument == "--help") {
            std::cout
                << "Usage: vehicle_can_simulator [--interface vcan0] "
                   "[--scenario normal|overheat|invalid|timeout|mixed] "
                   "[--count 20] [--interval-ms 200] "
                   "[--timeout-gap-ms 1600]\n";
            std::exit(EXIT_SUCCESS);
        } else {
            throw std::invalid_argument("unknown or incomplete option: " + argument);
        }
    }

    if (options.scenario != "normal" && options.scenario != "overheat" &&
        options.scenario != "invalid" && options.scenario != "timeout" &&
        options.scenario != "mixed") {
        throw std::invalid_argument("unsupported scenario: " + options.scenario);
    }
    return options;
}

vsm::CanFrame makeStatusFrame(double speed_kph, int temperature_c, int gear) {
    const auto speed_raw = static_cast<std::uint16_t>(
        std::lround(speed_kph * 100.0));

    vsm::CanFrame frame;
    frame.id = vsm::kVehicleStatusCanId;
    frame.dlc = 4;
    frame.data[0] = static_cast<std::uint8_t>(speed_raw & 0xFFU);
    frame.data[1] = static_cast<std::uint8_t>((speed_raw >> 8U) & 0xFFU);
    frame.data[2] = static_cast<std::uint8_t>(temperature_c + 40);
    frame.data[3] = static_cast<std::uint8_t>(gear);
    return frame;
}

vsm::CanFrame frameForScenario(const std::string& scenario, int sequence) {
    const double normal_speed = 40.0 + static_cast<double>((sequence * 7) % 50);
    const int normal_temperature = 85 + (sequence % 5);

    if (scenario == "overheat") {
        return makeStatusFrame(normal_speed, sequence < 3 ? 95 : 120, 3);
    }
    if (scenario == "invalid") {
        return makeStatusFrame(sequence % 2 == 0 ? 320.0 : normal_speed,
                               normal_temperature,
                               sequence % 2 == 0 ? 3 : 9);
    }
    if (scenario == "mixed") {
        if (sequence >= 4 && sequence <= 6) {
            return makeStatusFrame(normal_speed, 120, 3);
        }
        if (sequence >= 7 && sequence <= 9) {
            return makeStatusFrame(normal_speed, normal_temperature, 9);
        }
    }
    return makeStatusFrame(normal_speed, normal_temperature, 3);
}

}  // namespace

int main(int argc, char* argv[]) {
    try {
        const auto options = parseOptions(argc, argv);
        vsm::SocketCan socket(options.interface_name);
        vsm::Logger::log(
            vsm::LogLevel::Info,
            "starting simulator scenario=" + options.scenario +
                " interface=" + options.interface_name);

        for (int sequence = 0; sequence < options.count; ++sequence) {
            const bool inject_gap =
                (options.scenario == "timeout" && sequence == 5) ||
                (options.scenario == "mixed" && sequence == 10);
            if (inject_gap) {
                vsm::Logger::log(
                    vsm::LogLevel::Warning,
                    "pausing transmission for " +
                        std::to_string(options.timeout_gap_ms) +
                        "ms to trigger timeout alert");
                std::this_thread::sleep_for(
                    std::chrono::milliseconds(options.timeout_gap_ms));
            }

            const auto frame = frameForScenario(options.scenario, sequence);
            if (!socket.send(frame)) {
                throw std::runtime_error("incomplete CAN frame write");
            }
            vsm::Logger::log(
                vsm::LogLevel::Info,
                "sent CAN id=0x100 sequence=" + std::to_string(sequence));
            std::this_thread::sleep_for(
                std::chrono::milliseconds(options.interval_ms));
        }

        vsm::Logger::log(vsm::LogLevel::Info, "simulator completed");
        return EXIT_SUCCESS;
    } catch (const std::exception& error) {
        vsm::Logger::log(vsm::LogLevel::Error, error.what());
        return EXIT_FAILURE;
    }
}
