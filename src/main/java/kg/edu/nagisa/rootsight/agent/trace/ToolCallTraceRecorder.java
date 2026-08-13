package kg.edu.nagisa.rootsight.agent.trace;

import kg.edu.nagisa.rootsight.common.constant.ExceptionMessages;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 按诊断会话记录实际调用过的 Tool。
 *
 * <p>流式模型调用会跨越多个 Reactor 线程，不能再使用 ThreadLocal 传递轨迹。
 * 这里通过 Spring AI ToolContext 携带诊断 ID，使并发请求即使切换线程也不会互相污染。</p>
 */
@Component
public class ToolCallTraceRecorder {

    public static final String DIAGNOSIS_ID_CONTEXT_KEY = "rootsightDiagnosisId";
                  //是一个线程安全 Map 接口
    private final ConcurrentMap<String, CopyOnWriteArrayList<ToolCallTrace>> tracesByDiagnosisId
            = new ConcurrentHashMap<>(); //是 ConcurrentMap 接口的一种具体实现

    /**
     * 创建一次新的诊断轨迹并返回请求级诊断 ID。
     */
    public String start() {
        String diagnosisId = UUID.randomUUID().toString();
        tracesByDiagnosisId.put(diagnosisId, new CopyOnWriteArrayList<>());
        return diagnosisId;
    }

    /**
     * 根据模型请求携带的 ToolContext 记录一个已经实际执行的 Tool。
     */
    public void record(ToolContext toolContext, String toolName, String summary) {
        String diagnosisId = readDiagnosisId(toolContext);
        List<ToolCallTrace> traces = tracesByDiagnosisId.get(diagnosisId);
        if (traces == null) {
            throw new IllegalStateException(ExceptionMessages.DIAGNOSIS_TRACE_NOT_FOUND);
        }
        traces.add(new ToolCallTrace(toolName, summary));
    }

    /**
     * 返回指定诊断的不可变轨迹快照，避免响应发出后仍被后续逻辑修改。
     */
    public List<ToolCallTrace> snapshot(String diagnosisId) {
        return List.copyOf(tracesByDiagnosisId.getOrDefault(diagnosisId, new CopyOnWriteArrayList<>()));
    }

    /**
     * 在流结束、失败或被取消时清理会话轨迹，避免长时间运行后占用内存。
     */
    public void clear(String diagnosisId) {
        tracesByDiagnosisId.remove(diagnosisId);
    }

    /**
     * 从当前 Tool 调用上下文中提取请求级诊断 ID，用于隔离并发诊断轨迹。
     */
    private String readDiagnosisId(ToolContext toolContext) {
        Map<String, Object> context = toolContext == null ? Map.of() : toolContext.getContext();
        Object diagnosisId = context.get(DIAGNOSIS_ID_CONTEXT_KEY);
        if (!(diagnosisId instanceof String value) || value.isBlank()) {
            throw new IllegalStateException(ExceptionMessages.DIAGNOSIS_CONTEXT_MISSING);
        }
        return value;
    }
}
