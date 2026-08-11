# RootSight

RootSight 是一个面向通用软件系统的轻量级智能运维故障诊断 Agent。

用户只需描述故障现象，RootSight 会根据问题自主选择可用 Tool 收集证据，并让大语言模型结合证据生成诊断结论和处理建议。项目当前只提供观察与分析能力，不执行重启服务、修改配置、执行 SQL 等运维写操作。

## 当前阶段

项目已完成 Stage 1A 和 Stage 1B：

- 通过 Spring AI `ChatClient` 调用 DeepSeek。
- 提供统一的故障诊断 REST API。
- 支持 Bean Validation 和统一异常响应。
- 支持模型自主选择并多轮调用 Tool。
- 提供 Fake Metrics、Log、Redis Tool，用固定演示证据验证 Agent Loop。
- 在接口响应中返回本次实际执行的 Tool 调用轨迹。

当前 Fake Tool 仅用于验证架构和调用流程，返回结果不代表任何真实环境状态。

## 诊断流程

```text
用户描述故障
    ↓
DiagnosisController
    ↓
DiagnosisService
    ↓
ChatClient / DeepSeek
    ↓
模型根据问题自主选择 Tool
    ↓
Java Tool 返回结构化证据
    ↓
模型判断是否需要继续取证
    ↓
生成诊断结论 + Tool 调用轨迹
```

模型没有固定的 Tool 调用顺序。它应根据用户问题、Tool 用途和已经取得的证据，决定调用哪些 Tool、以什么顺序调用以及何时停止取证。

## 技术栈

- Java 17
- Spring Boot 4.1.0
- Spring AI 2.0.0
- DeepSeek V4 Flash
- Maven
- Lombok
- JUnit 6、MockMvc、Mockito

## 项目结构

```text
src/main/java/kg/edu/nagisa/rootsight
├── agent       诊断服务、诊断结果和 Tool 调用轨迹
├── api         REST 接口及请求响应 DTO
├── common      公共常量和统一异常处理
├── config      ChatClient 与 AI 配置
└── tool
    ├── evidence  Tool 返回的结构化证据
    └── fake      Stage 1B 使用的模拟 Tool
```

## 运行项目

### 环境要求

- JDK 17
- 可用的 DeepSeek API Key

将 API Key 配置到环境变量，不要写入 `application.yml` 或提交到 Git：

```powershell
$env:DEEPSEEK_API_KEY="your-api-key"
```

启动项目：

```powershell
.\mvnw.cmd spring-boot:run
```

默认端口为 `8081`。可通过 `ROOTSIGHT_SERVER_PORT` 环境变量覆盖。

## 调用诊断接口

请求地址：

```text
POST http://localhost:8081/api/diagnoses
```

PowerShell 示例：

```powershell
$body = @{
    question = "演示环境中的 order-service 出现故障，请主动收集必要证据并诊断。"
} | ConvertTo-Json

Invoke-RestMethod `
  -Uri "http://localhost:8081/api/diagnoses" `
  -Method Post `
  -ContentType "application/json; charset=utf-8" `
  -Body ([Text.Encoding]::UTF8.GetBytes($body))
```

响应结构：

```json
{
  "answer": "模型根据证据生成的诊断结论",
  "toolCalls": [
    {
      "toolName": "模型实际调用的 Tool 名称",
      "summary": "Tool 返回证据的摘要"
    }
  ]
}
```

## 配置项

| 环境变量 | 默认值 | 作用 |
| --- | --- | --- |
| `DEEPSEEK_API_KEY` | 无 | DeepSeek API 密钥，必须配置 |
| `DEEPSEEK_BASE_URL` | `https://api.deepseek.com` | DeepSeek API 地址 |
| `DEEPSEEK_CHAT_MODEL` | `deepseek-v4-flash` | 对话模型 |
| `DEEPSEEK_CHAT_TEMPERATURE` | `0.2` | 模型采样温度 |
| `ROOTSIGHT_SERVER_PORT` | `8081` | RootSight 服务端口 |
| `ROOTSIGHT_AI_RETRY_MAX_ATTEMPTS` | `2` | 模型调用最大尝试次数 |

## 阶段进度

| 阶段 | 状态 | 内容 |
| --- | --- | --- |
| Stage 1A | 已完成 | 基础工程、真实模型调用和诊断 API |
| Stage 1B | 已完成 | Fake Tool、多步 Tool Calling 和调用轨迹 |
| Stage 2A | 待实现 | 接入真实 Redis 和 MySQL 只读 Tool |
| Stage 2B | 待实现 | 接入 RabbitMQ 和安全配置查询 Tool |
| Stage 3 | 待实现 | Loki 日志采集与日志查询 Tool |
| Stage 4 | 待实现 | Prometheus 指标采集与指标查询 Tool |
| Stage 5 | 待实现 | RAG 运行知识库 |
| Stage 6 | 待实现 | 受控诊断工作流 |
| Stage 7 | 待实现 | 故障场景与 Evaluation |

## 当前边界

- Fake Tool 返回固定演示数据，尚未连接真实基础设施。
- 诊断请求当前为同步调用。
- 尚未实现会话记忆、RAG 和持久化诊断状态。
- RootSight 只提供读取、分析和建议，不执行具有副作用的运维操作。
