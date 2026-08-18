# RootSight

RootSight 是一个面向通用软件系统的轻量级智能运维故障诊断 Agent。

用户只需描述故障现象，RootSight 会根据问题自主选择可用 Tool 收集证据，并让大语言模型结合证据生成诊断结论和处理建议。项目当前只提供观察与分析能力，不执行重启服务、修改配置、执行 SQL 等运维写操作。

## 当前阶段

项目已完成 Stage 1A、Stage 1B、Stage 2A、Stage 2B、Stage 3、Stage 4、Stage 5 和 Stage 6：

- 通过 Spring AI `ChatClient` 调用 DeepSeek。
- 提供统一的故障诊断 REST API。
- 支持 Bean Validation 和统一异常响应。
- 支持模型自主选择并多轮调用 Tool。
- 保留 Fake Tool 源码用于回归 Stage 1B 的 Agent Loop；运行时日志和指标能力已切换为真实 Loki/Prometheus Tool。
- 提供真实 Redis、MySQL、RabbitMQ 只读 Tool，读取连通性和基础运行状态。
- 通过 Grafana Alloy 采集目标应用文件日志，并提供真实 Loki 只读查询 Tool。
- 日志查询支持故障时间锚点、渐进扩窗、最近异常兜底和命中点上下文补查。
- LogQL 由后端根据结构化参数构造，模型不能提交任意 LogQL。
- 通过 Prometheus 抓取目标应用的标准 Micrometer 指标，并提供真实指标查询 Tool。
- PromQL 由后端固定生成，只允许模型选择服务、故障时间和白名单统计窗口。
- 使用硅基流动普通 `BAAI/bge-m3` Embedding 和本地 Qdrant 建立运行知识库。
- 启动时按内容版本同步 README、运维文档和 Runbook，同版本不会重复写入全部向量。
- 提供受控的运行知识检索 Tool，并明确区分文档知识与实时运行证据。
- 使用请求级有限状态机约束“规划、取证、归纳、完成/失败”诊断步骤。
- 对整次诊断设置总超时，并在每个真实 Tool 执行前原子预占调用预算。
- 通过 SSE 返回工作流状态、已使用 Tool 次数、调用上限和诊断耗时。
- 提供固定白名单的安全配置 Tool，不允许按任意配置键读取环境信息。
- 通过 SSE 流式返回模型正文，客户端无需等待完整回答生成。
- 在流结束事件中返回本次实际执行的 Tool 调用轨迹。
- 使用固定的纯文本报告层级，并在展示前清理 Markdown 格式噪声。

Tool 返回的 `evidenceSource` 会明确标记证据来源：`DEMO` 表示固定演示数据，`REAL` 表示从当前配置的数据源读取。运行知识还会返回 `evidenceKind=OPERATIONAL_KNOWLEDGE` 和 `realtimeEvidence=false`，模型不得把文档知识或演示证据当作当前生产状态。

## 诊断流程

```text
用户描述故障
    ↓
DiagnosisController / SSE
    ↓
DiagnosisService
    ↓
PLANNING：理解问题并规划需要的证据
    ↓
ChatClient / DeepSeek
    ↓
模型根据问题自主选择 Tool
    ↓
EVIDENCE_COLLECTION：执行受预算保护的只读 Tool
    ↓
Java Tool 返回结构化证据
    ↓
模型判断是否需要继续取证
    ↓
SYNTHESIS：停止 Tool 调用并归纳证据
    ↓
逐块返回纯文本诊断结论
    ↓
COMPLETED/ERROR 返回最终状态与 Tool 调用轨迹
```

模型没有固定的 Tool 调用顺序。它仍根据用户问题、Tool 用途和已经取得的证据决定调用哪些 Tool，但必须遵守统一诊断步骤、总时限和调用预算。

## 技术栈

- Java 17
- Spring Boot 4.1.0
- Spring AI 2.0.0、Project Reactor、硅基流动 `BAAI/bge-m3`
- DeepSeek V4 Flash
- Spring JDBC、MySQL Connector/J
- Spring Data Redis、Lettuce
- Spring `RestClient`、RabbitMQ Management HTTP API
- Grafana Loki 3.7.2、Grafana Alloy 1.18.0、Prometheus 3.13.1、Qdrant 1.18.2、Docker Compose
- JavaFX 21
- Maven
- Lombok
- JUnit 6、MockMvc、Mockito

