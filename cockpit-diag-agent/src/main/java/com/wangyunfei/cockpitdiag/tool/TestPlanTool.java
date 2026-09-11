package com.wangyunfei.cockpitdiag.tool;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.wangyunfei.cockpitdiag.model.FaultCode;
import com.wangyunfei.cockpitdiag.model.TestSuggestion;

@Component
public class TestPlanTool {

    public List<TestSuggestion> generate(Set<FaultCode> codes) {
        List<TestSuggestion> tests = new ArrayList<>();
        if (codes.contains(FaultCode.OVER_TEMPERATURE)) {
            tests.add(new TestSuggestion(
                    "超温阈值与恢复测试",
                    "vcan0 已启动，监控器超时阈值为 1000 ms",
                    List.of("发送 109 °C", "发送 111 °C", "重复发送 120 °C", "恢复发送 100 °C"),
                    "110 °C 以下不告警；越阈值仅激活一次；恢复后仅输出一次 RECOVERED"));
        }
        if (codes.contains(FaultCode.SIGNAL_TIMEOUT)) {
            tests.add(new TestSuggestion(
                    "报文超时与恢复测试",
                    "监控器正常接收 0x100 周期报文",
                    List.of("停止发送超过 1500 ms", "观察超时事件", "恢复发送合法 0x100 报文"),
                    "产生一次 SIGNAL_TIMEOUT，报文恢复后产生一次恢复事件"));
        }
        if (codes.contains(FaultCode.ILLEGAL_VALUE)) {
            tests.add(new TestSuggestion(
                    "信号边界与非法枚举测试",
                    "可以构造原始 CAN 0x100 报文",
                    List.of("发送车速 260/260.01 km/h", "发送温度 -40/150/151 °C", "发送挡位 4/9"),
                    "边界值合法，越界值或未知挡位被识别为 ILLEGAL_VALUE"));
        }
        if (codes.contains(FaultCode.NO_SIGNAL_DATA)) {
            tests.add(new TestSuggestion(
                    "无数据降级测试",
                    "清空运行时数据后启动 Agent",
                    List.of("不导入日志直接请求诊断", "检查响应证据和置信度"),
                    "Agent 返回 NO_SIGNAL_DATA、低置信度且不臆测根因"));
        }
        return List.copyOf(tests);
    }
}
