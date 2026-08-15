# RootSight

RootSight 是一个面向通用软件系统的轻量级智能运维故障诊断 Agent。

用户只需描述故障现象，RootSight 会根据问题自主选择可用 Tool 收集证据，并让大语言模型结合证据生成诊断结论和处理建议。项目当前只提供观察与分析能力，不执行重启服务、修改配置、执行 SQL 等运维写操作。

## 当前阶段

项目已完成 Stage 1A、Stage 1B、Stage 2A、Stage 2B 和 Stage 3：

- 通过 Spring AI `ChatClient` 调用 DeepSeek。
- 提供统一的故障诊断 REST API。
- 支持 Bean Validation 和统一异常响应。
- 支持模型自主选择并多轮调用 Tool。
- 保留 Fake Tool 源码用于回归 Stage 1B 的 Agent Loop；运行时日志能力已切换为真实 Loki Tool。
- 提供真实 Redis、MySQL、RabbitMQ 只读 Tool，读取连通性和基础运行状态。
- 通过 Grafana Alloy 采集目标应用文件日志，并提供真实 Loki 只读查询 Tool。
- 日志查询支持故障时间锚点、渐进扩窗、最近异常兜底和命中点上下文补查。
- LogQL 由后端根据结构化参数构造，模型不能提交任意 LogQL。
- 提供固定白名单的安全配置 Tool，不允许按任意配置键读取环境信息。
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
- Spring `RestClient`、RabbitMQ Management HTTP API
- Grafana Loki 3.7.2、Grafana Alloy 1.18.0、Docker Compose
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
│   ├── loki    Loki 受控日志查询客户端
│   ├── mysql   MySQL 固定只读状态客户端
│   ├── rabbitmq RabbitMQ Management API 状态客户端
│   └── redis    Redis PING/INFO 状态客户端
├── desktop     JavaFX 桌面客户端
└── tool
    ├── evidence        Tool 返回的结构化证据
    ├── fake            Stage 1B 保留的模拟 Tool
    └── infrastructure  Stage 2A～3 的真实基础设施、日志与安全配置 Tool
observability           Loki、Alloy 与 Docker Compose 配置
observed-logs           Alloy 允许读取的目标应用日志目录
```

## 运行项目

### 环境要求

- JDK 17
- 可用的 DeepSeek API Key
- 可访问的 Redis、MySQL 和启用了 Management 插件的 RabbitMQ；建议使用只读/监控账号
- Docker Desktop 与 Docker Compose，用于运行本地 Loki 和 Alloy

将 API Key 配置到环境变量，不要写入 `application.yml` 或提交到 Git：

```powershell
$env:DEEPSEEK_API_KEY="your-api-key"
```

启动项目：

```powershell
.\mvnw.cmd spring-boot:run
```

默认端口为 `8081`。可通过 `ROOTSIGHT_SERVER_PORT` 环境变量覆盖。

### 启动日志采集

将需要观察的应用日志写入项目根目录的 `observed-logs`。支持 `.log`、`.txt` 和 `.json` 文件，真实日志文件不会进入 Git。

启动 Loki 和 Alloy：

```powershell
docker compose -f observability/docker-compose.yml up -d
```

默认情况下 Alloy 为日志添加 `service_name=observed-target`。如需使用其他逻辑服务名，应保证 Alloy 标签和 RootSight 默认查询服务一致：

```powershell
$env:ROOTSIGHT_OBSERVED_SERVICE="order-service"
$env:ROOTSIGHT_LOKI_DEFAULT_SERVICE="order-service"
docker compose -f observability/docker-compose.yml up -d
```

检查运行状态：

```powershell
docker compose -f observability/docker-compose.yml ps
Invoke-WebRequest http://127.0.0.1:3100/ready
```

停止日志基础设施：

```powershell
docker compose -f observability/docker-compose.yml down
```

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
| `ROOTSIGHT_RABBITMQ_MANAGEMENT_URL` | `http://127.0.0.1:15672` | RabbitMQ Management API 地址 |
| `ROOTSIGHT_RABBITMQ_USERNAME` | `rootsight_monitor` | RabbitMQ 监控账号 |
| `ROOTSIGHT_RABBITMQ_PASSWORD` | 无 | RabbitMQ 监控账号密码 |
| `ROOTSIGHT_RABBITMQ_VHOST` | `/` | 允许查询的 RabbitMQ vhost |
| `ROOTSIGHT_RABBITMQ_QUEUE_PAGE_SIZE` | `100` | 单次检查的队列分页大小，代码强制限制在 1～500 |
| `ROOTSIGHT_RABBITMQ_QUEUE_SAMPLE_LIMIT` | `20` | 最多返回给模型的队列样本数 |
| `ROOTSIGHT_RABBITMQ_CONNECT_TIMEOUT` | `3s` | Management API 连接超时 |
| `ROOTSIGHT_RABBITMQ_READ_TIMEOUT` | `5s` | Management API 读取超时 |
| `ROOTSIGHT_LOKI_URL` | `http://127.0.0.1:3100` | Loki HTTP API 地址 |
| `ROOTSIGHT_LOKI_SERVICE_LABEL` | `service_name` | 后端允许使用的固定服务标签名 |
| `ROOTSIGHT_LOKI_DEFAULT_SERVICE` | `observed-target` | Tool 未提供服务名时查询的默认标签值 |
| `ROOTSIGHT_LOKI_DEFAULT_LIMIT` | `50` | 默认返回日志数量 |
| `ROOTSIGHT_LOKI_MAX_LIMIT` | `100` | 单次查询允许返回的日志上限 |
| `ROOTSIGHT_LOKI_MAX_FILTER_LENGTH` | `120` | 服务名、关键词和 traceId 的最大字符数 |
| `ROOTSIGHT_LOKI_MAX_LINE_LENGTH` | `1000` | 单条日志进入模型上下文的最大字符数 |
| `ROOTSIGHT_LOKI_DEFAULT_WINDOW` | `30m` | 未提供故障时间时的初始查询窗口 |
| `ROOTSIGHT_LOKI_EXPANSION_WINDOW_1` | `2h` | 第一次扩大窗口 |
| `ROOTSIGHT_LOKI_EXPANSION_WINDOW_2` | `6h` | 第二次扩大窗口 |
| `ROOTSIGHT_LOKI_EXPANSION_WINDOW_3` | `24h` | 第三次扩大窗口 |
| `ROOTSIGHT_LOKI_FALLBACK_WINDOW` | `168h` | 最近异常兜底允许回溯的最大范围 |
| `ROOTSIGHT_LOKI_INCIDENT_BEFORE` | `10m` | 故障时间锚点之前的初始范围 |
| `ROOTSIGHT_LOKI_INCIDENT_AFTER` | `20m` | 故障时间锚点之后的初始范围 |
| `ROOTSIGHT_LOKI_CONTEXT_WINDOW` | `30s` | 命中异常后向前和向后补查的上下文范围 |
| `ROOTSIGHT_LOKI_CONTEXT_LIMIT` | `20` | 上下文补查最多返回的日志数 |
| `ROOTSIGHT_LOKI_CONNECT_TIMEOUT` | `3s` | Loki 连接超时 |
| `ROOTSIGHT_LOKI_READ_TIMEOUT` | `8s` | Loki 查询读取超时 |

