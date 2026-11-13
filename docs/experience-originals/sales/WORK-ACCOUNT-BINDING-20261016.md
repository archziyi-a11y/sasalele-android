# 当前工作号选择与全助手共用

日期2026-10-16，Asia/Shanghai。本机实现与测试完成，线上服务器和工作机尚未更新至本轮版本。当前实际手机v16，服务器为之前guidance版本。

## 实现

- 助手设置顶部提供当前工作号、选择/切换列表和确认；提醒页提供设置直达。销售界面只说明当前号和下一步，不解释方舟设备接入。
- 选择写入noBackup私有目录selected-work-account.json，AtomicFile保存，重启读取；不从当前微信屏幕或已有客户自动猜工作号。选择列表由已有设备授权允许的工作号组成，保持原有服务端授权范围，不把本地选择冒称服务器权限隔离。
- 切换时解绑当前客户、清理客户查看目标及持久化客户绑定。客户页面以工作号为key重新创建，画像/生成结果不会跨号延用；旧请求返回前在API检查当前工作号。旧持久客户只有与选定工作号相符才恢复。
- 搜索固定使用当前号，移除日常全工作号筛选；API追加当前account_id、拦截其他工作号的客户目标；未选择时相关面板引导助手设置。生成仍需人工确认客户，不自动将工作号选择当客户绑定。
- 提醒列表和悬浮角标只展示当前号。实际Android提醒输出只处理所选号，未选择不推送；提醒存储不删除其他工作号数据。独立规则测试中的FakeNotifier仍用于通用调度验证，本轮没有新增消息采集或全量提醒联调。
- 共享物料保持原有公共资料能力，不把物料内容强制按工作号拆分。本轮未改图片分享。

## 接口

新增GET /sales/accounts?refresh=true，复用工作机设备鉴权，返回accounts[{account_id,display_name}]。调用authorized_accounts既有限定分页和缓存，只取账号列表，不搜索客户或读取聊天。不提供微信登录/上线接口；APP未新增销售登录。

## 实际测试

- 后端69项通过：新账号列表鉴权、返回授权列表且无客户/消息读取，加现有生成、客户读取、模型配置和格式校验回归。
- 模拟器：既有UiExperienceTest 6项通过，覆盖候选人工确认、画像、润色编辑复制、指导模式及输入修改结果保留等。
- 新WorkAccountSelectionTest初次因测试清理方法返回Boolean导致JUnit初始化失败，原日志保留；修正为Unit后4/4通过：服务列表选择并保存、切换清客户、重新载入恢复、未选择引导、其他号请求拦截、提醒设置入口（部分断言属于同一用例，不拆成额外数量）。
- 正式/Debug/AndroidTest构建和lintVitalRelease通过，同正式签名校验通过。容器离线构建、接口导入和安装脚本bash -n通过。只用合成账号和本地HTTP，不读取真实客户，不调用真实模型。测试服务已停止。

## 交付

APK：D:/Dev/AndroidProjects/SASALELE/deliverables/SASALELE-1.4-work-account-v17.apk
版本17 / 1.4-work-account。
SHA-256：57bfcebe2d8cc7737f98a75cd4252d97960aa15d165d428c3c40d9838ceae807。
构建产物文件时间：2026-10-16T16:14:35.712193+08:00（开发机文件时钟）。签名证书仍为b71a52372487906863d4f2071cd211df2bc4bb5c667e39127f664587626f3957。

服务包：private/sasalele-work-account-20261016.tar.gz，61708字节。
SHA-256：21fb487ebb4ac40d04d9511ac461412460b696f8493837aeee0b6b53b79c63b3。
镜像sasalele-sales-api:20261016-work-account，接口版本work-account-v1。原有sales-business-v2.1和sales-guidance-v1提示词未变。

必须先部署服务，再安装v17，否则旧服务没有accounts接口。上传 /home/admin/sasalele-upload，校验解压到 /home/admin/sasalele-work-account-20261016 后 sudo bash install.sh。脚本复用既有加密模型材料，备份SQLite，只更新API，启动失败尝试回旧镜像。包无私有客户数据库，限定检查未发现已有模型密钥明文。

## 待验收

目标服务器上线、v17覆盖安装、实际工作号名称识别选择、真实设备重启及切号尚未验收。选择工作号是人工声明，不自动证明手机当前微信登录账号；销售端不增加后台概念说明。实际提醒数据源和调度全链路仍未重新验收，不能因本轮筛选实现就宣称实时提醒已上线。素材分享后续单独做。

证据：docs/evidence/work-account-20261016/，构建日志private/work-account-build-v17-20261016.log。