## 项目结构

```text
src/main/java/kg/edu/nagisa/rootsight
├── agent       诊断服务、诊断结果、Tool 轨迹与请求级工作流状态机
├── api         REST 接口及请求响应 DTO
├── common      公共常量和统一异常处理
├── config      ChatClient 与 AI 配置
├── infrastructure
│   ├── loki    Loki 受控日志查询客户端
│   ├── mysql   MySQL 固定只读状态客户端
│   ├── prometheus Prometheus 固定指标查询客户端
│   ├── rabbitmq RabbitMQ Management API 状态客户端
│   └── redis    Redis PING/INFO 状态客户端
├── knowledge   本地知识文件加载、分块、版本同步和语义检索
├── desktop     JavaFX 桌面客户端
└── tool
    ├── evidence        Tool 返回的结构化证据
    ├── fake            Stage 1B 保留的模拟 Tool
    └── infrastructure  Stage 2A～4 的真实基础设施、日志、指标与安全配置 Tool
observability           Loki、Alloy、Prometheus、Qdrant 与 Docker Compose 配置
observed-logs           Alloy 允许读取的目标应用日志目录
```

## 运行项目

### 环境要求

- JDK 17
- 可用的 DeepSeek API Key
- 可访问的 Redis、MySQL 和启用了 Management 插件的 RabbitMQ；建议使用只读/监控账号
- 被观察应用需暴露 Prometheus 格式指标；Spring Boot 应用可使用 Actuator 和 Micrometer Registry
- 可用的硅基流动 API Key，用于普通 `BAAI/bge-m3` Embedding
- Docker Desktop 与 Docker Compose，用于运行本地 Loki、Alloy、Prometheus 和 Qdrant

将 API Key 配置到环境变量，不要写入 `application.yml` 或提交到 Git：

```powershell
$env:DEEPSEEK_API_KEY="your-api-key"
$env:SILICONFLOW_API_KEY="your-siliconflow-api-key"
```

启动项目：

```powershell
.\mvnw.cmd spring-boot:run
```

默认端口为 `8081`。可通过 `ROOTSIGHT_SERVER_PORT` 环境变量覆盖。

### 启动日志、指标与向量数据库

将需要观察的应用日志写入项目根目录的 `observed-logs`。支持 `.log`、`.txt` 和 `.json` 文件，真实日志文件不会进入 Git。

当前仓库附带的 `prometheus/targets/shortpan.json` 将 ShortPan 作为本地示例目标，地址为 `host.docker.internal:8080`，并添加 `application=short-pan` 标签。主 Prometheus 配置通过 `file_sd` 加载目标，因此替换业务应用时只需新增或修改目标文件，不需要修改 RootSight Java 代码。

当前本地 `observability/.env` 已把 Alloy 的只读日志目录指向 `D:/ShortPan/logs`，该文件不会提交 Git。其他环境可从 `.env.example` 复制并修改。

启动 Loki、Alloy、Prometheus 和 Qdrant：

```powershell
docker compose --env-file observability/.env -f observability/docker-compose.yml up -d
```

默认情况下 Alloy 为日志添加 `service_name=observed-target`。如需使用其他逻辑服务名，应保证 Alloy 标签和 RootSight 默认查询服务一致：

```powershell
$env:ROOTSIGHT_OBSERVED_LOG_PATH="D:/order-service/logs"
$env:ROOTSIGHT_OBSERVED_SERVICE="order-service"
$env:ROOTSIGHT_LOKI_DEFAULT_SERVICE="order-service"
$env:ROOTSIGHT_PROMETHEUS_DEFAULT_SERVICE="order-service"
docker compose --env-file observability/.env -f observability/docker-compose.yml up -d
```

检查运行状态：

```powershell
docker compose --env-file observability/.env -f observability/docker-compose.yml ps
Invoke-WebRequest http://127.0.0.1:3100/ready
Invoke-WebRequest http://127.0.0.1:9090/-/ready
Invoke-RestMethod http://127.0.0.1:9090/api/v1/targets
Invoke-RestMethod http://127.0.0.1:6333/
```

停止本地观测与向量基础设施：

```powershell
docker compose --env-file observability/.env -f observability/docker-compose.yml down
```

### 配置运行知识源

知识源目录与被观察系统通过环境变量配置，Java 代码不绑定 ShortPan。目录默认读取根目录 `README.md`、`docs/*.md` 和 `runbooks/*.md`，并排除面试题等不属于运行知识的文档：

