# RootSight

RootSight 是一个面向通用软件系统的轻量级智能运维故障诊断 Agent。

用户只需描述故障现象，RootSight 会根据问题自主选择可用 Tool 收集证据，并让大语言模型结合证据生成诊断结论和处理建议。项目当前只提供观察与分析能力，不执行重启服务、修改配置、执行 SQL 等运维写操作。

## 当前阶段

项目已完成 Stage 1A、Stage 1B 和 Stage 2A：

- 通过 Spring AI `ChatClient` 调用 DeepSeek。
- 提供统一的故障诊断 REST API。
- 支持 Bean Validation 和统一异常响应。
- 支持模型自主选择并多轮调用 Tool。
- 提供 Fake Metrics、Log、Redis Tool，用固定演示证据验证 Agent Loop。
- 提供真实 Redis、MySQL 只读 Tool，读取连通性和基础运行状态。
- 通过 SSE 流式返回模型正文，客户端无需等待完整回答生成。
- 在流结束事件中返回本次实际执行的 Tool 调用轨迹。
- 使用固定的纯文本报告层级，并在展示前清理 Markdown 格式噪声。

Tool 返回的 `evidenceSource` 会明确标记证据来源：`DEMO` 表示固定演示数据，`REAL` 表示从当前配置的真实基础设施读取。模型不得把演示证据当作生产环境事实。

## 诊断流程

```text
用户描述故障
    ↓
DiagnosisController / SSE
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
逐块返回纯文本诊断结论
    ↓
COMPLETED 事件返回 Tool 调用轨迹
```

模型没有固定的 Tool 调用顺序。它应根据用户问题、Tool 用途和已经取得的证据，决定调用哪些 Tool、以什么顺序调用以及何时停止取证。

## 技术栈

- Java 17
- Spring Boot 4.1.0
- Spring AI 2.0.0、Project Reactor
- DeepSeek V4 Flash
- Spring JDBC、MySQL Connector/J
- Spring Data Redis、Lettuce
- JavaFX 21
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
├── infrastructure
│   ├── mysql   MySQL 固定只读状态客户端
│   └── redis   Redis PING/INFO 状态客户端
├── desktop     JavaFX 桌面客户端
└── tool
    ├── evidence        Tool 返回的结构化证据
    ├── fake            Stage 1B 保留的模拟 Tool
    └── infrastructure  Stage 2A 的真实基础设施 Tool
```

## 运行项目

### 环境要求

- JDK 17
- 可用的 DeepSeek API Key
- 可访问的 Redis 和 MySQL；建议使用只读/监控账号

将 API Key 配置到环境变量，不要写入 `application.yml` 或提交到 Git：

```powershell
$env:DEEPSEEK_API_KEY="your-api-key"
```

启动项目：

```powershell
.\mvnw.cmd spring-boot:run
```

默认端口为 `8081`。可通过 `ROOTSIGHT_SERVER_PORT` 环境变量覆盖。

启动 JavaFX 桌面客户端：

```powershell
.\mvnw.cmd javafx:run
```

## 调用诊断接口

请求地址：

```text
POST http://localhost:8081/api/diagnoses
```

PowerShell 示例（`curl.exe -N` 会在每个 SSE 事件到达后立即显示，不等待连接结束）：

```powershell
$body = '{"question":"检查当前目标的 MySQL 和 Redis 状态并给出诊断。"}'
curl.exe -N `
  -X POST "http://localhost:8081/api/diagnoses" `
  -H "Content-Type: application/json; charset=utf-8" `
  --data-raw $body
```

响应为三类 SSE 事件：

```text
event:content
data:{"type":"CONTENT","content":"诊断结论：\n","toolCalls":[]}

event:content
data:{"type":"CONTENT","content":"当前目标基础设施可访问。","toolCalls":[]}

event:completed
data:{"type":"COMPLETED","content":"","toolCalls":[{"toolName":"inspect_mysql_status","summary":"..."}]}
```

- `CONTENT`：模型正文增量，前端收到后直接追加显示。
- `COMPLETED`：正文结束，并携带完整 Tool 调用轨迹。
- `ERROR`：流式调用失败时返回经过脱敏的错误消息；已经产生的 Tool 轨迹会一并保留。

