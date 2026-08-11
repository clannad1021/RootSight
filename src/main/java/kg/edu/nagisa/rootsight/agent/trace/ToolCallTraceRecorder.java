package kg.edu.nagisa.rootsight.agent.trace;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 记录当前同步诊断请求调用过的 Tool。
 *
 * <p>Stage 1B 的 ChatClient 和 Tool 在同一调用线程中执行，因此先用 ThreadLocal 隔离并发请求；
 * 后续诊断工作流阶段会把它替换为显式的诊断状态对象。</p>
 */
@Component
public class ToolCallTraceRecorder {

    private final ThreadLocal<List<ToolCallTrace>> traces = ThreadLocal.withInitial(ArrayList::new);

    /** 开始一次新的诊断轨迹。 */
    public void start() {
        traces.set(new ArrayList<>());
    }

    /** 记录一个已经实际执行的 Tool。 */
    public void record(String toolName, String summary) {
        traces.get().add(new ToolCallTrace(toolName, summary));
    }

    /** 返回不可变快照，避免响应返回后轨迹仍被后续逻辑修改。 */
    public List<ToolCallTrace> snapshot() {
        return List.copyOf(traces.get());
    }

    /** 清理线程变量，防止线程池复用造成跨请求数据污染。 */
    public void clear() {
        traces.remove();
    }
}
