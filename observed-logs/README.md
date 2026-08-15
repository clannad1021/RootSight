# Observed logs

将需要被 RootSight 查询的目标应用日志写入或复制到此目录。

支持 `.log`、`.txt` 和 `.json` 文件。实际日志文件已被 `.gitignore` 排除，不应提交到 Git；Alloy 只读挂载此目录并为日志添加 `service_name` 标签。

默认标签值为 `observed-target`，可在启动 Compose 前通过 `ROOTSIGHT_OBSERVED_SERVICE` 环境变量覆盖。
