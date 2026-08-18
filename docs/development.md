# 开发与调试指南

本文面向希望阅读、调试或继续扩展 RootSight 的开发者。项目概览与快速运行方式请先看 [项目 README](../README.md)。

## 1. 开发环境

- JDK 17
- Windows PowerShell（当前项目主要开发环境）
- Docker Desktop / Docker Compose
- IDEA 或其他支持 Spring Boot、Lombok 和 JavaFX 的 IDE

检查 Maven 实际使用的 JVM：

```powershell
.\mvnw.cmd -version
```

## 2. 常用命令

```powershell
# 编译
.\mvnw.cmd compile

# 全量测试
.\mvnw.cmd test

# 打包
.\mvnw.cmd -DskipTests package

# 启动 Web 后端
.\mvnw.cmd spring-boot:run

# 启动 JavaFX 客户端
.\mvnw.cmd javafx:run
```

## 3. 入口类

| 入口 | 类 | 用途 |
| --- | --- | --- |
| Web 后端 | `kg.edu.nagisa.rootsight.RootSightApplication` | REST、SSE、Tool、RAG、Evaluation |
| 桌面客户端 | `kg.edu.nagisa.rootsight.desktop.RootSightDesktopLauncher` | JavaFX UI |

IDEA 启动桌面端时不要选择 Web 后端入口，否则只会启动 `8081` 服务，不会显示窗口。

## 4. 阅读代码的建议顺序

1. `AiConfiguration`：系统行为规范和可用 Tool。
2. `DiagnosisController`：SSE API 入口。
3. `DiagnosisService`：模型调用、内容流、超时和终态。
4. `DiagnosisWorkflowCoordinator`：请求级状态机和原子预算。
5. `ToolCallTraceRecorder`：诊断 ID 与 Tool 轨迹隔离。
6. `tool/infrastructure`：模型看到的 Tool 方法。
7. `infrastructure`：真实外部访问和安全失败转换。
8. `knowledge`：RAG 索引与检索。
9. `evaluation`：场景评分和报告聚合。

架构说明见 [架构设计](architecture.md)。

## 5. 新增 Tool 的约束

新增 Tool 时应同时满足：

- 默认只读，不执行具有副作用的操作。
- 参数是业务语义字段，不接受任意 SQL、PromQL、LogQL 或配置键。
- 在外部访问前调用工作流预算检查。
- 使用 `ToolContext` 中的诊断 ID 记录轨迹，不使用 `ThreadLocal`。
- 返回结构化 Evidence，并包含明确证据来源。
- 对返回数量、文本长度、时间窗口和分页设置上限。
- 连接失败转换为安全消息，不把凭证、URL、堆栈或供应商响应发给模型。
- 为正常、空结果、连接失败和越界参数补充测试。

## 6. 配置与密钥

真实密钥只放在环境变量中：

```powershell
$env:DEEPSEEK_API_KEY="..."
$env:SILICONFLOW_API_KEY="..."
```

不要把以下内容写入日志、文档示例值或测试快照：

- API Key
- Redis、MySQL、RabbitMQ 密码
- Authorization Header
- 带凭证的连接 URL
- 本地 `.env` 内容

## 7. 测试边界

单元测试应使用 Mock、Fake Tool 或受控结构化结果，不依赖开发者机器上的真实 Redis、MySQL 和 RabbitMQ。

真实验收应与自动化测试分开：

- 使用隔离端口、测试 vhost、测试账号或独立进程配置。
- 不停止共享中间件或 IDE 正在运行的业务进程。
- 验收结束后停止 RootSight 临时实例并确认目标服务仍然运行。
- 不把本地 Evaluation 报告、日志或凭证提交 Git。

## 8. 文档同步

功能变化后至少检查：

- 根目录 `README.md`
- `docs/architecture.md`
- `docs/deployment.md`
- `docs/evaluation.md`
- `docs/development.md`
- `observed-logs/README.md`
- `src/main/resources/application.yml` 中的配置注释

文档只描述当前已经实现并验证的能力。计划能力应明确标记为边界或后续方向，不能写成现状。
