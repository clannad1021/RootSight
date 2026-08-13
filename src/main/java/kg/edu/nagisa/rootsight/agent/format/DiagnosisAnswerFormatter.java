package kg.edu.nagisa.rootsight.agent.format;

import org.springframework.stereotype.Component;

/**
 * 清理模型流中的 Markdown 装饰符，确保纯文本前端不会显示星号、标题井号等格式噪声。
 * Markdown 是一种轻量级文本格式，通过特殊符号表达标题、加粗、列表、代码等样式。
 */
@Component
public class DiagnosisAnswerFormatter {

    private static final String REPORT_START = "诊断结论";

    /**
     * 为每次诊断创建独立的增量格式化器，保存当前是否处于行首的流式状态。
     */
    public StreamFormatter createStreamFormatter() {
        return new StreamFormatter();
    }

    /**
     * 有状态的流式文本清理器，能够正确处理 Markdown 标记恰好被拆到两个数据块的情况。
     */
    public static final class StreamFormatter {
        //标记行首
        private boolean lineStart = true;
        private boolean reportStarted;
        //暂时保存还没有确定是否属于正式报告的文字。它主要解决“诊断结论”被拆成多个数据块的问题
        private final StringBuilder pendingUntilReportStart = new StringBuilder();

        /**
         * 清理一个模型增量文本块，同时保留正文中的 URL、接口路径和服务名称。
         */
        public String format(String chunk) {
            if (chunk == null || chunk.isEmpty()) {
                return "";
            }

            StringBuilder result = new StringBuilder(chunk.length());
            for (int index = 0; index < chunk.length(); index++) {
                char current = chunk.charAt(index);

                // Markdown 的加粗、标题、代码和删除线标记在 TextArea 中没有样式意义，直接移除。
                if (current == '*' || current == '#' || current == '`' || current == '~') {
                    continue;
                }
                if (current == '\r') {
                    continue;
                }
                if (current == '\n') {
                    result.append(current);
                    lineStart = true;
                    continue;
                }

                if (lineStart) {
                    //删除行首空格
                    if (Character.isWhitespace(current)) {
                        continue;
                    }
                    // 仅清理行首的装饰性斜杠，正文中的 /api、HTTP/2 等信息保持原样。
                    if (current == '/' || current == '\\') {
                        continue;
                    }
                    // 输出规范要求使用编号；若模型仍返回 Markdown 横线列表，则移除装饰符，仅保留正文。
                    if (current == '-' || current == '+') {
                        continue;
                    }
                }

                result.append(current);
                if (!Character.isWhitespace(current)) {
                    lineStart = false;
                }
            }
            return keepReportContentOnly(result.toString());
        }

        /**
         * 丢弃正式“诊断结论”之前的模型前置文本，只向客户端输出完整诊断报告内容。
         */
        private String keepReportContentOnly(String formattedChunk) {
            if (reportStarted || formattedChunk.isEmpty()) {
                return formattedChunk;
            }

            pendingUntilReportStart.append(formattedChunk);
            int reportStartIndex = pendingUntilReportStart.indexOf(REPORT_START);
            if (reportStartIndex >= 0) {
                reportStarted = true;
                String reportContent = pendingUntilReportStart.substring(reportStartIndex);
                pendingUntilReportStart.setLength(0);
                return reportContent;
            }

            // 仅保留可能与下一数据块组成“诊断结论”的尾部，模型在正式报告前的过程性前言不发送给前端。
            int retainedLength = Math.min(pendingUntilReportStart.length(), REPORT_START.length() - 1);
            String retainedSuffix = pendingUntilReportStart.substring(
                    pendingUntilReportStart.length() - retainedLength
            );
            pendingUntilReportStart.setLength(0);
            pendingUntilReportStart.append(retainedSuffix);
            return "";
        }
    }
}