```powershell
$env:ROOTSIGHT_KNOWLEDGE_SOURCE_ROOT="D:/ShortPan"
$env:ROOTSIGHT_KNOWLEDGE_SYSTEM_NAME="short-pan"
```

首次启动会创建 `rootsight_knowledge` 集合并完成分块、Embedding 和写入。此后内容版本未变化时返回 `UP_TO_DATE`；文档发生变化时先写入完整新版本，再清理同一系统的旧版本，避免更新中途留下不完整知识库。

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

响应为四类 SSE 事件：

```text
event:status
data:{"type":"STATUS","content":"","toolCalls":[],"workflow":{"state":"PLANNING","toolCallCount":0,"maxToolCalls":8,"elapsedMs":1}}

event:content
data:{"type":"CONTENT","content":"诊断结论：\n","toolCalls":[],"workflow":null}

event:content
data:{"type":"CONTENT","content":"当前目标基础设施可访问。","toolCalls":[],"workflow":null}

event:completed
data:{"type":"COMPLETED","content":"","toolCalls":[{"toolName":"inspect_mysql_status","summary":"..."}],"workflow":{"state":"COMPLETED","toolCallCount":1,"maxToolCalls":8,"elapsedMs":3200}}
```

- `STATUS`：工作流状态变化或 Tool 预算进度，状态包括 `PLANNING`、`EVIDENCE_COLLECTION` 和 `SYNTHESIS`。
- `CONTENT`：模型正文增量，前端收到后直接追加显示。
- `COMPLETED`：正文结束，并携带完整 Tool 调用轨迹和最终工作流快照。
- `ERROR`：流式调用失败、总超时或超过 Tool 上限时返回安全消息；已经完成的 Tool 轨迹和对应终态会一并保留。

模型最终报告固定使用“诊断结论、关键证据、推理依据、处理建议”四层纯文本结构。服务端会过滤正式报告前的过程性前言，防御性移除星号、井号、代码围栏和行首装饰性斜杠，但会保留 `/api/orders`、`HTTP/2` 等正文中的有效斜杠。连续 token 会在约 40ms 的小窗口内合并，以减少前端刷新次数，同时保持实时输出。

## 配置项

| 环境变量 | 默认值 | 作用 |
| --- | --- | --- |
| `DEEPSEEK_API_KEY` | 无 | DeepSeek API 密钥，必须配置 |
| `DEEPSEEK_BASE_URL` | `https://api.deepseek.com` | DeepSeek API 地址 |
| `DEEPSEEK_CHAT_MODEL` | `deepseek-v4-flash` | 对话模型 |
| `DEEPSEEK_CHAT_TEMPERATURE` | `0.2` | 模型采样温度 |
| `SILICONFLOW_API_KEY` | 无 | 硅基流动 Embedding API 密钥，必须配置且不会写入日志 |
| `SILICONFLOW_BASE_URL` | `https://api.siliconflow.cn/v1` | 硅基流动 OpenAI 兼容 API 基址 |
| `ROOTSIGHT_SERVER_PORT` | `8081` | RootSight 服务端口 |
| `ROOTSIGHT_AI_RETRY_MAX_ATTEMPTS` | `2` | 模型调用最大尝试次数 |
| `ROOTSIGHT_DIAGNOSIS_TIMEOUT` | `90s` | 单次诊断从规划到报告结束的总时限 |
| `ROOTSIGHT_DIAGNOSIS_MAX_TOOL_CALLS` | `8` | 单次诊断允许执行的真实 Tool 调用上限 |
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
| `ROOTSIGHT_PROMETHEUS_URL` | `http://127.0.0.1:9090` | Prometheus HTTP API 地址 |
| `ROOTSIGHT_PROMETHEUS_SERVICE_LABEL` | `application` | 后端允许使用的固定服务标签名 |
| `ROOTSIGHT_PROMETHEUS_DEFAULT_SERVICE` | `observed-target` | Tool 未提供服务名时查询的默认标签值 |
| `ROOTSIGHT_PROMETHEUS_DEFAULT_WINDOW` | `5m` | 未提供窗口时使用的统计窗口 |
| `ROOTSIGHT_PROMETHEUS_MAX_SERVICE_LENGTH` | `120` | 服务标签值的最大字符数 |
| `ROOTSIGHT_PROMETHEUS_QUERY_TIMEOUT` | `5s` | Prometheus 单条查询执行超时 |
| `ROOTSIGHT_PROMETHEUS_CONNECT_TIMEOUT` | `3s` | Prometheus 连接超时 |
| `ROOTSIGHT_PROMETHEUS_READ_TIMEOUT` | `8s` | Prometheus 查询读取超时 |
| `ROOTSIGHT_OBSERVED_LOG_PATH` | `../observed-logs` | Alloy 容器只读挂载的宿主日志目录 |
| `ROOTSIGHT_OBSERVED_SERVICE` | `observed-target` | Alloy 写入 Loki 的 `service_name` 标签值 |
| `ROOTSIGHT_QDRANT_HOST` | `127.0.0.1` | Qdrant gRPC 地址 |
| `ROOTSIGHT_QDRANT_GRPC_PORT` | `6334` | Qdrant gRPC 端口 |
| `ROOTSIGHT_QDRANT_USE_TLS` | `false` | 是否为 Qdrant gRPC 启用 TLS |
| `ROOTSIGHT_QDRANT_COLLECTION` | `rootsight_knowledge` | 运行知识集合名 |
| `ROOTSIGHT_KNOWLEDGE_SOURCE_ROOT` | `knowledge-base` | 通用运行知识源根目录 |
| `ROOTSIGHT_KNOWLEDGE_SYSTEM_NAME` | `observed-system` | 写入元数据并用于隔离检索的逻辑系统名 |
| `ROOTSIGHT_KNOWLEDGE_ENABLED` | `true` | 是否启用运行知识能力 |
| `ROOTSIGHT_KNOWLEDGE_AUTO_INDEX` | `true` | 是否在应用启动后自动同步知识 |
| `ROOTSIGHT_KNOWLEDGE_TOP_K` | `5` | 默认返回的相似知识分块数，代码上限为 10 |
| `ROOTSIGHT_KNOWLEDGE_SIMILARITY_THRESHOLD` | `0.45` | 语义检索相似度阈值 |

