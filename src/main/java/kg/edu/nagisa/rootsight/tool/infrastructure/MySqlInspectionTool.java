package kg.edu.nagisa.rootsight.tool.infrastructure;

import kg.edu.nagisa.rootsight.agent.trace.ToolCallTraceRecorder;
import kg.edu.nagisa.rootsight.agent.workflow.DiagnosisWorkflowCoordinator;
import kg.edu.nagisa.rootsight.infrastructure.mysql.MySqlStatusClient;
import kg.edu.nagisa.rootsight.tool.evidence.MySqlStatusEvidence;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/**
 * 向 Agent 暴露 MySQL 真实只读状态检查能力。
 */
@Component
@RequiredArgsConstructor
public class MySqlInspectionTool {

    private final MySqlStatusClient mySqlStatusClient;
    private final ToolCallTraceRecorder traceRecorder;
    private final DiagnosisWorkflowCoordinator workflowCoordinator;

    /**
     * 检查当前配置目标的 MySQL 服务状态，只执行代码内固定的只读 SQL。
     */
    @Tool(name = "inspect_mysql_status",
            description = "读取当前目标 MySQL 的真实连通性、版本、运行时间、连接线程、查询量和慢查询数。仅在这些证据有助于回答当前问题时调用。")
    public MySqlStatusEvidence inspectMySqlStatus(ToolContext toolContext) {
        workflowCoordinator.beforeToolCall(toolContext);
        MySqlStatusEvidence evidence = mySqlStatusClient.inspectStatus();
        traceRecorder.record(toolContext, "inspect_mysql_status",
                "[REAL] 目标=" + evidence.targetName()
                        + "，MySQL=" + evidence.status()
                        + "，耗时=" + evidence.responseTimeMs() + "ms");
        return evidence;
    }
}