模型最终报告固定使用“诊断结论、关键证据、推理依据、处理建议”四层纯文本结构。服务端会过滤正式报告前的过程性前言，防御性移除星号、井号、代码围栏和行首装饰性斜杠，但会保留 `/api/orders`、`HTTP/2` 等正文中的有效斜杠。连续 token 会在约 40ms 的小窗口内合并，以减少前端刷新次数，同时保持实时输出。

## 配置项

| 环境变量 | 默认值 | 作用 |
| --- | --- | --- |
| `DEEPSEEK_API_KEY` | 无 | DeepSeek API 密钥，必须配置 |
| `DEEPSEEK_BASE_URL` | `https://api.deepseek.com` | DeepSeek API 地址 |
| `DEEPSEEK_CHAT_MODEL` | `deepseek-v4-flash` | 对话模型 |
| `DEEPSEEK_CHAT_TEMPERATURE` | `0.2` | 模型采样温度 |
| `ROOTSIGHT_SERVER_PORT` | `8081` | RootSight 服务端口 |
| `ROOTSIGHT_AI_RETRY_MAX_ATTEMPTS` | `2` | 模型调用最大尝试次数 |
| `ROOTSIGHT_TARGET_NAME` | `default-target` | 当前基础设施目标的逻辑名称 |
| `ROOTSIGHT_DB_URL` | `jdbc:mysql://localhost:3306/` | MySQL JDBC 地址，不要求绑定业务数据库 |
| `ROOTSIGHT_DB_USERNAME` | `readonly` | MySQL 只读账号 |
| `ROOTSIGHT_DB_PASSWORD` | 无 | MySQL 只读账号密码 |
| `ROOTSIGHT_REDIS_HOST` | `localhost` | Redis 地址 |
| `ROOTSIGHT_REDIS_PORT` | `6379` | Redis 端口 |
| `ROOTSIGHT_REDIS_USERNAME` | `rootsight` | Redis ACL 只读账号 |
| `ROOTSIGHT_REDIS_PASSWORD` | 无 | Redis ACL 账号密码 |
| `ROOTSIGHT_REDIS_DATABASE` | `2` | Redis 数据库编号 |

## 基础设施只读边界

- MySQL Tool 只执行源码中固定的实例信息和 `SHOW GLOBAL STATUS` 查询，不接收模型生成的 SQL。
- Hikari 连接池启用只读模式，并建议配合数据库只读账号形成双重约束。
- Redis Tool 只执行 `PING` 和分区 `INFO`，不遍历 Key，也不执行写命令。
- Redis 账号只有 `PING` 权限时仍返回 `UP`，同时用 `metricsAvailable=false` 标明 INFO 指标不可用。
- 连接失败会返回 `DOWN` 结构化证据，不会把 JDBC/Redis 底层异常或连接信息发送给模型。
- Redis/MySQL 属于被观察目标，不参与 RootSight 自身 Actuator 健康判定；目标宕机时诊断服务仍保持可用。

## 阶段进度

| 阶段 | 状态 | 内容 |
| --- | --- | --- |
| Stage 1A | 已完成 | 基础工程、真实模型调用和诊断 API |
| Stage 1B | 已完成 | Fake Tool、多步 Tool Calling 和调用轨迹 |
| Stage 2A | 已完成 | 接入真实 Redis 和 MySQL 只读 Tool |
| Stage 2B | 待实现 | 接入 RabbitMQ 和安全配置查询 Tool |
| Stage 3 | 待实现 | Loki 日志采集与日志查询 Tool |
| Stage 4 | 待实现 | Prometheus 指标采集与指标查询 Tool |
| Stage 5 | 待实现 | RAG 运行知识库 |
| Stage 6 | 待实现 | 受控诊断工作流 |
| Stage 7 | 待实现 | 故障场景与 Evaluation |

## 当前边界

- Metrics 与 Log 仍为固定演示数据；Redis 和 MySQL 已连接真实基础设施。
- REST API 与 JavaFX 客户端均已使用流式诊断；JavaFX 会逐段追加模型回答。
- 尚未实现会话记忆、RAG 和持久化诊断状态。
- RootSight 只提供读取、分析和建议，不执行具有副作用的运维操作。