## 基础设施只读边界

- MySQL Tool 只执行源码中固定的实例信息和 `SHOW GLOBAL STATUS` 查询，不接收模型生成的 SQL。
- Hikari 连接池启用只读模式，并建议配合数据库只读账号形成双重约束。
- Redis Tool 只执行 `PING` 和分区 `INFO`，不遍历 Key，也不执行写命令。
- Redis 账号只有 `PING` 权限时仍返回 `UP`，同时用 `metricsAvailable=false` 标明 INFO 指标不可用。
- RabbitMQ Tool 只通过 Management HTTP API 读取节点概览和指定 vhost 的有界队列分页，不建立 AMQP 连接、不读取消息正文，也不发布或消费消息。
- 队列总数超过当前分页或样本上限时使用 `queueResultTruncated=true` 明确标记，分页汇总字段使用 `sampled` 前缀，避免把局部数据误当作全局总量。
- 安全配置 Tool 没有任意配置键参数，只返回应用名、逻辑目标、模型名、服务端口、Redis 数据库编号、RabbitMQ vhost、Loki/Prometheus 默认服务标签值和可用只读 Tool；密码、密钥、用户名、连接 URL、原始环境变量始终排除。
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

## Prometheus 指标查询边界

- 模型只能提供目标服务、带时区的故障时间和统计窗口，不能提交 PromQL、指标名或任意标签匹配器。
- 统计窗口限制为 `1m`、`5m`、`15m`、`30m` 和 `1h`；服务标签名由运维配置，标签值执行长度校验和字符串转义。
- 后端固定查询抓取可用性、HTTP QPS、5xx 错误率、成功率、P95/P99 延迟、进程 CPU、JVM 堆内存和存活线程数。
- P95/P99 使用服务端直方图 bucket 和 `histogram_quantile` 聚合，因此被观察应用必须暴露 `http_server_requests_seconds_bucket`。
- 所有指标使用同一个 `observationTime` 查询；提供历史故障时间时，可读取 Prometheus 保留期内该时刻的证据。
- `UP` 表示抓取和核心 HTTP 指标正常，`DOWN` 表示 `up=0`，`DEGRADED` 表示可抓取但缺少 HTTP 指标，`NO_DATA` 表示未找到目标序列，`UNAVAILABLE` 表示 Prometheus 本身不可访问。
- API URL、PromQL 和底层错误正文不会进入模型上下文；失败时只返回集中定义的安全异常消息。

