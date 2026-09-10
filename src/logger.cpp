#include "vsm/logger.hpp"

#include <chrono>
#include <ctime>
#include <iomanip>
#include <iostream>
#include <mutex>

namespace vsm {
namespace {

std::mutex log_mutex;

const char* levelName(LogLevel level) {
    switch (level) {
        case LogLevel::Info:
            return "INFO";
        case LogLevel::Warning:
            return "WARN";
        case LogLevel::Error:
            return "ERROR";
    }
    return "UNKNOWN";
}

}  // namespace

void Logger::log(LogLevel level, const std::string& message) {
    const auto now = std::chrono::system_clock::now();
    const auto time = std::chrono::system_clock::to_time_t(now);
    const auto milliseconds = std::chrono::duration_cast<std::chrono::milliseconds>(
                                  now.time_since_epoch()) %
                              1000;

    std::tm local_time{};
#ifdef _WIN32
    localtime_s(&local_time, &time);
#else
    localtime_r(&time, &local_time);
#endif

    std::lock_guard<std::mutex> lock(log_mutex);
    std::cout << '[' << std::put_time(&local_time, "%Y-%m-%d %H:%M:%S") << '.'
              << std::setfill('0') << std::setw(3) << milliseconds.count()
              << "][" << levelName(level) << "] " << message << '\n';
}

}  // namespace vsm
