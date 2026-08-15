package kg.edu.nagisa.rootsight.tool.evidence;

import java.time.Instant;

/**
 * 日志查询的绝对时间范围，避免“最近几分钟”等相对描述产生歧义。
 */
public record LogTimeRangeEvidence(
        Instant start,
        Instant end
) {
}
