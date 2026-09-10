#include <chrono>
#include <thread>

#include <gtest/gtest.h>

#include "vsm/blocking_queue.hpp"

namespace vsm {
namespace {

using namespace std::chrono_literals;

TEST(BlockingQueueTest, PreservesFifoOrderAcrossThreads) {
    BlockingQueue<int> queue(2);
    std::thread producer([&queue] {
        EXPECT_TRUE(queue.push(10));
        EXPECT_TRUE(queue.push(20));
        queue.close();
    });

    int value = 0;
    EXPECT_EQ(queue.popFor(value, 100ms), QueuePopResult::Item);
    EXPECT_EQ(value, 10);
    EXPECT_EQ(queue.popFor(value, 100ms), QueuePopResult::Item);
    EXPECT_EQ(value, 20);
    EXPECT_EQ(queue.popFor(value, 100ms), QueuePopResult::Closed);

    producer.join();
}

TEST(BlockingQueueTest, ReturnsTimeoutWhenNoItemArrives) {
    BlockingQueue<int> queue(1);
    int value = 0;

    EXPECT_EQ(queue.popFor(value, 10ms), QueuePopResult::Timeout);
    queue.close();
}

TEST(BlockingQueueTest, RejectsPushAfterClose) {
    BlockingQueue<int> queue(1);
    queue.close();

    EXPECT_FALSE(queue.push(42));
}

}  // namespace
}  // namespace vsm
