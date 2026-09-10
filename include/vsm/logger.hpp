#pragma once

#include <string>

namespace vsm {

enum class LogLevel {
    Info,
    Warning,
    Error,
};

class Logger {
public:
    static void log(LogLevel level, const std::string& message);
};

}  // namespace vsm
