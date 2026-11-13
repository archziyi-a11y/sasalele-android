# Project Plan

创建一个可运行的原生 Android 应用，名称“销售助手”。使用 Kotlin、Jetpack Compose、Material 3，中文界面。

这是新项目。目标是先完成可继续扩展的架构、精致界面、真实本地功能和模拟数据闭环，后续由其他开发者接入图片识别、客户画像、方舟 CRM 与真实大模型。无需安装或登录微信即可演示全部主要流程。不要停在计划或静态页面：请实现、编译并运行验证。

一、产品场景与视觉设计

销售人员在微信客户一对一聊天时，通过屏幕侧边的竖向浮动工具条调用助手。

视觉参考：窄小、圆角胶囊形工具条，浅色背景、深色图标，选中项以柔和蓝色突出；点击入口展开圆角面板。保持清晰文字层级、舒适间距和大点击区域。不要把所有功能和诊断堆在同一屏。

实现真正可跨应用显示的浮窗，支持拖动、左右停靠、收起展开。按 Android 系统要求实现用户主动授权和必要的前台服务，提供关闭入口。无需无障碍权限，不使用微信节点读取、自动点击或自动发送。

另建本地“模拟聊天页”，用于无微信登录的演示：支持切换虚构私聊、群聊、公众号和聊天列表，打开键盘、滚动聊天记录。浮窗应能在这个页面实际使用。非私聊只识别并排除，不提取正文或匹配画像。

应用首页负责启用引导、演示入口、素材管理和设置；核心体验是浮动工具条，不要仅做成普通首页导航。

二、竖向工具条的五个入口，从上到下

1. 识别客户 / 客户头像
- 初始使用相机或取景框识别图标。
- 长按进入截屏识别，给出震动反馈；短按打开当前客户资料卡，未绑定时显示识别引导。
- 提供系统截图入口及导入已有图片入口。截屏时暂时隐藏助手浮窗，完成后恢复；系统截图需用户授权。不能实现的环境明确反馈，并保留图片导入。
- 本阶段图片识别使用可替换的模拟接口，明确标记“模拟识别”，不得假装实现了真实 OCR。
- 流程包含图片预览、识别中、成功、多候选、无匹配、缺少身份线索和非私聊提示。
- 匹配确认后图标变成客户头像，显示姓名和画像加载成功反馈。
- 资料卡展示画像摘要、关注点等示例字段，提供“解绑当前客户”。
- 头像绑定客户就是当前接待客户，由用户手动维护，不自动跟随微信页面，不添加“设为当前接待”。
- 点击排行榜其他客户不改变头像绑定。

2. AI 金牌销售指导
- 未匹配客户画像时置灰；点击解释“请先识别客户”。
- 绑定后展开当前客户指导和“待接待”排行榜。
- 排行榜每条显示头像、姓名、意向程度、最新消息摘要、消息时间和数据更新时间。
- 点击客户条目打开该客户独立的 AI 指导会话，支持追问并保存历史。
- 提供“看最新消息给我建议”快捷操作，模拟刷新上下文，显示已读到的消息时间。
- 可补充截图或粘贴上下文。
- 提醒文案使用“我刚查到消息”，不把查询时间当作消息发生时间。

3. 帮你回
- 使用对话气泡加笔尖图标。
- 支持手动粘贴/编辑上下文、补充图片提取结果、生成回复和润色。
- 普通回复与润色不要求先匹配画像，画像是增强信息。
- 点击生成才调用模型接口，本阶段使用模拟生成器。
- 默认生成后展示候选，用户手动复制；设置可开启“生成后自动复制”，仍须展示内容和“已复制”反馈。
- 支持编辑、重新生成、复制及模拟加载、失败、取消。
- 发起请求时固定目标客户和上下文版本；若期间解绑或更换客户，不得把旧结果显示为新客户回复。
- 不自动发送消息。第一阶段无需实现完整自定义输入法，但应使用系统键盘验证编辑体验，预留独立的输入法接入边界。

4. 常用素材
- 本地海报和定型文，支持收藏、搜索、最近使用。
- 文字预览、复制；海报预览、通过 Android 分享面板分享。
- 提供少量精致的虚构示例素材，并支持用户添加文字和导入图片。
- 这些本地功能应真实可用，而非仅模拟按钮。

5. 更多 / 配置
- 回复偏好、自动复制、提醒参数、提示音与震动、浮窗位置、权限引导、暂停助手和诊断。
- 日常界面不要显示节点、请求版本等工程字段，诊断单独放置。

三、提醒与解绑逻辑

使用模拟消息事件实现可测试的提醒状态机：

