# 编辑补充问题导致指导消失：修复记录

2026-10-16，Asia/Shanghai。用户报告修改销售指导补充问题即丢失已有结果。

原因：BusinessGenerationPanel把draft、editedDraft、feedback的remember键包含latest/original，修改输入触发状态重置；点击生成也立即draft=null。

修复：结果和手动编辑内容仅随客户/功能类型隔离，不随输入重置；记录成功结果对应的输入快照。新输入与快照不同时显示“输入已修改，以下结果对应上一次输入；重新生成成功后才会替换。”提交新任务仍保留原结果，失败、停止等待或超时不会清空，成功后才替换。输入变更仍取消客户端旧等待，并通过RequestEpoch校验防止过时异步返回覆盖新状态。服务器任务取消语义未变。

同一通用组件的帮我回、润色也获益；切客户/类型仍清除结果，避免串客户。此修复是当前面板内保留，不新增跨关闭、进程退出或重启的指导历史持久化。

验证：assembleRelease/assembleDebug/assembleDebugAndroidTest及lintVitalRelease通过；模拟器合成HTTP用例1/1通过，覆盖生成初次指导、手工编辑、修改补充输入、失败重生成保留、新成功替换及旧输入标识消失。未调用方舟或真实模型，测试服务只监听localhost，已停止。请求过时保护实现经代码检查，未声称覆盖所有竞态。

正式签名APK：D:/Dev/AndroidProjects/SASALELE/deliverables/SASALELE-1.3.1-guidance-v15.apk
版本15 / 1.3.1-guidance；SHA-256：54284e9d0346f37313c028d9c365bef4b823f8af64de97535254b9765d0a7f79。
签名沿用既有正式证书。已在当前PKW110覆盖安装Success并启动Status ok，保留本地配置和服务端数据。无需服务端部署；尚未让用户为本修复重复生成真实客户指导。

证据：docs/evidence/guidance-state-20261016/；构建日志private/guidance-build-v15-20261016.log。未将模拟器验证冒充真机端到端验收。
