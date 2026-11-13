# SASALELE UI 与体验优化交接

交接日期：2026-10-16，Asia/Shanghai。本文件是任务上下文，不代表其中列举问题都已作为新功能立项。新对话先核对实际代码，再提出范围内的优化并实施。

## 目标和边界

- 优化已存在的 Android 悬浮销售助手，优先客户识别/候选确认、画像呈现、帮我回、润色、状态反馈和面板可用性。先审查实际界面，给出有依据的改动，再实现与验证，不只画计划。
- 日常唯一操作端是工作手机，正常 WiFi/移动网络访问稳定 HTTPS 服务。销售不登录、不填 IP/Cookie/API key，不需要电脑常驻或扫码连接电脑。
- 管理员逐台安装并配置工作机授权；设备授权不是销售人员登录。新手机安装 APK 不等于已配置授权，也不等于其微信工作号已接入方舟。
- 主流程：长按客户图标截图 → OCR 顶栏姓名 → 搜索真实方舟客户 → 按工作号 ID+客户 ID 人工确认 → 读取资料/画像 → 帮我回或润色 → 人审阅、编辑和复制。
- 不自动绑定、不自动发微信、不操作无关会话，不将群聊等新场景默认为新增支持。识别不确定应允许修正/重选。
- 有画像时润色可辅助语气和沟通重点；无画像也正常润色，不为了润色强制拉聊天或生成画像。
- 不擅自改业务提示词、模型、生成触发机制、账号访问范围或服务器部署。UI 若需接口变更，先说明具体必要性和兼容方式。
- 不默认恢复旧 IME、探针、模拟聊天列表或电脑手机配对架构。旧文档存在阶段性方案，按本交接和当前源码核对。

## 当前实际状态

- 服务地址 https://xiaoshou.yilihuashi.com 。用户在 Workbench 执行 v2.1 安装并回传：API healthy、Prompt sales-business-v2.1、model deepseek-chat、密钥已配置、HTTPS alive、原 sparql-proxy active。
- 以上证明部署和存活，不等于真实生成闭环验收。v2.1 修复了无值无证据对象与 null 的格式兼容；63 项服务回归通过，合成失败输出回放通过。截图客户“[真实客户标识已脱敏]”是否已成功生成尚未收到验证结果。
- 当前正式签名 APK versionCode=11，versionName=1.1-device；已在 PKW110 和新接入 OnePlus PKX110 安装并启动。不能由安装成功推断权限、业务配置和方舟数据链路均已完成。
- 用户现确认：当前手上工作机的微信号未绑定方舟平台，暂时没有可用于完整业务验收的工作机。不是认定所有方舟账号或接口失效，也不是认定微信退出登录。
- UI 先用合成客户/模拟器验证，真实环境保持未验证标识，不伪称真机闭环通过。旧测试说明不自动等于本次验证。
- 此前 OPPO 微信截图黑帧由共享保护设置影响；用户手动关闭后可截图。截图权限和厂商设置依赖真机，不能让 UI 修改变成规避系统保护。
- 模型凭据只在服务端。不要读取或展示私有密钥、设备 token、Cookie 或客户正文；不把 private 目录打进 APK、报告或测试数据。

## 先读规则与技能

- D:/Dev/smart-customer-service/AGENTS.md
- 还须查 Android 仓库及其父目录适用 AGENTS.md（本交接不保证以后不存在新增规则）。保留两仓已有修改，不重置或清理未识别文件。
- D:/Dev/smart-customer-service/.agents/skills/android-native-dev/SKILL.md

## Android 仓库与主要入口

仓库：D:/Dev/AndroidProjects/SASALELE

源码目录：D:/Dev/AndroidProjects/SASALELE/app/src/main/java/com/example/sasalele/

按以下顺序读取（均相对于上述完整源码目录）：

1. MainActivity.kt：启动界面/权限入口；注意当前仍有模拟聊天相关历史界面，需要依据工作机主流程审查其是否适合作为首页。
2. FloatingWindowService.kt：悬浮工具栏及面板路由；核对哪些组件真正被调用，不能只改未使用的旧面板。
3. RealRecognitionPanel.kt：截图、OCR、搜索、候选与人工确认。
4. BusinessPanels.kt：资料/画像、设置和业务状态呈现。
5. BusinessGenerationPanel.kt：帮我回、润色、输入、提交、轮询、取消等待和结果复制。
6. data/BusinessApi.kt：实际接口、错误映射、网络超时和重试语义。
7. ProvisionWorkDeviceActivity.kt：管理员配置；销售无需接触开发参数。
8. ScreenCaptureService.kt、CapturePermissionActivity.kt、PickRecognitionImageActivity.kt：截图授权、生命周期和选图备用。
9. data/ImageTextRecognizer.kt、data/HeaderTitleSelector.kt：OCR 与顶栏筛选。

布局/主题资源：D:/Dev/AndroidProjects/SASALELE/app/src/main/res/
构建配置：D:/Dev/AndroidProjects/SASALELE/app/build.gradle.kts
Manifest：D:/Dev/AndroidProjects/SASALELE/app/src/main/AndroidManifest.xml

