# 画像格式兼容修复 v2.1

交付核查日期：2026-10-16（Asia/Shanghai）。修复及测试产物生成于 2026-10-15，包名保留原日期。

已确认：使用真实 DeepSeek、纯合成聊天和长消息 ID 复现 model_output_invalid。摘要 533 字，返回 JSON 可解析；selected_class 为 {"value":null,"evidence_message_ids":[]}，旧验证器只接受直接 null，所以拒绝整个画像。

已修复：无值且无证据的对象规范化为未知；不丢弃其他有效字段。非空值依然必须具有真实输入消息 ID；null 却声称有证据仍拒绝。画像校验错误在失败任务 result.diagnostic 中保存固定标签，不保存模型原文、字段值或聊天正文。

明确边界：上述是本机确认的同类缺陷；当前服务器旧失败任务没有细分原因，不能证明截图中客户的失败恰好由该字段导致。未扩大读取其他客户聊天，未自动重试真实客户生成。未改变生成预算、模型、密钥、上游同步与画像事实纪律。

验证：63 项测试，0 failures、0 errors；原来失败的合成模型输出在新验证器回放通过，原未知项为 null、其他有效事实保留；本机容器导入和校验通过，安装脚本 bash -n 通过。证据 docs/evidence/profile-format-v2.1/。不等于目标服务器已更新或真机已验收。

产物：private/sasalele-business-v2.1-20261015.tar.gz，59971 字节。
SHA-256：1445d2337bd06d4e1411f39607efe245008e297af5e4fe3c7712cb2c5912f6a0。
镜像：sasalele-sales-api:20261015-business-v2.1；提示词版本：sales-business-v2.1。

上线：通过 Workbench 上传到 /home/admin/sasalele-upload，校验后解压到独立 /home/admin/sasalele-business-v2.1-20261015，执行 sudo bash install.sh。脚本复用已有模型密文，保留设备授权和数据卷，备份 SQLite，只升级 API。启动失败尝试恢复旧镜像。无需重新安装 APK。

后续：先核对服务器提示词版本、健康和已有代理状态，再安排当前绑定客户单次刷新。若再次失败，读取该次任务固定诊断标签后处理；不反复要求用户盲目重试。历史失败提示不会因服务器升级自动消失，只有新的任务结果才能验证修复。
