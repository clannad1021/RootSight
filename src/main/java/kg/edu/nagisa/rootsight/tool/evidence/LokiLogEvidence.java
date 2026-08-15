package kg.edu.nagisa.rootsight.tool.evidence;

import java.util.List;

/**
 * Loki 真实日志查询证据，明确记录请求范围、实际范围、扩窗策略和截断状态。
 */
public record LokiLogEvidence(
        String evidenceSource,
        String targetName,
        String targetService,
        String status,
        boolean available,
        long responseTimeMs,
        LogTimeRangeEvidence requestedRange,
        LogTimeRangeEvidence effectiveRange,
        String strategy,
        int queryAttempts,
        int matchedCount,
        boolean truncated,
        String nextCursor,
        List<LokiLogEntryEvidence> logs,
        boolean contextAvailable,
        List<LokiLogEntryEvidence> contextLogs,
        String detail
) {
}