## 基础设施只读边界

- MySQL Tool 只执行源码中固定的实例信息和 `SHOW GLOBAL STATUS` 查询，不接收模型生成的 SQL。
- Hikari 连接池启用只读模式，并建议配合数据库只读账号形成双重约束。
- Redis Tool 只执行 `PING` 和分区 `INFO`，不遍历 Key，也不执行写命令。
- Redis 账号只有 `PING` 权限时仍返回 `UP`，同时用 `metricsAvailable=false` 标明 INFO 指标不可用。
- RabbitMQ Tool 只通过 Management HTTP API 读取节点概览和指定 vhost 的有界队列分页，不建立 AMQP 连接、不读取消息正文，也不发布或消费消息。
- 队列总数超过当前分页或样本上限时使用 `queueResultTruncated=true` 明确标记，分页汇总字段使用 `sampled` 前缀，避免把局部数据误当作全局总量。
- 安全配置 Tool 没有任意配置键参数，只返回应用名、逻辑目标、模型名、服务端口、Redis 数据库编号、RabbitMQ vhost、Loki 默认服务标签值和可用只读 Tool；密码、密钥、用户名、连接 URL、原始环境变量始终排除。
- 连接失败会返回 `DOWN` 结构化证据，不会把 JDBC、Redis 或 RabbitMQ 底层异常和连接信息发送给模型。
- Redis、MySQL、RabbitMQ 属于被观察目标，不参与 RootSight 自身 Actuator 健康判定；目标宕机时诊断服务仍保持可用。

## Loki 日志查询边界

- 模型只能提供目标服务、带时区的故障时间、关键词、traceId 和期望数量，不能提交 LogQL。
- 后端固定使用配置的服务标签构造精确流选择器，并对所有文本值执行长度校验和 LogQL 字符串转义。
- 默认查询最近 30 分钟；无异常时依次扩展到 2 小时、6 小时和 24 小时，最后最多回溯 7 天查找最近异常。
- `requestedRange` 表示 Agent 请求的初始窗口，`effectiveRange` 表示实际命中的窗口；`strategy` 区分精确查询、扩窗、历史兜底和无匹配。
- 返回数量强制限制在安全上限内；达到上限时设置 `truncated=true` 并返回 `nextCursor`。
- 命中 ERROR/WARN 后，仅围绕最新命中点补查短时间上下文；上下文失败不会丢弃已经取得的主查询证据。
- 常见密码、API Key、Authorization、Token 和 Bearer Token 会在进入模型上下文前脱敏，单条日志也会执行长度限制。
- Loki 不可用返回 `UNAVAILABLE`，查询成功但没有日志返回 `NO_MATCH`，两者不会混为一谈。

## 阶段进度

| 阶段 | 状态 | 内容 |
| --- | --- | --- |
| Stage 1A | 已完成 | 基础工程、真实模型调用和诊断 API |
| Stage 1B | 已完成 | Fake Tool、多步 Tool Calling 和调用轨迹 |
| Stage 2A | 已完成 | 接入真实 Redis 和 MySQL 只读 Tool |
| Stage 2B | 已完成 | 接入 RabbitMQ Management API 和安全配置查询 Tool |
| Stage 3 | 已完成 | Alloy 日志采集、Loki 受控查询和自适应时间窗口 |
| Stage 4 | 待实现 | Prometheus 指标采集与指标查询 Tool |
| Stage 5 | 待实现 | RAG 运行知识库 |
| Stage 6 | 待实现 | 受控诊断工作流 |
| Stage 7 | 待实现 | 故障场景与 Evaluation |

## 当前边界

- Metrics 仍为固定演示数据；Log、Redis、MySQL 和 RabbitMQ 已连接真实基础设施。
- REST API 与 JavaFX 客户端均已使用流式诊断；JavaFX 会逐段追加模型回答。
- 尚未实现会话记忆、RAG 和持久化诊断状态。
- RootSight 只提供读取、分析和建议，不执行具有副作用的运维操作。