## RAG 运行知识边界

- Embedding 模型在配置中固定为普通 `BAAI/bge-m3`，没有使用 `Pro/BAAI/bge-m3`；向量维度为 1024。
- Qdrant 由本地 Docker Compose 运行，REST 和 gRPC 端口只绑定 `127.0.0.1`，数据保存在命名卷中。
- 只有白名单 Markdown 路径会被读取，文件数量、单文件大小、查询长度、分块数量、Top K 和返回片段长度均有上限。
- 面试题和 question-bank 文档默认排除；发送给模型的来源仅为知识根目录下的相对路径，不暴露宿主机绝对路径。
- 每个分块使用确定性 UUID，并带有 `system_name`、`source`、`index_version` 和 `chunk_index` 元数据。
- 检索必须按 `system_name` 过滤，知识 Tool 不接受任意 Qdrant 过滤器或数据库查询表达式。
- Agent 将知识片段视为待核对资料而不是系统指令，忽略文档中试图改变角色、绕过规则或诱导 Tool 调用的内容。
- 知识检索结果是设计说明、故障经验和 Runbook，不代表目标当前状态；诊断结论应与 Metrics、Log 和基础设施 Tool 的实时证据分别陈述。
- 知识源、Embedding 或 Qdrant 不可用时返回安全的 `UNAVAILABLE` 证据；启动同步失败只降低 RAG 能力，不泄露密钥、URL、绝对路径或底层异常。

## 受控诊断工作流边界

- 工作流状态依次覆盖规划、证据收集、结论归纳以及完成、失败、超时、Tool 超限等终态。
- 状态以诊断 ID 隔离并存放在并发 Map 中，不使用 `ThreadLocal`，因此流式线程切换和并发请求不会互相污染。
- Tool 次数在访问外部服务前原子预占；达到上限后的调用会被拒绝，不会先访问 Redis、MySQL、RabbitMQ、Loki、Prometheus 或 Qdrant。
- 总超时覆盖模型规划、全部 Tool 取证和最终报告生成，不等同于各基础设施客户端自己的连接或读取超时。
- 超时和 Tool 超限通过 SSE `ERROR` 事件返回集中定义的安全消息，不向客户端暴露模型供应商异常或内部调用栈。
- 终止事件包含 `toolCallCount`、`maxToolCalls` 和 `elapsedMs`，便于 Stage 7 Evaluation 统计调用效率和诊断耗时。
- 状态机只规定诊断阶段和资源边界，不建立“某种故障必须调用某组 Tool”的硬编码映射；具体 Tool 与顺序仍由模型基于证据决定。
- 请求结束或客户端断开后立即清理内存状态；当前不提供跨请求恢复和持久化 Checkpoint。

## 阶段进度

| 阶段 | 状态 | 内容 |
| --- | --- | --- |
| Stage 1A | 已完成 | 基础工程、真实模型调用和诊断 API |
| Stage 1B | 已完成 | Fake Tool、多步 Tool Calling 和调用轨迹 |
| Stage 2A | 已完成 | 接入真实 Redis 和 MySQL 只读 Tool |
| Stage 2B | 已完成 | 接入 RabbitMQ Management API 和安全配置查询 Tool |
| Stage 3 | 已完成 | Alloy 日志采集、Loki 受控查询和自适应时间窗口 |
| Stage 4 | 已完成 | Prometheus 指标采集、固定 PromQL 与真实指标查询 Tool |
| Stage 5 | 已完成 | 硅基流动 Embedding、本地 Qdrant、版本化知识同步与受控 RAG Tool |
| Stage 6 | 已完成 | 请求级诊断状态机、SSE 状态、总超时和原子 Tool 调用预算 |
| Stage 7 | 待实现 | 故障场景与 Evaluation |

## 当前边界

- Metrics、Log、Redis、MySQL 和 RabbitMQ 均已接入真实只读数据源；Fake Tool 仅保留用于回归测试。
- REST API 与 JavaFX 客户端均已使用流式诊断；JavaFX 会逐段追加模型回答。
- 尚未实现会话记忆、持久化诊断状态和跨请求 Checkpoint；Stage 6 状态只在单次请求生命周期内存在。
- RootSight 只提供读取、分析和建议，不执行具有副作用的运维操作。