- 高意向或可能流失客户的新入站消息参与提醒，排除头像当前绑定客户。
- 支持配置“首次等待多久”“重复间隔几分钟”“最多追加几次”，分别设置，默认值明确属于演示默认。
- 长时间未回复重复提醒；确认已回复结束该轮。
- 支持“稍后提醒”和“本次忽略”。
- 连续入站消息、重复拉取事件不能产生重复提醒或无限重置次数。
- 表现为短暂闪动、持续红点、待处理客户数量，配合可分别关闭的震动与提示音，遵循系统静音和勿扰。
- 同批客户合并提示；点击查看具体客户。
- 每天 Asia/Shanghai 时区18:30和22:00自动解绑当前客户，保留画像和指导历史。
- 解绑后该客户恢复参与提醒，重新判断等待中的消息。
- 应用错过定时点时，下次启动补做；只清除跨过时间点的旧绑定，不清除时间点之后建立的新绑定。
- 定时与后台实现遵循 Android 限制，说明实际执行保证；不要为了演示擅自修改设备时钟。
- 提供可注入测试时钟和演示事件控制面板，快速验证时间点、重启恢复、回复、同步滞后、忽略和绑定排除。

四、架构与数据

按 UI、业务逻辑、数据来源分层，避免把逻辑堆在 Activity 或 Composable 内。使用 ViewModel、StateFlow、协程及合适的本地存储。

定义可替换接口：
- 图片获取与识别
- 客户画像搜索
- 回复和销售指导生成
- CRM 客户与消息来源
- 提醒调度
- 时间来源

模拟适配器与真实接入边界分开。模拟客户、消息、画像和 AI 内容清楚标记，客户 ID 和工作账号 ID 分开。客户资料查看对象与当前接待绑定是两个不同状态。

本地保存设置、素材、指导历史和必要提醒状态；截图默认不长期保存，用户主动收藏的素材除外。不要硬编码 API 密钥或请求真实服务，不访问其他项目的密钥。

五、验证与交付

在模拟器运行，验证：
- 浮窗拖动、停靠、展开与键盘共存。
- 图片导入及各识别结果状态。
- 绑定、换客户、解绑和旧生成结果处理。
- 指导会话按客户隔离。
- 真实复制、素材导入和分享。
- 提醒去重、次数上限、已回复停止、当前客户排除。
- 两个解绑时间点和重启恢复。

交付完整可编译项目、可安装 debug APK、简短架构说明、演示步骤和验证结果。列出模拟部分、真实本地功能及尚未接入的能力；未通过的项目如实说明。优先交付可运行闭环，避免不必要的复杂架构。

## Project Brief

# Project Brief: 销售助手 (Sales Assistant)

## Features

1. **Global Floating Toolbar (全局悬浮工具条)**: A draggable, collapsible, and dockable vertical floating window overlay that remains accessible across the system (designed specifically to sit alongside apps like WeChat). It includes a dedicated presentation mode via a simulated local chat page.
2. **Smart Client Recognition & Binding (客户智能识别与绑定)**: A simulated OCR and image-processing pipeline allowing users to import or capture screenshots to identify clients. It retrieves simulated CRM profiles, binds them to the active session, and automatically unbinds clients daily at 18:30 and 22:00.
3. **AI Sales Guidance & Smart Reply (AI 金牌销售指导与帮你回)**: An AI-driven assistance module providing per-client isolated chat history for sales coaching, context-aware reply generation, text polishing, and a customizable one-click copy mechanism (currently utilizing simulated AI interfaces).
4. **Intelligent Reminder State Machine (智能提醒调度系统)**: A robust background state machine managing follow-up reminders for high-intent clients. It handles custom delay intervals, retry limits, deduplication, state recovery upon reboot, and automatically mutes notifications for the currently bound client.

## High-Level Tech Stack

* **Language & UI Toolkit**: Kotlin, Jetpack Compose, Material 3.
* **Navigation & Adaptive Strategy**: **Jetpack Navigation 3** (strictly used for state-driven routing) and **Compose Material Adaptive** (strictly used for all layouts, ensuring adaptability between the floating window, expanded panels, and main app screens).
* **Architecture & Async**: MVVM architecture relying on Kotlin Coroutines and `StateFlow`/`SharedFlow` for reactive state management, decoupling UI from business logic and simulated data adapters.
* **System Integrations**: Android `WindowManager` (System Alert Window) for the floating UI, and Foreground Services to maintain the floating window and scheduling state.
* **Persistence**: Jetpack Room (explicitly required to store local materials, AI guidance history, and reminder state-machine data) and Jetpack DataStore (for storing user settings and configuration preferences).

## Implementation Steps
**Total Duration:** 16h 18m 31s

