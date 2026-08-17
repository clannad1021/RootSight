package kg.edu.nagisa.rootsight.knowledge.model;

import org.springframework.ai.document.Document;

import java.util.List;

/**
 * 一次知识源读取和分块的不可变结果，版本由全部源文件内容共同决定。
 */
public record KnowledgeDocumentBatch(
        String version,
        int sourceCount,
        List<Document> documents
) {
}
