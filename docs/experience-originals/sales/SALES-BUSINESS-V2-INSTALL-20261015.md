# 业务提示词 v2 实装交付

日期：2026-10-15，Asia/Shanghai，开发机记录。源码、部署包、本机容器验证完成；线上安装等待 Workbench 上传执行，未宣称线上成功。

润色：已有画像时携带缓存画像用于语气和沟通重点；没有画像时正常处理原文，不调用方舟、不触发画像模型。两条路径均有自动测试。仍需工作机授权、已确认客户和可用模型，不等于离线模型能力。

新增将提示词版本纳入任务幂等摘要，避免同一请求 ID 在更新后复用旧提示词的成功结果。

验证：57 项自动测试通过；Docker 镜像 sasalele-sales-api:20261015-business-v2 离线构建成功；容器内实际导入 PROMPT_VERSION=sales-business-v2；安装脚本 bash -n 通过。未在目标 Ubuntu 执行脚本；目标服务器密钥解密、升级、回退、模型实际调用及真机尚未验收。脚本复用既有密文，不要求再次提供密钥。包内容扫描未发现本机现有模型密钥明文；不包含私有聊天数据库。

脚本会先解密已有 DeepSeek 配置、离线构建镜像、对当前 API 的 SQLite 用 backup API 备份到持久卷 backups 子目录，再只更新 API。更新启动失败时尝试恢复原镜像；回退失败会报错停止。不会重启 edge 或 sparql-proxy。单库备份一致，不宣称多库事务原子快照。线上配置读取和 HTTPS 检查失败会返回错误，仍需检查日志，不代表完整业务验收。

部署包：`sasalele-business-v2-20261015.tar.gz`
SHA-256：`b313a8e4a4e98963b2e0a34a7bb09c8ac5dad7ffcbff194a9515b6cfd263e255`
大小：59684 字节。

## Workbench 操作

必须先将该包上传到 `/home/admin/sasalele-upload`，校验成功才执行安装，防止错包。操作者为你，设备为服务器 Workbench 终端，账号 admin；不是在手机运行。

```bash
cd /home/admin/sasalele-upload
printf '%s\n' 'b313a8e4a4e98963b2e0a34a7bb09c8ac5dad7ffcbff194a9515b6cfd263e255  sasalele-business-v2-20261015.tar.gz' | sha256sum -c - &&
mkdir -p /home/admin/sasalele-business-v2-20261015 &&
tar -xzf sasalele-business-v2-20261015.tar.gz -C /home/admin/sasalele-business-v2-20261015 &&
sudo bash /home/admin/sasalele-business-v2-20261015/install.sh
```

输出应包含容器健康、Prompt: sales-business-v2、model_key_configured: True、HTTPS alive、既有代理 active。不得贴 upstream.env、密钥、设备配置或授权值；上述状态输出即可。状态通过后再验证实际生成及工作机；尚不要求用户操作客户对话。

当前没有新建 APK：这是服务端提示词实装。既有 v11 APK 生成能力依赖后端更新，当前工作机安装版本需后续核对。三套提示词及语义评估见 SALES-BUSINESS-PROMPTS-V2-20261015.md 和 SALES-BUSINESS-V2-REPORT-20261015.md。
