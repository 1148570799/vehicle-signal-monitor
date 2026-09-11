# Vehicle Signal Monitor

基于 C++17 和 Linux SocketCAN 的车载信号监控与故障告警系统。项目通过
`vcan` 在无 CAN 硬件环境下模拟车速、冷却液温度和挡位信号，使用独立接收
线程和有界阻塞队列解耦报文采集与业务处理，并对超温、信号超时和非法值进行
状态式告警。

## 功能

- 使用 Linux SocketCAN 读写经典 CAN 报文，支持 `vcan0` 虚拟接口。
- 解析 `0x100` 车况报文中的车速、冷却液温度和挡位。
- 接收线程与处理线程通过线程安全的有界生产者-消费者队列通信。
- 检测冷却液超温、报文超时、非法车速/温度/挡位。
- 只在告警激活和恢复时输出事件，避免相同故障重复刷屏。
- 内置 `normal`、`overheat`、`invalid`、`timeout` 和 `mixed` 模拟场景。
- 使用 CMake 构建，GoogleTest 覆盖协议解析、告警状态机和阻塞队列。

## 架构

```mermaid
flowchart LR
    Simulator[CAN 信号模拟器] -->|CAN 0x100| VCAN[Linux vcan0]
    VCAN --> Receiver[SocketCAN 接收线程]
    Receiver --> Queue[有界阻塞队列]
    Queue --> Decoder[信号解析器]
    Decoder --> Engine[告警状态机]
    Engine --> Logger[结构化控制台日志]
```

接收线程只负责 I/O，处理线程负责解析和告警。告警引擎和协议解析器不依赖
SocketCAN，因此可以在没有 CAN 设备的环境中进行单元测试。

## 报文协议

CAN ID：`0x100`，Intel/Little-Endian，至少 4 字节。

| 字节 | 信号 | 编码 | 有效范围 |
|---|---|---|---|
| 0-1 | 车速 | `raw * 0.01 km/h` | 0-260 km/h |
| 2 | 冷却液温度 | `raw - 40 °C` | -40-150 °C |
| 3 | 挡位 | `0=P, 1=R, 2=N, 3=D, 4=S` | 0-4 |

默认超温阈值为 `110 °C`，默认信号超时时间为 `1000 ms`。

## 环境要求

- Ubuntu 22.04/24.04 或其他支持 SocketCAN 的 Linux
- GCC 11+ 或 Clang 14+
- CMake 3.20+
- Git（CMake 首次配置时下载固定版本的 GoogleTest 1.17.0）

安装基础工具：

```bash
sudo apt update
sudo apt install -y build-essential cmake git iproute2 can-utils
```

## 构建与测试

```bash
cmake -S . -B build -DCMAKE_BUILD_TYPE=Release
cmake --build build --parallel
ctest --test-dir build --output-on-failure
```

## 创建 vcan0

```bash
chmod +x scripts/setup_vcan.sh
./scripts/setup_vcan.sh vcan0
```

等价的手工命令：

```bash
sudo modprobe vcan
sudo ip link add dev vcan0 type vcan
sudo ip link set up vcan0
ip -details link show vcan0
```

如果 `vcan0` 已存在，可跳过 `ip link add`。

## 运行演示

终端一启动监控器，运行 10 秒，信号超过 1000 ms 未到达即告警：

```bash
./build/vehicle_signal_monitor \
  --interface vcan0 \
  --timeout-ms 1000 \
  --duration 10
```

终端二启动混合场景模拟器：

```bash
./build/vehicle_can_simulator \
  --interface vcan0 \
  --scenario mixed \
  --count 20 \
  --interval-ms 200 \
  --timeout-gap-ms 1600
```

混合场景依次发送正常、超温、非法挡位和恢复信号，并暂停 1600 ms 触发信号
超时。典型告警日志如下：

```text
[2026-09-10 12:00:00.000][ERROR] alert=OVER_TEMPERATURE state=ACTIVE severity=CRITICAL message="coolant temperature exceeded threshold: 120C > 110C"
[2026-09-10 12:00:00.600][INFO] alert=OVER_TEMPERATURE state=RECOVERED severity=INFO message="coolant temperature returned to normal"
[2026-09-10 12:00:00.800][WARN] alert=ILLEGAL_VALUE state=ACTIVE severity=WARNING message="illegal vehicle signal: gear=9"
[2026-09-10 12:00:02.500][ERROR] alert=SIGNAL_TIMEOUT state=ACTIVE severity=CRITICAL message="vehicle status signal timed out after 1000ms"
[2026-09-10 12:00:03.100][INFO] alert=SIGNAL_TIMEOUT state=RECOVERED severity=INFO message="vehicle status signal recovered"
```

单独验证场景：

```bash
./build/vehicle_can_simulator --scenario normal
./build/vehicle_can_simulator --scenario overheat
./build/vehicle_can_simulator --scenario invalid
./build/vehicle_can_simulator --scenario timeout
```

## 测试范围

- 正常 `0x100` 报文解析及单位换算。
- 未知 CAN ID 忽略、短帧拒绝。
- 超温告警激活、去重和恢复。
- 非法车速、温度、挡位检测。
- 信号超时激活、去重和恢复。
- 有界阻塞队列的 FIFO、超时和关闭行为。

## 设计取舍

1. 使用 `steady_clock` 计算报文超时，避免系统时间调整造成误告警。
2. SocketCAN 接收线程与解析线程解耦，突发报文不会直接阻塞业务处理。
3. 告警按状态变化输出，而不是每帧输出同一故障，便于后续接入仪表或诊断系统。
4. 当前协议是可演示的最小自定义协议；生产项目中应由 DBC/接口控制文档生成或维护信号定义。

## 后续可扩展

- 通过 DBC 文件配置 CAN ID、位宽、缩放和偏移。
- 增加 CAN FD、UDS 诊断和故障码持久化。
- 接入 spdlog、Prometheus 或可视化仪表盘。
- 增加消息优先级、队列丢弃策略和压力测试。

## CockpitDiag 诊断 Agent

仓库新增 [`cockpit-diag-agent`](cockpit-diag-agent/) 子项目。它使用 Java 21 和
Spring Boot 接收车辆信号或导入本监控器日志，编排信号检查、日志分析、诊断
知识检索与测试方案工具，生成带证据、风险等级、置信度和安全边界的诊断报告。

```bash
cd cockpit-diag-agent
mvn clean verify
mvn spring-boot:run
```

## License

MIT
