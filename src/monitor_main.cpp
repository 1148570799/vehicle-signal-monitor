#include <atomic>
#include <chrono>
#include <csignal>
#include <cstdlib>
#include <iomanip>
#include <iostream>
#include <sstream>
#include <stdexcept>
#include <string>
#include <thread>
#include <vector>

#include "vsm/alert_engine.hpp"
#include "vsm/blocking_queue.hpp"
#include "vsm/can_frame.hpp"
#include "vsm/logger.hpp"
#include "vsm/signal_decoder.hpp"
#include "vsm/socket_can.hpp"
#include "vsm/vehicle_signals.hpp"

namespace {

std::atomic<bool> running{true};

void handleSignal(int) {
    running.store(false);
}

struct Options {
    std::string interface_name{"vcan0"};
    int timeout_ms{1000};
    int duration_seconds{0};
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
        } else if (argument == "--timeout-ms" && index + 1 < argc) {
            options.timeout_ms = parsePositiveInt(argv[++index], argument);
        } else if (argument == "--duration" && index + 1 < argc) {
            options.duration_seconds = parsePositiveInt(argv[++index], argument);
        } else if (argument == "--help") {
            std::cout
                << "Usage: vehicle_signal_monitor [--interface vcan0] "
                   "[--timeout-ms 1000] [--duration seconds]\n";
            std::exit(EXIT_SUCCESS);
        } else {
            throw std::invalid_argument("unknown or incomplete option: " + argument);
        }
    }
    return options;
}

vsm::LogLevel toLogLevel(vsm::Severity severity) {
    switch (severity) {
        case vsm::Severity::Info:
            return vsm::LogLevel::Info;
        case vsm::Severity::Warning:
            return vsm::LogLevel::Warning;
        case vsm::Severity::Critical:
            return vsm::LogLevel::Error;
    }
    return vsm::LogLevel::Error;
}

void logEvents(const std::vector<vsm::AlertEvent>& events) {
    for (const auto& event : events) {
        std::ostringstream message;
        message << "alert=" << vsm::alertTypeToString(event.type)
                << " state=" << (event.active ? "ACTIVE" : "RECOVERED")
                << " severity=" << vsm::severityToString(event.severity)
                << " message=\"" << event.message << '"';
        vsm::Logger::log(toLogLevel(event.severity), message.str());
    }
}

}  // namespace

int main(int argc, char* argv[]) {
    try {
        const auto options = parseOptions(argc, argv);
        std::signal(SIGINT, handleSignal);
        std::signal(SIGTERM, handleSignal);

        vsm::Logger::log(
            vsm::LogLevel::Info,
            "starting monitor on interface=" + options.interface_name +
                " timeout_ms=" + std::to_string(options.timeout_ms));

        vsm::SocketCan socket(options.interface_name);
        vsm::BlockingQueue<vsm::CanFrame> frame_queue(256);
        const auto started_at = std::chrono::steady_clock::now();

        std::thread receiver([&socket, &frame_queue] {
            try {
                while (running.load()) {
                    auto frame = socket.receiveFor(std::chrono::milliseconds(200));
                    if (frame && !frame_queue.push(std::move(*frame))) {
                        break;
                    }
                }
            } catch (const std::exception& error) {
                vsm::Logger::log(
                    vsm::LogLevel::Error,
                    std::string("receiver stopped: ") + error.what());
                running.store(false);
            }
            frame_queue.close();
        });

        vsm::SignalDecoder decoder;
        vsm::AlertThresholds thresholds;
        thresholds.signal_timeout = std::chrono::milliseconds(options.timeout_ms);
        vsm::AlertEngine alert_engine(thresholds, started_at);

        while (running.load()) {
            if (options.duration_seconds > 0 &&
                std::chrono::steady_clock::now() - started_at >=
                    std::chrono::seconds(options.duration_seconds)) {
                running.store(false);
                break;
            }

            vsm::CanFrame frame;
            const auto pop_result = frame_queue.popFor(
                frame, std::chrono::milliseconds(100));

            if (pop_result == vsm::QueuePopResult::Closed) {
                break;
            }
            if (pop_result == vsm::QueuePopResult::Item) {
                const auto result = decoder.decode(frame);
                if (result.status == vsm::DecodeStatus::Malformed) {
                    vsm::Logger::log(vsm::LogLevel::Warning, result.error);
                } else if (result.status == vsm::DecodeStatus::Decoded) {
                    const auto& signals = *result.signals;
                    std::ostringstream message;
                    message << std::fixed << std::setprecision(2)
                            << "speed=" << signals.speed_kph << "km/h"
                            << " coolant=" << signals.coolant_temperature_c << "C"
                            << " gear=" << vsm::gearToString(signals.raw_gear);
                    vsm::Logger::log(vsm::LogLevel::Info, message.str());
                    logEvents(alert_engine.onSignal(signals));
                }
            }

            logEvents(alert_engine.checkTimeout(std::chrono::steady_clock::now()));
        }

        running.store(false);
        frame_queue.close();
        receiver.join();
        vsm::Logger::log(vsm::LogLevel::Info, "monitor stopped");
        return EXIT_SUCCESS;
    } catch (const std::exception& error) {
        vsm::Logger::log(vsm::LogLevel::Error, error.what());
        return EXIT_FAILURE;
    }
}
