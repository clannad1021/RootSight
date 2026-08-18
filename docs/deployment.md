# 部署与目标接入

本文说明如何在本地或测试环境启动 RootSight，并将一个 Spring Boot 或其他可观测应用接入日志、指标和基础设施诊断。

## 1. 端口规划

| 组件 | 默认端口 | 用途 |
| --- | --- | --- |
| RootSight | `8081` | 诊断 API、Evaluation API、Actuator |
| Loki | `3100` | 日志查询 API |
| Alloy | `12345` | Alloy 管理接口 |
| Prometheus | `9090` | 指标查询与目标状态 |
| Qdrant REST | `6333` | 健康检查和管理 |
| Qdrant gRPC | `6334` | Spring AI VectorStore |

## 2. 必需环境变量

```powershell
$env:DEEPSEEK_API_KEY="your-deepseek-api-key"
$env:SILICONFLOW_API_KEY="your-siliconflow-api-key"
```

默认模型分别为：

- Chat：`deepseek-v4-flash`
- Embedding：普通 `BAAI/bge-m3`

硅基流动基址必须包含 `/v1`，默认值已经配置为 `https://api.siliconflow.cn/v1`。

## 3. 启动 Loki、Alloy、Prometheus 和 Qdrant

```powershell
Copy-Item observability/.env.example observability/.env
```

编辑本地 `observability/.env`：

```dotenv
ROOTSIGHT_OBSERVED_LOG_PATH=D:/path/to/application/logs
ROOTSIGHT_OBSERVED_SERVICE=order-service
```

`observability/.env` 已被 Git 忽略。日志目录以只读方式挂载到 Alloy 容器。

```powershell
docker compose --env-file observability/.env `
  -f observability/docker-compose.yml up -d
```

检查组件：

```powershell
docker compose --env-file observability/.env `
  -f observability/docker-compose.yml ps

Invoke-WebRequest http://127.0.0.1:3100/ready
Invoke-WebRequest http://127.0.0.1:9090/-/ready
Invoke-RestMethod http://127.0.0.1:6333/
```

## 4. 接入日志

目标应用需要将 `.log`、`.txt` 或 `.json` 文件写入 `ROOTSIGHT_OBSERVED_LOG_PATH`。Alloy 会添加：

```text
service_name=<ROOTSIGHT_OBSERVED_SERVICE>
```

RootSight 查询侧必须使用相同值：

```powershell
$env:ROOTSIGHT_LOKI_DEFAULT_SERVICE="order-service"
```

若不希望挂载业务目录，也可以把测试日志放入仓库的 `observed-logs/`，但真实日志文件不应提交 Git。

## 5. 接入 Prometheus 指标

被观察应用需要暴露 Prometheus 格式指标。Spring Boot 项目通常需要 Actuator 和 Prometheus Registry，并确保：

```text
GET /actuator/prometheus
```

返回 HTTP 200 和 Prometheus 文本。

在 `observability/prometheus/targets/` 中新增或修改 `file_sd` 目标，例如：

```json
[
  {
    "targets": ["host.docker.internal:8080"],
    "labels": {
      "application": "order-service",
      "environment": "local"
    }
  }
]
```

同步 RootSight 查询标签：

```powershell
$env:ROOTSIGHT_PROMETHEUS_DEFAULT_SERVICE="order-service"
```

检查抓取状态：

```powershell
Invoke-RestMethod http://127.0.0.1:9090/api/v1/targets
```

P95/P99 依赖 `http_server_requests_seconds_bucket`。如果目标只暴露 count/sum 而没有 histogram bucket，RootSight 会把缺失项作为证据缺口返回，而不是编造延迟值。

## 6. 接入 Redis

```powershell
$env:ROOTSIGHT_REDIS_HOST="127.0.0.1"
$env:ROOTSIGHT_REDIS_PORT="6379"
$env:ROOTSIGHT_REDIS_USERNAME="rootsight"
$env:ROOTSIGHT_REDIS_PASSWORD="your-password"
$env:ROOTSIGHT_REDIS_DATABASE="2"
```

账号只需要诊断所需的最小权限：连接、选择指定数据库、`PING`，以及可选的 `INFO`。RootSight 不扫描 Key，也不执行写命令。

如果账号只有 `PING` 权限，Tool 仍可以判断连通性，并通过 `metricsAvailable=false` 表示 INFO 指标不可用。

## 7. 接入 MySQL

