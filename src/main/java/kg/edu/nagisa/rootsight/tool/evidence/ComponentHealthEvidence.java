package kg.edu.nagisa.rootsight.tool.evidence;

/**
 * 基础设施组件健康证据。
 */
public record ComponentHealthEvidence(
        String component,
        String status,
        boolean reachable,
        String detail
) {
}
