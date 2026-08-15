package kg.edu.nagisa.rootsight.tool.evidence;

import java.util.List;

/**
 * 运行配置的固定白名单证据，只暴露诊断所需的非敏感字段。
 */
public record SafeConfigurationEvidence(
        String evidenceSource,
        String targetName,
        String applicationName,
        String aiModel,
        int serverPort,
        int redisDatabase,
        String rabbitMqVhost,
        String lokiDefaultService,
        List<String> availableReadOnlyTools,
        List<String> excludedSensitiveCategories
) {
}
