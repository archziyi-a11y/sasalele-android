# SASALELE 首批销售标准实施记录（2026-10-13）

## 当前结论

已把讨论落为执行计划并完成不依赖实际服务器资源的源码、部署包、管理员发机工具与本地验证。用户本轮明确“资源先跳过，提供填写格式”，因此真实服务器/域名/正式签名落实与Android15真机上线验收仍待继续；不能宣称已经达到可上线验收通过。

产品目标不变：销售只使用工作手机，WiFi/移动网络连接项目稳定业务服务，无人员登录、不连接开发电脑、不手填IP/开发访问码。管理员逐台安装并首次配置独立工作机凭证。实际模型、自动画像及上游写入仍不在本轮。

基线：D:\Dev\backups\SASALELE-production-20261013，保存Git HEAD/状态/差异、Android副本及业务服务源码；保留已有图标和用户修改，不改旧探针Android源码、自动任务或真实客户数据库。没有提交Git。

## 已实现

1. 计划：PRODUCTION-EXECUTION-PLAN-20261013.md逐项列动作、证据与待资源状态。
2. 正式服务模式：sales_http支持production独立工作机访问凭证；不再接受旧LAN共享码。SQLite保存高熵凭证的SHA-256摘要，默认90天，可按机撤销；重启保留，其他工作机不受某台撤销影响。没有员工账号系统，未宣称硬件防复制或硬件身份认证。
3. 发机管理：manage_work_devices.py签发独立私有JSON文件，只输出文件路径、设备编号和有效期，不输出token。文件不进Git；管理员安全传输并在APP内选择文件，发机前必须在线检查通过，验证后删除传输副本。权限随工作机交接，管理员在到期前更新。
4. 部署包：Docker非root API、只读容器文件系统、持久缓存卷、自动重启策略、进程健康检查，Caddy HTTPS代理且API不直接映射公网端口。构建上下文用Dockerfile专用忽略文件仅允许必要源码，避免凭据入镜像或影响其他项目Docker构建。实际测试依赖锁定requirements.lock，Caddy固定本次验证digest。
5. 观测：服务生成X-Request-ID，日志只写编号、固定路由、状态、耗时、+08:00观察时间，限大小轮转。不记录客户姓名、聊天正文、凭证/请求体。422验证错误不回显用户输入。/_health/live只证明进程存活，授权/sales/health再核对上游工作号范围；不能把存活写成业务可用。
6. Android：正式HTTPS域名预置构建配置，工作机配置用Android Keystore AES-GCM加密、AtomicFile原子保存至noBackup目录。首次导入/清除会更新界面与查询状态。日常页面没有IP/访问码字段；管理员发机入口用于一次导入。旧局域网输入只在debug“开发诊断（非日常使用）”内，需显式启用，无静默回退。
7. 网络反馈：配置未完成、手机离线、DNS、超时、TLS、服务不可达、设备撤销/到期、上游故障、服务繁忙分开提示；有响应的故障带查询编号。保留系统TLS校验，不跟随重定向。只读查询瞬时I/O失败最多重试一次、间隔250ms，绑定确认不自动重试；取消会取消OkHttp请求，旧响应不能覆盖新目标。
8. OCR：排除〈7、<12、返回、导航数字/时间/网络标记等，选择后面的实际名称。该截图问题用合成顶栏复现与验证，不读取、复制或保存附件聊天正文。不能由此证明所有实际聊天UI分类/OCR都可靠。
9. 发布检查：版本号递增为2，显示版本1.1-device，名称/图标继续SASALELE、SA SA / LE LE。正式release打包要求真实HTTPS地址和完整私有签名配置，缺资源拒绝生成正式发布包。debug签名不是首批销售正式签名；现有debug安装转正式签名的首次迁移需要管理员安排，后续必须固定签名覆盖升级。

## 已运行验证

|用例|实际结果/界限|
|---|---|
|服务规则|21项通过，含原18项业务规则以及设备凭证重启/撤销/到期、production拒绝LAN码、错误/日志脱敏与不保存明文token。没有为测试批量拉真实聊天。|
|Android构建及单元任务|assembleDebug、assembleDebugAndroidTest、testDebugUnitTest通过。|
|模拟器自动测试|18项通过，4.379秒：客户端/导航规则6、Keystore配置3、网络故障与取消/有界重试3、真实中文ML Kit/OCR规则5、截图请求状态规则1。|
|容器|最终锁定依赖镜像构建成功，断网容器中合成设备授权、跨号候选、确认和客户隔离上下文通过；不调用模型、无真实凭据挂载。|
|代理/Compose|Caddy配置校验通过；Compose使用合成声明config --quiet通过；空部署模板明确拒绝缺失域名。这不是域名解析、真实证书或公网连接验证。|
|正式构建保护|无服务地址被拒绝；合成URL格式输入但无签名被拒绝。预期保护性失败，不是release已交付。|
|隔离扫描|已知7项敏感值扫描最终APK和本轮公开源码/部署文件，0命中，无私有身份样本/工作机连接文件入包。只证明已知值扫描，不证明任意未知秘密不存在。|