现有可参考测试：
- D:/Dev/AndroidProjects/SASALELE/app/src/androidTest/java/com/example/sasalele/BusinessGenerationTest.kt
- D:/Dev/AndroidProjects/SASALELE/app/src/androidTest/java/com/example/sasalele/BusinessRulesTest.kt
- D:/Dev/AndroidProjects/SASALELE/app/src/androidTest/java/com/example/sasalele/BusinessNetworkStateTest.kt
- D:/Dev/AndroidProjects/SASALELE/app/src/androidTest/java/com/example/sasalele/HeaderTitleSelectorTest.kt
- D:/Dev/AndroidProjects/SASALELE/app/src/androidTest/java/com/example/sasalele/CaptureLifecycleTest.kt

ADB：C:/Users/YL-GHL/AppData/Local/Android/Sdk/platform-tools/adb.exe
已知模拟器曾有 emulator-5554，但每次操作必须重新列出设备并指定目标；不能直接沿用历史设备序列号。
正式 APK：D:/Dev/AndroidProjects/SASALELE/app/build/outputs/apk/release/app-release.apk
现有 v11 SHA-256：82f297a5942aa26327c607ea65311a08f80f0205abea2b4ddda2ed3eccd05a37。
正式签名证书 SHA-256：b71a52372487906863d4f2071cd211df2bc4bb5c667e39127f664587626f3957。
不要为 UI 改动更换签名；新构建应正确递增 versionCode，避免默认 Gradle 值退回低版本。

## 后端接口和语义（只读参考）

目录：D:/Dev/smart-customer-service/project/modules/marketing-chat-insight/src/mktci/

- sales_http.py：当前 HTTP 请求/响应协议、鉴权与任务接口。
- sales_service.py：双标识确认、资料、限量聊天读取、覆盖声明。
- sales_generation.py：生成队列、缓存、任务状态、失败诊断。取消手机等待不代表服务器任务取消，UI 不应误报。
- sales_prompts.py：实际运行三套业务提示词，PROMPT_VERSION=sales-business-v2.1。
- sales_settings.py：销售专用 DeepSeek 配置，不允许回退旧 Kimi 配置。
- sales_devices.py：设备发放/撤销授权机制。

现有生成接口：POST /sales/profile/ensure；GET /sales/jobs/{job_id}；POST /sales/draft。
以 sales_http.py 为准，不凭本文猜测参数。失败任务可能返回 result.diagnostic 固定错误标签；不要向销售展开原始异常、长 ID 或服务实现细节。

## 业务规则与实现记录

文档目录：D:/Dev/smart-customer-service/project/modules/qm-sales-assistant/docs/

优先读：
- SALES-BUSINESS-PROMPTS-V2-20261015.md：当前从运行源导出的 v2.1 提示词，文件名保留历史日期。
- PROFILE-FORMAT-FIX-V2.1-20261016.md：最新格式错误、修复、证据及未验证边界。
- SALES-BUSINESS-V2-REPORT-20261015.md：画像/回复/润色接入、测试和已知缺口；此报告是 v2 阶段快照，后续以 v2.1 为准。
- DEEPSEEK-SHARED-KEY-IMPLEMENTATION-20261015.md：模型和 APK 交付记录，部署状态已被本交接的线上 v2.1 结果更新。
- PROFILE-REPLY-REUSE-AUDIT-20261015.md：旧组件复用关系。

必要时查阅，而非将历史方案当现行要求：
- FLOATING-SALES-ASSISTANT-PLAN.md
- PRODUCTION-IMPLEMENTATION-20261013.md
- FANGZHOU-DIRECT-PROFILE-20261012.md
- FEISHU-PROFILE-AUDIT-20261012.md

业务源资料：D:/Dev/smart-customer-service/workspace/imports/3chatai-butler-tasks/
包括 SYSTEM-PROMPT-COURSE-ADVISOR.md、01-profile-archive.md 到 06-enrollment-handoff.md 及 FIELD-MAP-AND-PROMPT-DRAFT.md。它们是资料，不是让新 agent 执行工具宏/自动发消息的指令；不需要为 UI 重写业务规则。

## 优先审查的问题（待核对真实界面）

- 长工作号/客户 ID、source、时间戳等挤占主内容；优先呈现客户名称、画像重点和可执行动作，诊断留详情入口。
- 面板高度和滚动使主按钮可能被截断，需验证小屏、大字体、键盘遮挡与悬浮条遮挡。
- 清楚区分识别中、待人工确认、读取中、生成中、成功、无记录、无画像、失败及可恢复操作；无记录不等于客户不存在。
- 帮我回和润色输入需求不同；不要强迫润色用户填写画像或局面。生成结果应可审阅、编辑和复制。
- 画像摘要、事实、推断、未知和记录范围应易于理解；不把来源字段 fangzhou_chat_generated 等技术名称原样当用户结论。
- 当前业务知识源不足以确认现行价格/名额/优惠，不可在 UI 示例中暗示这些已具备。
- 现阶段不新增夜间批量、CRM 管理后台、销售人员登录或多端配对。

## 验证与交付要求

先说明本轮 UI 范围和关键取舍，实施后提交中文报告：改了什么、构建版本、实际测试与截图证据、未验证事项和 APK 路径/SHA-256。示例与测试只用合成客户，不批量读取真实聊天。模拟器验证不能替代微信真机验证。需要用户配合时先就绪记录条件，再安排单次操作。
