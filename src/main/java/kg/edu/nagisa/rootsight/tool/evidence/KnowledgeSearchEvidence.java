package kg.edu.nagisa.rootsight.tool.evidence;

import java.util.List;

/**
 * 运行知识检索证据；该证据描述系统设计与手册，不代表当前实时运行状态。
 */
public record KnowledgeSearchEvidence(
        String evidenceSource,
        String evidenceKind,
        boolean realtimeEvidence,
        String targetName,
        String knowledgeSystem,
        String status,
        boolean available,
        long responseTimeMs,
        int matchedCount,
        List<KnowledgeSnippetEvidence> snippets,
        String detail
) {
}
