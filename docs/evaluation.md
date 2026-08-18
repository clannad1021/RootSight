# Evaluation 指南

RootSight 的 Evaluation 用于回答三个问题：

1. Agent 是否定位到了预期根因？
2. Agent 是否选择了必要且相关的 Tool？
3. Agent 是否在允许时间内完成诊断？

评测复用真实 `DiagnosisService`、真实系统提示词和真实 Tool Calling，不会为获得高分而替换成固定调用脚本。

## 1. 启用评测入口

Evaluation 默认关闭，因为批量场景会产生真实模型调用和外部查询。

```powershell
$env:ROOTSIGHT_EVALUATION_ENABLED="true"
.\mvnw.cmd spring-boot:run
```

接口：

```text
POST http://127.0.0.1:8081/api/evaluations
Content-Type: application/json
```

## 2. 运行示例

```powershell
.\evaluation\run-evaluation.ps1
```

默认读取 `evaluation/scenarios.example.json`，并将报告写入：

```text
evaluation/evaluation-report.json
```

本地报告已被 Git 忽略。

也可以指定地址、场景和输出文件：

```powershell
.\evaluation\run-evaluation.ps1 `
  -BaseUrl "http://127.0.0.1:8081" `
  -ScenarioFile ".\evaluation\scenarios.example.json" `
  -OutputFile ".\evaluation\evaluation-report-local.json"
```

## 3. 场景结构

```json
{
  "id": "redis-unavailable",
  "name": "Redis 不可连接",
  "question": "当前被观测系统访问 Redis 失败，请定位根因并给出处置建议。",
  "requiredTools": [
    "inspect_redis_status"
  ],
  "allowedTools": [
    "query_application_logs",
    "query_service_http_metrics"
  ],
  "rootCauseKeywordGroups": [
    ["Redis"],
    ["DOWN", "不可用", "无法连接", "连接失败"]
  ],
  "maxDurationMs": 90000,
  "minToolF1": 0.75
}
```

字段含义：

| 字段 | 含义 |
| --- | --- |
| `id` | 场景唯一标识，同一批次不能重复 |
| `name` | 报告中的可读名称 |
| `question` | 原样发送给诊断 Agent 的问题 |
| `requiredTools` | 正确定位该场景必须调用的 Tool |
| `allowedTools` | 与场景相关、可以接受的补充 Tool |
| `rootCauseKeywordGroups` | 根因词组；组内是“或”，组间是“且” |
| `maxDurationMs` | 场景允许的最大诊断耗时 |
| `minToolF1` | 场景通过所需的最低 Tool F1 |

必需 Tool 会自动加入允许集合，不需要在 `allowedTools` 中重复声明。

## 4. 评分规则

### 根因定位

每个 `rootCauseKeywordGroups` 分组至少命中一个关键词，并且所有分组都命中时，`rootCauseLocated=true`。

例如：

```json
[
  ["Redis"],
  ["DOWN", "不可用", "连接失败"]
]
```

要求回答同时出现“Redis”和任意一个不可用同义词。

该规则透明、确定、易于回归，但它是词组匹配，不等同于专家语义评分。复杂场景应配合人工抽样复核。

### Tool Precision

```text
Precision = 实际调用且属于允许集合的 Tool 数 / 实际调用 Tool 总数
```

调用无关 Tool 会降低 Precision。

### Tool Recall

```text
Recall = 已调用的必需 Tool 数 / 必需 Tool 总数
```

遗漏关键证据 Tool 会降低 Recall。

### Tool F1

```text
F1 = 2 × Precision × Recall / (Precision + Recall)
```

报告中的 `toolSelectionAccuracy` 是全部场景 F1 的平均值。

### 场景通过

场景同时满足以下条件才通过：

- 工作流终态为 `COMPLETED`
- 根因关键词全部命中
- Tool F1 不低于阈值
- 实际耗时不超过阈值

## 5. 报告字段

聚合指标：

- `totalScenarios`
- `passedScenarios`
- `passRate`
- `rootCauseAccuracy`
- `toolSelectionAccuracy`
- `averageDurationMs`

场景明细还会列出：

- 实际选择的 Tool
- 缺失的必需 Tool
- 非预期 Tool
- 工作流终态
- 诊断正文
- 安全终止消息

## 6. 故障准备

故障应在隔离测试环境制造，Evaluation 本身不会关闭服务或修改配置。

### Redis 不可连接

可以只覆盖本次 RootSight 进程的 Redis 地址或端口，使 Tool 得到真实连接失败，同时不停止共享 Redis。

### MySQL 连接异常

可以使用隔离实例、测试账号或仅对本次 RootSight 进程配置不可访问地址。不要在共享环境撤销业务账号权限。

### RabbitMQ 消费者停止

使用测试 vhost 和测试队列：

1. 启动测试 producer/consumer。
2. 产生可识别的积压消息。
3. 停止测试 consumer，保留队列和积压。
4. 确认 monitoring 账号能看到 `consumers=0` 和队列深度。
5. 运行场景，结束后恢复测试 consumer。

## 7. 如何看待结果

- 模型、提示词、Tool 描述、目标版本或场景文件变化后，应重新建立基线。
- 单场景通过不能代表整体准确率，应保留正常、异常、证据不足和多故障并存场景。
- 高 Recall、低 Precision 往往意味着模型“把所有 Tool 都查一遍”；高 Precision、低 Recall 则意味着遗漏关键证据。
- 根因正确但耗时过长，通常需要检查无关 Tool、重复取证或外部查询超时。
- Tool F1 高但根因错误，说明证据选择正确，推理或报告归纳仍需改进。

## 8. 当前验收记录

Stage 7 完成时使用进程级无效 Redis 端口进行了一次隔离验收，没有停止真实 Redis 容器：

| 指标 | 结果 |
| --- | --- |
| 场景数 | 1 |
| 场景通过率 | 100% |
| 根因定位准确率 | 100% |
| Tool F1 | 0.8333 |
| 诊断耗时 | 38.642 秒 |
| 工作流终态 | `COMPLETED` |

该记录用于验证真实 API、Tool Calling、超时控制和评分链路已经贯通，不应当作统计意义上的模型 Benchmark。
