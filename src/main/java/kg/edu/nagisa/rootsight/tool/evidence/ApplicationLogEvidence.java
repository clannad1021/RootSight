package kg.edu.nagisa.rootsight.tool.evidence;

import java.util.List;

/**
 * 应用日志证据。真实 Loki Tool 会在后续阶段沿用相同的结构化返回思路。
 */
public record ApplicationLogEvidence(
        String targetService,
        String timeRange,
        List<String> errorLogs
) {
}
