package kg.edu.nagisa.rootsight.knowledge.model;

/**
 * 启动同步结果，用于日志说明是否真正写入新版本，且不包含知识正文。
 */
public record KnowledgeIndexResult(
        String status,
        String version,
        int sourceCount,
        int chunkCount
) {
}