```powershell
$env:ROOTSIGHT_DB_URL="jdbc:mysql://127.0.0.1:3306/"
$env:ROOTSIGHT_DB_USERNAME="readonly"
$env:ROOTSIGHT_DB_PASSWORD="your-password"
```

MySQL Tool 只运行源码中固定的实例与 `SHOW GLOBAL STATUS` 查询。建议使用独立只读账号；Hikari 连接池本身也启用了只读模式。

## 8. 接入 RabbitMQ

```powershell
$env:ROOTSIGHT_RABBITMQ_MANAGEMENT_URL="http://127.0.0.1:15672"
$env:ROOTSIGHT_RABBITMQ_USERNAME="rootsight_monitor"
$env:ROOTSIGHT_RABBITMQ_PASSWORD="your-password"
$env:ROOTSIGHT_RABBITMQ_VHOST="/"
```

账号需要目标 vhost 的 monitoring 权限。RootSight 只调用 Management HTTP API，不建立 AMQP 连接，不读取消息正文，也不发布或消费消息。

## 9. 配置运行知识

```powershell
$env:ROOTSIGHT_KNOWLEDGE_SOURCE_ROOT="D:/order-service"
$env:ROOTSIGHT_KNOWLEDGE_SYSTEM_NAME="order-service"
```

默认索引：

- 根目录 `README.md`
- `docs/*.md`
- `runbooks/*.md`

默认排除面试题和 question bank。首次启动完成分块、Embedding 和 Qdrant 写入；相同内容再次启动返回 `UP_TO_DATE`。

不需要 RAG 时可以关闭：

```powershell
$env:ROOTSIGHT_KNOWLEDGE_ENABLED="false"
$env:ROOTSIGHT_KNOWLEDGE_AUTO_INDEX="false"
```

## 10. 关键运行参数

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `ROOTSIGHT_SERVER_PORT` | `8081` | RootSight 端口 |
| `ROOTSIGHT_TARGET_NAME` | `default-target` | 当前目标逻辑名称 |
| `ROOTSIGHT_DIAGNOSIS_TIMEOUT` | `90s` | 单次诊断总时限 |
| `ROOTSIGHT_DIAGNOSIS_MAX_TOOL_CALLS` | `8` | 单次 Tool 预算 |
| `ROOTSIGHT_LOKI_DEFAULT_SERVICE` | `observed-target` | Loki 默认服务标签值 |
| `ROOTSIGHT_PROMETHEUS_DEFAULT_SERVICE` | `observed-target` | Prometheus 默认 application 标签值 |
| `ROOTSIGHT_QDRANT_COLLECTION` | `rootsight_knowledge` | Qdrant 集合名 |
| `ROOTSIGHT_KNOWLEDGE_ENABLED` | `true` | 是否启用知识检索 |
| `ROOTSIGHT_KNOWLEDGE_AUTO_INDEX` | `true` | 启动后是否同步知识 |
| `ROOTSIGHT_EVALUATION_ENABLED` | `false` | 是否注册批量评测 API |

其余查询窗口、结果上限、连接超时和 Evaluation 上限均可在 `src/main/resources/application.yml` 中查看并通过同名环境变量覆盖。

## 11. 启动与停止

启动后端：

```powershell
.\mvnw.cmd spring-boot:run
```

启动桌面端：

```powershell
.\mvnw.cmd javafx:run
```

停止本地观测组件：

```powershell
docker compose --env-file observability/.env `
  -f observability/docker-compose.yml down
```

不添加 `-v` 时，Loki、Prometheus、Alloy 和 Qdrant 的命名卷数据会保留。

## 12. 常见检查

### RootSight 启动失败

- 确认 JDK 为 17。
- 确认两个 API Key 已进入当前终端，而不只是写入了尚未重新加载的系统环境变量。
- 检查 `8081` 是否被占用。

### Loki 查不到日志

- 检查 Alloy 的宿主日志路径是否正确。
- 检查文件扩展名是否为 `.log`、`.txt` 或 `.json`。
- 确认 `ROOTSIGHT_OBSERVED_SERVICE` 与 `ROOTSIGHT_LOKI_DEFAULT_SERVICE` 一致。

### Prometheus 返回 NO_DATA

- 直接访问目标 `/actuator/prometheus`。
- 查看 `/api/v1/targets` 中 scrape 错误。
- 确认目标的 `application` 标签与 RootSight 配置一致。

### 目标中间件宕机但 RootSight health 仍为 UP

这是预期行为。中间件是诊断对象，不是 RootSight 自身健康依赖；对应 Tool 会返回 DOWN 证据供 Agent 分析。
