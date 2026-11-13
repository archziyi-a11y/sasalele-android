# v35 工作机发布（2026-11-03）

用户授权向每台手机发布。当前方舟受管设备17台，16台在线。使用已构建正式签名 v35，SHA-256 eabe06e4722f33fa2f02584d282a5f979b66feab0365f7385b5d0e32be596d13。

约束：覆盖更新，不卸载或清除数据，不升级方舟工作端。核对原始上传和转换下载的完整哈希后，按冻结清单逐台提交在线设备并保存回执。离线跳过，结果未知不重复提交。平台旧名称安装记录不计为新版本验收。逐台私有记录：private/fangzhou-release-v35-20261103/。时间采用执行端UTC转换Asia/Shanghai，仅为提交/观测时间。

## 下发结果
最后观测 2026-11-03T15:25:34.480238+08:00。16台在线工作机均收到一次安装指令（code=0受理），[人员7]离线跳过。
APK原始下载及平台转换下载完整SHA-256均通过；首次上传代理TLS连接中断，HEAD确认对象不存在后使用直连上传，未关闭TLS校验。应用更新请求发生连接异常后先回读再继续，无重复安装提交。
平台安装列表仍保留旧应用名或未返回对应条目，不提供实际版本号。本轮记录为已推送、版本待确认，不宣称16台全部升级成功。

|设备|提交结果|平台当前记录|版本确认|
|---|---|---|---|
|[人员2]（用户已确认，平台未备注） / [设备尾号已脱敏]|accepted_not_verified|SASALELE v30 · 安装完成|待确认|
|管理员(未备注) / 0000|accepted_not_verified|SASALELE v30 · 安装完成|待确认|
|管理员(未备注) / [设备尾号已脱敏]|accepted_not_verified|SASALELE v30 · 安装完成|待确认|
|管理员(未备注) / 0000|accepted_not_verified|SASALELE v30 · 安装完成|待确认|
|[人员8] / [设备尾号已脱敏]|accepted_not_verified|SASALELE v30 · 安装完成|待确认|
|[人员9] / [设备尾号已脱敏]|accepted_not_verified|SASALELE v30 · 安装完成|待确认|
|[人员10] / [设备尾号已脱敏]|accepted_not_verified|SASALELE v30 · 安装完成|待确认|
|[人员1] / 0000|accepted_not_verified|SASALELE v30 · 安装超时|待确认|
|[人员2]备用号 / [设备尾号已脱敏]|accepted_not_verified|SASALELE v30 · 安装超时|待确认|
|[人员3] / 0000|accepted_not_verified|SASALELE v30 · 安装完成|待确认|
|[人员7] / [设备尾号已脱敏]|skipped_offline|— · 未返回|待确认|
|[人员4] / [设备尾号已脱敏]|accepted_not_verified|SASALELE v30 · 安装完成|待确认|
|[人员11] / 0000|accepted_not_verified|SASALELE v30 · 安装完成|待确认|
|[人员5] / [设备尾号已脱敏]|accepted_not_verified|SASALELE v30 · 安装完成|待确认|
|[人员6] / 0000|accepted_not_verified|SASALELE v30 · 安装超时|待确认|
|[人员12] / 0000|accepted_not_verified|SASALELE v30 · 安装完成|待确认|
|[人员2] / 0000|accepted_not_verified|SASALELE v30 · 安装超时|待确认|
