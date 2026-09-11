package com.wangyunfei.cockpitdiag.tool;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.wangyunfei.cockpitdiag.model.FaultCode;

@Component
public class KnowledgeLookupTool {

    public KnowledgeResult lookup(Set<FaultCode> codes) {
        Set<String> actions = new LinkedHashSet<>();
        List<String> references = new ArrayList<>();

        for (FaultCode code : codes) {
            switch (code) {
                case OVER_TEMPERATURE -> {
                    actions.add("停车并保持安全状态，复核温度传感器与冷却回路");
                    actions.add("保存故障前后 CAN 报文和监控日志，禁止仅清除告警后继续测试");
                    references.add("docs/diagnostic-playbook.md#over-temperature");
                }
                case SIGNAL_TIMEOUT -> {
                    actions.add("检查 vcan/CAN 接口状态、报文周期及接收线程是否阻塞");
                    actions.add("对比发送端与接收端时间戳，定位报文丢失发生在哪一层");
                    references.add("docs/diagnostic-playbook.md#signal-timeout");
                }
                case ILLEGAL_VALUE -> {
                    actions.add("核对 CAN ID、字节序、缩放系数和挡位枚举定义");
                    actions.add("保留原始帧并使用边界值用例复现，避免直接修改线上阈值");
                    references.add("docs/diagnostic-playbook.md#illegal-value");
                }
                case NO_SIGNAL_DATA -> {
                    actions.add("先导入监控日志或上报一帧车辆信号，再执行故障诊断");
                    references.add("docs/diagnostic-playbook.md#no-data");
                }
            }
        }
        return new KnowledgeResult(List.copyOf(actions), List.copyOf(references));
    }

    public record KnowledgeResult(List<String> actions, List<String> references) {
    }
}