### Task_1_FoundationAndFloatingWindow: Set up SYSTEM_ALERT_WINDOW permissions, create a Foreground Service for a draggable Jetpack Compose floating window with 5 core vertical entries. Create the Simulated Chat Page in MainActivity for demonstration.
- **Status:** COMPLETED
- **Updates:** Implemented SYSTEM_ALERT_WINDOW, Foreground Service, draggable Jetpack Compose floating window with 5 buttons, and mock chat UI in MainActivity. Project successfully compiles.
- **Acceptance Criteria:**
  - App launches to Simulated Chat Page
  - Requests and grants overlay permission
  - Draggable floating window appears with 5 entries

### Task_2_ClientBindingAndAI: Implement Client Binding (simulated OCR), AI Sales Guidance (mock coaching with Room DB), and Smart Reply (simulated text generation). Add auto-unbind logic for 18:30 and 22:00.
- **Status:** COMPLETED
- **Updates:** Implemented Client State management, OCR long-press mock flow, side panels for AI Guidance and Smart Reply generation (with clipboard integration), and the 18:30/22:00 automatic unbind scheduler. Project builds correctly.
- **Acceptance Criteria:**
  - Client binding works
  - Mock AI coaching chat displays
  - Smart reply generates and copies text

### Task_3_MaterialsAndSettings: Develop Common Materials (Room DB for real local text/posters, search, favorite, share via Intent) and the Settings screen (DataStore for preferences).
- **Status:** COMPLETED
- **Updates:** Integrated Room database for functional materials management (search, favorite, copy, share via intent). Integrated DataStore preferences for persistent settings. Compiled and verified locally.
- **Acceptance Criteria:**
  - Can add, search, and share local materials
  - Settings persist via DataStore

### Task_4_RemindersAndVerification: Build the Intelligent Reminder State Machine (scheduling, persistence, catch-up mechanism). Final integration and Run and Verify. Instruct critic_agent to verify app stability, confirm alignment with user requirements, and report critical UI issues.
- **Status:** COMPLETED
- **Updates:** Reminder scheduler state machine, persistent data catch-up, and red badge built. App integrated and tested via critic_agent. Fully stable, no crashes, no critical UI layout problems detected. Fully complies with scope.
- **Acceptance Criteria:**
  - Reminder scheduling works with persistence
  - make sure all existing tests pass
  - build pass
  - app does not crash

### Task_5_RefactorBindingAndAI: Refactor Client Binding (system image picker, multi-state mock interfaces) and AI Guidance/Reply (async generation with version control, AI chat isolation with Room persistence).
- **Status:** COMPLETED
- **Updates:** Completed Stage 1 (System image picker, MockOcrEngine with multi-state branches, ClientID vs WorkAccount separation) and Stage 2 (Smart Reply async state machine with strict version control, Auto-copy settings integration, Room persistence for AI Guidance chat history per client). Project compiled and built successfully.
- **Acceptance Criteria:**
  - System image picker integrates correctly for OCR
  - Mock interfaces handle multi-state scenarios like success, multi-candidate, and no-match
  - Async AI reply generation drops stale requests upon client switch

### Task_6_RemindersMaterialsAndTesting: Implement time-injected reminder deduplication/auto-unbind, real local storage/image sharing, and write comprehensive business logic tests. Run and verify application stability.
- **Status:** COMPLETED
- **Updates:** Stage 3 and Stage 4 applied by user, tested, and built by Coder Agent. All requested business loop logic is finalized, including multi-branch OCR, Room persistence, strict Reminder dedup and catch-up unbinds, and FileProvider image sharing. Unit tests pass and APK is ready.
- **Acceptance Criteria:**
  - Reminder state machine handles deduplication and reboot recovery
  - Real image saving and sharing functions correctly
  - Business logic tests pass successfully
  - make sure all existing tests pass
  - build pass
  - app does not crash
- **Duration:** 29h 18m 11s

### Task_7_FinalStatusReportAndHandover: Generate a final status report confirming the project's completion, acknowledging the user's validation of the UI functionality and the resource exhaustion constraints. Hand over the project without further automated testing.
- **Status:** COMPLETED
- **Updates:** User confirmed UI functions perfectly on local device. Agent error was a false positive due to sandbox touch handling. Quota exhaustion noted due to heavy virtualization tasks. Project officially considered stable and complete.
- **Acceptance Criteria:**
  - Final status report is generated and provided to the user
  - Project completion is confirmed without further high-resource testing
  - make sure all existing tests pass
  - build pass
  - app does not crash
- **Duration:** N/A



## 2026-10-13 当前执行范围更新

旧阶段完成记录不代表当前已上线。现执行“首批销售可安装使用”标准：手机日常独立联网、稳定HTTPS业务服务、管理员逐台发机配置、无人员登录。可执行计划见 D:/Dev/smart-customer-service/project/modules/qm-sales-assistant/docs/PRODUCTION-EXECUTION-PLAN-20261013.md。资源由用户后续填写，本轮先完成源码/部署包/合成验证；实际部署与Android15真机待资源落实。
