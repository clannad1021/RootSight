# 被观察应用日志目录

该目录用于本地演示和测试：将需要被 RootSight 查询的目标应用日志写入或复制到这里，Alloy 会以只读方式采集并发送到 Loki。

## 支持的文件

- `*.log`
- `*.txt`
- `*.json`

实际日志文件已经被 `.gitignore` 排除，不应提交到 Git。目录中只保留本说明文件。

## 使用默认目录

在 `observability/.env` 中配置：

```dotenv
ROOTSIGHT_OBSERVED_LOG_PATH=../observed-logs
ROOTSIGHT_OBSERVED_SERVICE=observed-target
```

然后启动：

```powershell
docker compose --env-file observability/.env `
  -f observability/docker-compose.yml up -d
```

Alloy 会为日志添加：

```text
service_name=observed-target
```

RootSight 的查询配置必须保持一致：

```powershell
$env:ROOTSIGHT_LOKI_DEFAULT_SERVICE="observed-target"
```

## 直接采集业务日志目录

更常见的方式是不复制日志，而是直接将业务应用日志目录只读挂载给 Alloy：

```dotenv
ROOTSIGHT_OBSERVED_LOG_PATH=D:/order-service/logs
ROOTSIGHT_OBSERVED_SERVICE=order-service
```

Windows 路径建议使用正斜杠。修改 `.env` 后需要重新创建 Alloy 容器：

```powershell
docker compose --env-file observability/.env `
  -f observability/docker-compose.yml up -d --force-recreate alloy
```

## 查不到日志时

1. 确认文件扩展名在支持范围内。
2. 确认 Docker Desktop 有权读取宿主目录。
3. 确认 Alloy 容器中的挂载路径为 `/var/log/observed`。
4. 确认 `ROOTSIGHT_OBSERVED_SERVICE` 与 `ROOTSIGHT_LOKI_DEFAULT_SERVICE` 一致。
5. 查看 Alloy 日志和 Loki 就绪状态：

```powershell
docker compose --env-file observability/.env `
  -f observability/docker-compose.yml logs alloy

Invoke-WebRequest http://127.0.0.1:3100/ready
```

RootSight 会对进入模型上下文的日志执行长度限制和常见凭证脱敏，但业务应用仍不应主动把密码、Token 或完整密钥写入日志。