设备核实：Zibai_Pixel_API_36，serial emulator-5554，Android16/API36。未操作微信、未改设备时钟；不是Android15真机通过。HTTP网络用例使用127.0.0.1:18876本地合成服务器与显式debug诊断桥接，只验证错误分类/重试/取消，不当作生产HTTPS或5G链路。

发生过的失败与处理：Gradle签名配置Properties导入错误修正后构建成功；发布检查任务配置缓存捕获脚本对象修正为独立任务输入；容器测试先误依赖pytest，改为独立合成夹具后通过；一次模拟器HTTP故障用例先遭传输中断，单测复核通过，再加入限定只读重试及强制不完整响应验证，最终18项通过。未隐藏这些阶段失败。

## 未完成/待验证

- 实际服务器、域名/DNS、TLS证书、运维责任人和正式签名资源：按用户要求先待填写。没有部署公开业务服务、修改防火墙或替代购买资源。
- 正式服务真实上游联调、公网WiFi/5G、Android15后台/浮窗/系统授权、离线切换恢复、正式签名覆盖升级、管理员实际选文件发机流程：待资源落实后按清单验证。本轮Keystore测试不等于实际发机流程通过。
- 本轮没有重新对所有截图/选图GUI做完整回归；保留原实现并运行状态/OCR测试，真实设备仍须验证。
- 历史画像覆盖和19,840联系人别名索引的局限仍沿用之前报告，没有新增画像或宣称全量业务画像已接入。
- 设备独立bearer凭证可被持有发机文件的人复制；用私有文件传输、Keystore本地加密、管理员删除副本与撤销控制，未增加员工登录/硬件证明系统。
- 无真实模型调用、策略扩展、画像生成/回写或聊天批量扫描。

## 资源填写与下一步执行

复制 deploy/resources.example.json 为已忽略的resources.json，填写已有资源；没有就留空。凭据只填私有文件路径，不贴密码/Cookie/私钥。当前模板按Linux容器部署准备；若已有主机条件不同，可填写后调整部署方法，不要求盲目购买特定主机。

先运行 tools/validate_production_resources.py 校验声明；声明齐全不等于资源已验证。随后落实DNS/HTTPS、部署服务、私有只读上游配置、发机独立凭证、正式签名构建；观测就绪后再安排用户Android15测试。不得让销售处理开发电脑、扫码配对、IP或共享开发码。

必须先正式服务和授权检查通过再发机，原因是配置导入成功不能证明服务可达。服务资源到位后采用稳定URL，不给员工输入服务器地址。管理员发机文件内的独立凭证不是方舟/飞书凭据，不能分享给其他设备。

## 交付与证据完整路径

- 执行计划：D:\Dev\smart-customer-service\project\modules\qm-sales-assistant\docs\PRODUCTION-EXECUTION-PLAN-20261013.md
- 资源模板：D:\Dev\smart-customer-service\project\modules\qm-sales-assistant\deploy\resources.example.json
- 部署说明：同deploy目录README.md、Dockerfile、Dockerfile.dockerignore、compose.yaml、Caddyfile、requirements.lock、deployment.env.example、release-signing.properties.example。
- 发机/资源检查工具：D:\Dev\smart-customer-service\project\modules\qm-sales-assistant\tools\manage_work_devices.py、validate_production_resources.py。
- Android15验收清单：D:\Dev\smart-customer-service\project\modules\qm-sales-assistant\docs\ANDROID15-PRODUCTION-ACCEPTANCE-20261013.md。
- 服务测试：D:\Dev\smart-customer-service\project\modules\qm-sales-assistant\private\business-service\production-tests.xml、production-tests.log。
- 容器/部署校验：同private/business-service目录docker-build-final.log、container-tests-locked.log、caddy-config-validation.log、compose-validation.log、deployment-verification-metadata.json。
- 凭据扫描：同private/business-service目录production-credential-scan.json。
- Android构建与18项测试：D:\Dev\AndroidProjects\SASALELE\.agent\production-build-delivery.log、production-device-delivery.log。
- 发布保护拒绝记录：同.agent目录production-preflight-missing-resources2.log、production-preflight-signing-missing2.log（合成输入，无实际部署）。
- APK元数据：D:\Dev\AndroidProjects\SASALELE\.agent\evidence\production-apk-metadata.json。

APK：D:\Dev\AndroidProjects\SASALELE\app\build\outputs\apk\debug\app-debug.apk
产物时间：2026-10-13T16:14:20+08:00（PC文件系统mtime，Asia/Shanghai，非真机验收时间）。
SHA-256：`8f67cea11b5fbc0d8b2852c9a2426b54bf23879cba0408dbe7a4eff0ddde3f53`
该文件是当前源码开发验证包，未内置实际正式域名，不是首批销售正式发布包。用户填写资源后仍须真实部署、固定签名打包与Android15验收。本轮临时合成服务器及测试进程结束停止，持久资产与证据保留。
