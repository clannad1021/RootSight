package kg.edu.nagisa.rootsight.tool.evidence;

/**
 * 单条运行知识片段，保留来源和相似度以便模型区分文档事实与实时观测。
 */
public record KnowledgeSnippetEvidence(
        String source,
        int chunkIndex,
        Double similarityScore,
        String content
) {
}
