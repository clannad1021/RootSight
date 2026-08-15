package kg.edu.nagisa.rootsight.tool.evidence;

import java.time.Instant;

/**
 * 单条 Loki 日志证据；消息在进入模型上下文前已经过脱敏和长度限制。
 */
public record LokiLogEntryEvidence(
        Instant timestamp,
        String level,
        String message
) {
}
