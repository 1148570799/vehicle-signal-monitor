cmake_minimum_required(VERSION 3.20)

project(vehicle_signal_monitor VERSION 1.0.0 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

option(VSM_BUILD_TESTS "Build unit tests" ON)

find_package(Threads REQUIRED)

add_library(vsm_core
    src/alert_engine.cpp
    src/logger.cpp
    src/signal_decoder.cpp
)

target_include_directories(vsm_core PUBLIC include)
target_link_libraries(vsm_core PUBLIC Threads::Threads)

if(MSVC)
    target_compile_options(vsm_core PRIVATE /W4 /permissive-)
else()
    target_compile_options(vsm_core PRIVATE -Wall -Wextra -Wpedantic)
endif()

if(CMAKE_SYSTEM_NAME STREQUAL "Linux")
    add_library(vsm_socketcan src/socket_can.cpp)
    target_include_directories(vsm_socketcan PUBLIC include)
    target_link_libraries(vsm_socketcan PUBLIC vsm_core)

    add_executable(vehicle_signal_monitor src/monitor_main.cpp)
    target_link_libraries(vehicle_signal_monitor PRIVATE vsm_socketcan)

    add_executable(vehicle_can_simulator src/simulator_main.cpp)
    target_link_libraries(vehicle_can_simulator PRIVATE vsm_socketcan)
else()
    message(STATUS "SocketCAN executables are Linux-only; building portable core and tests")
endif()

include(CTest)
if(BUILD_TESTING AND VSM_BUILD_TESTS)
    include(FetchContent)
    set(gtest_force_shared_crt ON CACHE BOOL "" FORCE)
    FetchContent_Declare(
        googletest
        URL https://github.com/google/googletest/archive/refs/tags/v1.17.0.tar.gz
        DOWNLOAD_EXTRACT_TIMESTAMP TRUE
    )
    FetchContent_MakeAvailable(googletest)

    add_executable(vsm_tests
        tests/alert_engine_test.cpp
        tests/blocking_queue_test.cpp
        tests/signal_decoder_test.cpp
    )
    target_link_libraries(vsm_tests PRIVATE vsm_core GTest::gtest_main)

    include(GoogleTest)
    gtest_discover_tests(vsm_tests)
endif()
