# 销售助手真实业务接入交付报告（2026-10-13）

## 结论与边界

本轮实现只读闭环：长按系统截屏 → 端侧中文 OCR → 自动业务名称查询 → 跨工作号候选 → 人工确认双标识 → 方舟基础资料＋飞书历史画像＋对应私聊近期消息 → 结构化“帮我回”上下文。模拟器与必要真实数据只读联调已运行；不是 Android 15 真机验收通过。

没有调用真实回复模型、发送或复制微信消息、生成新画像、恢复自动任务、写飞书/方舟，也没有操作微信或修改设备时钟。既有通用模型客户端仍在，但没有现成已验证的回复业务通路，本轮交付可查看的结构化数据，不扩展策略功能。所有测试点击仅在助手、系统授权页及独立受控测试 APK 中进行，生产代码没有无障碍读取或注入点击。

修改前保存 Git HEAD、状态、差异和非破坏性源码副本：D:\Dev\backups\SASALELE-business-20261013。保留两个仓库已有用户改动，没有重建项目、修改旧探针项目或提交 Git。未改变业务数据库结构；新增独立本地缓存表。

## 数据来源与覆盖

| 数据层 | 已确认覆盖与限制 |
|---|---|
| 方舟授权范围 | 当前既有服务凭据可见 15 个工作号；历史画像只有 13 个，不能混为同一覆盖量。没有新增销售人员登录系统。 |
| 实时名称 | 已验证 friendlistData 的 keyword 可匹配真实昵称、备注；pageIndex 从 0 起分页。page/pageNum 与 friendKeyword 不可靠或被忽略，没有继续采用。 |
| 别名 | 原生 keyword 对测试微信 alias 不命中，使用可手动刷新联系人资料索引。只读 100 页、每页 200，缓存 19,840 个唯一双标识，末次服务 total 为 19,841；动态分页期间数量不一致，coverage_complete=false，不能宣称完整快照。不是 19,840 条画像。 |
| 索引时间 | PC time.time，Asia/Shanghai：开始 2026-10-13 09:09:08+08:00，完成/刷新 09:10:58+08:00；读取阶段 monotonic 107.98 秒。不是消息发生或画像生成时间。 |
| 飞书历史画像 | 重新核对：102 条记录、13 个工作号、21 条非空摘要。最新 last_enriched_at 为 2026-07-18 09:43:58.288+08:00，只能称作业写入时间；摘要生成时间未知。缺失、陈旧、冲突、不可用独立标记。 |
| 真实聊天联调 | 只读原已授权样本的一个私聊，7 天窗口取得 2 条入站消息；双标识资料响应核验通过。没有批量读取其他客户聊天，没有完整历史结论。 |
| Android | 不再打包旧私有身份样本。手机运行时从局域网查询真实业务数据；本地 JSON 导入与裁剪保留在诊断/维护，日常保留选图备用。 |

真实名称返回 11 个候选、分属 11 个工作号。按名字或别名相同不合并客户，只有同一工作号 ID＋客户 ID 才去重。人工确认缓存优先，同名仍保留其他候选。常见测试 alias 跨号首批 50 条不含目标，按工作号筛选后通过；分页可继续查看，不能把第一页当成无目标。

私有数据位置：D:\Dev\smart-customer-service\project\modules\qm-sales-assistant\private\business-service\sales-cache.sqlite；上游配置沿用既有 config/.env 加载规则，凭据值不写入报告。真实截图/姓名证据位于 Android 已忽略 .agent/*.png；不提交 Git。聊天只在短期服务内存及手机查看页中组装，不写聊天正文日志。

## 接口与缓存

复用 marketing-chat-insight 的方舟客户端、profile_feishu_client、字段映射、normalize_message 与双标识校验。新增轻量 FastAPI 查询入口，不挂载 /jobs；复用长连接，上游流程串行，HTTP 最多接纳 2 个同时请求，超限返回可重试反馈。

全部接口要求短期 Bearer 局域网访问码；不是方舟/飞书凭据，也不是销售员工身份认证。该测试码覆盖当前服务可见工作号，8 小时有效，手机必须得到明确授权后使用。

| 接口 | 参数/行为 |
|---|---|
| GET /sales/health | 只读状态、工作号数、服务启动时间、访问码有效期；未授权 401。 |
| POST /sales/search | q、可选 account_id、page（从1开始）、page_size≤50、refresh。实时昵称/备注＋别名索引＋确认缓存；返回候选、来源/观察时间、画像线索、分页/覆盖元数据。 |
| POST /sales/detail | account_id＋friend_id、refresh；分别返回 identity 与 profile。 |
| POST /sales/confirm | 双标识、recognized_name；强制实时核验资料，持久保存本地人工确认及别名，不写上游。 |
| POST /sales/context | 双标识、request_type（帮我回/润色/销售指导）、latest_situation≤4000字符、refresh、budget_chars=6000..32000；须已人工确认。 |

确认缓存存 SQLite，含双标识、名称/别名、来源、确认时间；30 天后标为旧确认，仍需核对，绑定每次实时核验。身份缓存 5 分钟、历史画像快照 15 分钟、近期聊天内存 60 秒（最多128组），互不阻断刷新；服务重启保留确认/身份/画像，聊天重新按需读取。工作号范围 15 分钟刷新。别名索引 24 小时后提示陈旧，PC 手动刷新、最多150页/180秒，不新增定时扫描；失败保留原有效代次。索引更新只读取联系人资料，不扫聊天。

近期聊天只限定确认客户的双标识和最近7天，最多2页×50条，返回本次取得范围内最近50条；保留消息ID、isSend规范化方向、wechatTime毫秒及+08:00时间。响应有会话双标识时逐行校验，缺失时只说明请求限定会话，不假装每行已核验；同毫秒先后未证实。窗口/分页上限/拒绝行/读取时间明确返回。画像不可用或聊天失败仍有分层反馈，不能当成无历史。

上下文按 JSON 字符预算（默认16000，非模型token）保留关键画像及最近消息；单条消息/摘要最多2000字符，补充最多4000，返回遗漏条数和截断标记。聊天正文为数据，不执行指令。示例仅结构，以下标识与文本为合成：

```json
{
  "target": {"account_id":"synthetic-account","friend_id":"synthetic-customer","name":"合成客户","source":"fangzhou_friend_detail","dual_key_verified":true,"observed_at":"带+08:00的观察时间"},
  "historical_profile": {"status":"missing","summary":null,"source":"feishu_history","job_written_at":null,"summary_generated_at":null,"stale":true},
  "recent_messages": [{"msg_id":"synthetic-message","direction":"inbound","sent_at":"带+08:00的发生时间","sent_at_ms":0,"content_text":"合成消息数据","content_truncated":false}],
  "user_supplement":"用户补充局面",
  "user_request":"帮我回",
  "coverage":{"complete_history":false,"pages_read":1,"included_messages":1},
  "budget":{"unit":"JSON characters","limit":16000,"omitted_messages":0},
  "trust_boundary":{"chat_is_untrusted_data":true,"execute_chat_instructions":false,"model_called":false}
}
```

## Android 行为与实际验证

日常搜索自动发起、可修正 OCR 姓名、可选工作号筛选、查询中/无匹配/失败/重试/分页；候选头像、工作号、双标识、来源与摘要线索可查看。人工确认后显示姓名/头像并反馈绑定。日常面板移除关闭按钮；外部点击由全屏透明助手窗口消费，逐级详情→上级面板→工具条→最小入口，最小入口外的下一次普通点击恢复底层正常操作。

直接截屏继续使用正式 MediaProjection 授权及 mediaProjection 前台服务，仅截一张并释放。独立应用测试发现原授权页会带起助手主页，本轮修正为空 taskAffinity＋独立临时任务，复测截图目标为原独立应用。隐藏助手截图，系统投屏标记属于系统画面；直接截图 OCR 按当前设备状态栏尺寸过滤标记，选图不假设相同设备尺寸。仍显示页面“不确定”，群聊/公众号/列表有明确线索时排除，不读取正文。诊断裁剪可调整；不宣称全部方向/设备已适配。

AVD核实为 Zibai_Pixel_API_36，serial=emulator-5554，Android16/API36；不是 Android15。PC WLAN测试地址<测试机局域网地址>:8876，模拟器确实访问该LAN地址，无localhost替代或adb reverse。未开公网服务、未改防火墙。

| 用例 | 本轮实际结果 |
|---|---|
| 服务规则 | 18项合成测试通过：跨号同名/筛选/无匹配、双键冲突/删除、确认前禁止消息、持久确认/失败降级、画像陈旧缺失、方向时间及跨客户行拒绝、刷新、预算最近消息/截断、鉴权过期、别名特殊字符与索引失败保留。 |
| 模拟器自动测试 | 逻辑版本13项通过：4项客户端规则、3项真实LAN/目标确认上下文/网络失败、5项真实ML Kit中文/横屏合成图片/群聊排除/裁剪/候选歧义、1项截图请求状态规则。不是13项系统截屏验收。 |
| 真实搜索 | 11个候选/11个工作号、可选筛选正确、无匹配0条；资料响应双标识通过；服务实际重启后确认缓存仍在。 |
| 真实GUI闭环 | 独立测试页显示授权样本名称→长按授权→端侧OCR精确名称→自动查询11候选→核对授权双标识→人工确认→姓名头像→上下文组装反馈。只读取同一私聊，不截图聊天原文详情。 |
| 跨应用截屏 | 修正后原独立应用画面，无助手工具条/面板，无助手主页；资源检查 media_projection=null。连续两次分别读取合成标题及授权标题，无旧结果覆盖观察。 |
| 外部点击 | 独立应用计数按钮为0；详情返回上级、面板收工具条、工具条收最小入口时计数不增；最小入口后下一普通点击计数1。短按资料仅打开资料面板，不出现截屏授权。 |
| 授权取消/超时、选图 | 允许、取消、授权等待超时后恢复；系统选图→临时私有副本→预览/真实OCR通过；退出后清理。细节见末尾补充。 |
| 隔离扫描 | 已知7项敏感值对最终APK全部解压条目及本轮源码扫描：0命中；没有私有身份样本/连接文件入包。此为已知值扫描，不能证明任意未知秘密不存在。 |

曾出现的失败：默认pytest临时目录Windows权限、测试替身缺少目录客户端方法，修正后通过；独立测试APK缺少目标App依赖，改为纯Java系统组件后启动通过；跨应用授权带起主页、最小窗口范围、OCR系统标记误选已发现并修正复测。此前旧报告里的助手模拟页短标题不稳定不能泛化为全部OCR可靠；本轮只对实际列出的标题/图片成功作结论。

## 未实现与未验证

- 未调用真实回复模型，不产出真实销售建议，不生成或回写画像。
- 19,840 联系人索引不是全部业务画像；19,841 动态total与唯一索引数未对齐，别名完整性未证实。未新增常驻自动刷新。
- Android15真机系统授权、后台启动/省电限制、浮窗触摸、旋转/状态栏/系统受保护画面未实机验证；API36模拟器不能替代。
- 真机WiFi到PC、防火墙/AP隔离、真实微信UI识别稳定性未测试；没有登录或点击微信。
- 多页近期消息的真实高消息量窗口完整性、同毫秒顺序未证明；只承诺明示窗口/上限及本次已取范围。
- 当前局域网测试码不是员工认证和细粒度权限系统；仅供授权测试设备，HTTP只在可信局域网使用，不部署公开服务。

## 证据完整路径

- 基线：D:\Dev\backups\SASALELE-business-20261013
- 服务测试：D:\Dev\smart-customer-service\project\modules\qm-sales-assistant\private\business-service\test-sales-final.xml 与 test-sales-final.log
- 真实搜索：同目录 real-search-final-metadata.json、real-alias-final-metadata.json；画像 aggregate 为 profile-coverage-final.json；真实聊天元数据 real-verification-metadata.json（早期只读轮次，窗口有明确起止，不当成本次UI的同一时刻）。
- 索引：同目录 alias-index-progress.json；凭据扫描 credential-scan-final.json。
- 构建：D:\Dev\AndroidProjects\SASALELE\.agent\business-build-final.log；设备测试 business-device-release.log。
- 跨应用截图：同 .agent 目录 cross-app-fixed-authorization.png、cross-app-captured.png、cross-app-ocr-statusfiltered.png、cross-app-release.txt、cross-app-release2.txt。
- 外部点击：outside-step1.png、outside-fixed-minimal.png、outside-fixed-next.png、detail-outside-step-private.png（含真实名称/标识，仅本机私有证据）。
- GUI闭环：real-ocr-ui-private.png、real-candidates-ui-private.png、real-bound-ui-private.png、real-context-summary-private.png（含真实名称/头像，不进入Git）。

## APK与真机步骤

APK：D:\Dev\AndroidProjects\SASALELE\app\build\outputs\apk\debug\app-debug.apk
构建产物写入时间：2026-10-13T10:34:09+08:00（PC文件系统，Asia/Shanghai）；构建成功证据为 business-build-final.log。
大小：74014764字节。SHA-256：`2eab11c66e6ba1c2e90dc87023c8421157ec6e6daab0f50474742373614d9eb2`。

1. 用户在PC启动只读服务。**必须**使用PC当前可达的局域网IPv4，原因是手机的localhost指手机自身；填localhost无法连接。在 D:\Dev\smart-customer-service 打开PowerShell，运行：
   `& .\project\modules\qm-sales-assistant\tools\start_sales_readonly.ps1 -LanIp <测试机局域网地址>`
   本次IP仅是测试时地址，若PC地址改变必须替换。进程前台运行，Ctrl+C停止；既有Python环境必须具备FastAPI/uvicorn/httpx及项目依赖。只绑定明确私网地址，不自动修改防火墙或开公网端口。
2. 用户在PC本地查看 D:\Dev\smart-customer-service\project\modules\qm-sales-assistant\private\business-service\lan-access.json 的code字段。**必须**仅给获授权测试手机填写此短期码，原因是它可查询当前服务可见工作号；不要填写/复制方舟Cookie或飞书凭据。8小时到期后重启服务生成新码；有效期内重启复用该码。不要把文件内容发到聊天/提交Git。
3. 用户在Android15测试手机安装上述APK，允许此次测试所需的安装来源；开启销售助手，授予“显示在其他应用上层”权限，并点击开启助手。进入工具条设置的局域网连接，填PC地址与短期码，点击保存并测试连接。预期“连接成功：15个已授权工作号”及有效期；实际数量可能变化。**必须**手机可路由访问该PC私网地址（常见方式为同一可信WiFi）；不通会显示网络失败，先检查PC服务、地址、网络隔离与防火墙，不能以模拟器成功代替真机成功。
4. 用户先在可控测试聊天画面长按工具条最上方客户图标。每次**必须**在系统弹窗明确允许当前屏幕分享，原因是正式截屏授权不能复用或绕过；取消应恢复工具条并中文提示。系统受限时使用选图备用，不绕过限制。授权后原画面应保留、助手暂时隐藏，出现截图预览、顶栏文字和查询状态；若误识别，修正姓名或进入诊断裁剪。
5. 核对候选姓名、工作号和客户双标识。**必须**人工确认目标客户，原因是跨工作号同名不能证明身份；否则不能读取对应近期聊天。预期确认后姓名/可用头像和绑定反馈。无匹配可修正名称、清筛选、翻页或重试。
6. 点击“帮我回”，补充最新局面，选择请求类型，点击刷新并组装上下文。预期看见分层状态、读取窗口、消息数、画像缺失/陈旧与时间、截断说明，明确“未调用真实模型”。查看结构化上下文会包含该客户近期聊天数据，请仅在获授权环境查看，不向外公开截图。
7. 测试短按只开资料卡、长按授权允许/取消、连续两次不同目标、外部逐级收起且该次点击不触发底层；选图入口仍应可用。尤其记录Android15的权限/后台/浮窗结果。系统导航栏/状态栏属于系统操作区域，本轮普通内容区消费验证不等于拦截系统操作。
8. 使用后停止助手并停止PC服务。别名需要更新时由PC显式运行 tools/refresh_sales_alias_index.py（只读联系人，不扫聊天），观察覆盖/失败元数据；不需要每天逐工作号扫全聊天。

本轮结束停止临时测试服务，保留持久确认/索引及私有证据；不自动进入模型、画像生成或其他阶段。


## 最终补充验证记录

- 系统截图授权取消：在本轮授权弹窗点击Cancel，返回原目标画面，助手恢复；取消后没有新截图，MediaProjection为空。证据：D:\Dev\AndroidProjects\SASALELE\.agent\capture-cancel-restored-ready-private.png、final-cancel-release.txt。授权等待超时：本轮请求开始PC time.time=1791858272.1508696（2026-10-13 10:24:32+08:00），持续未允许，超过60秒后授权页退出、独立目标恢复且工具条可用；不是无帧捕获15秒超时测试。证据timeout-authorizing-private.png、timeout-restored-private.png、timeout-release.txt（同一完整.agent目录）。
- 选图：系统Photo Picker实际选择测试截图，私有cache/recognition-images新增单文件、预览/真实OCR进入日常查询页；退出面板后目录为空。证据D:\Dev\AndroidProjects\SASALELE\.agent\picker-selection.png及picker-cancel.png。该截图文字有误读，显示不确定，未绑定客户；只验证选图通路，没有把所有图片准确识别记为通过。测试ADB截屏的命名临时文件与合成下载夹样本在结束清理；生产截屏始终写私有cache，没有公共相册写入代码。
- 部分实际系统失败仍未注入验证：无帧15秒超时、受保护屏幕、授权页被系统杀死、旋转期间捕获、Android15后台/省电限制；保留明确反馈路径，不能记成运行通过。
- 同一逻辑版本的13项模拟器自动测试、18项服务规则测试均通过；最终APK校验值与正文相同。本轮停止临时PC查询进程及助手测试进程，保留确认/索引缓存。

- 工具条收起按钮拖动实际移动，前台仍是原测试阶段助手模拟页，没有打开系统授权；证据D:\Dev\AndroidProjects\SASALELE\.agent\toolbar-drag.png。客户图标长按与短按分别在此前独立页已验证。最终资源检查D:\Dev\AndroidProjects\SASALELE\.agent\final-resource-release.txt；服务停止证据D:\Dev\smart-customer-service\project\modules\qm-sales-assistant\private\business-service\shutdown-metadata.json（2026-10-13 10:33:23+08:00，端口已关闭）。
- 服务脚本若被本机PowerShell策略阻止，可在已有Python环境直接设置项目src到PYTHONPATH并运行 python -m mktci.sales_http --host 当前PC私网IPv4 --port 8876，不必降低系统策略。页面截图取证文件为测试工具的临时文件，与应用自身私有截屏保存路径不同。

最终仅清理行尾空格后重新打包；逻辑未改变。最新打包成功证据：D:\Dev\AndroidProjects\SASALELE\.agent\business-build-package.log。

最后行尾空格清理后的交付APK已实际重新安装，离线10项（客户端4＋真实OCR5＋截图状态规则1）再次通过，4.754秒；LAN3项在此前同逻辑构建验证通过，此时服务已停止，没有伪造离线LAN成功。证据D:\Dev\AndroidProjects\SASALELE\.agent\final-apk-offline-tests.log。测试进程再次停止。模拟器状态栏显示设备自身UTC时间，与报告PC的+08:00显示差异不代表设备时钟被修改；消息时间按方舟wechatTime规范化。

## 后续图标更新（2026-10-13）

本报告正文记录的是此前业务接入构建的测试与校验值。后续仅更换白底红字“SA LE / SA LE”桌面图标及应用显示名称SASALELE，默认APK已更新：2026-10-13T14:46:47+08:00；SHA-256 `6158d701c45f43e69c4e87cdfc0e0798e4ea54b94d9321fd8bf05426104653dd`。新版本构建及模拟器安装成功，不新增业务功能。图标版本记录：D:\Dev\AndroidProjects\SASALELE\design\icons\icon-build-metadata.json。

图标排版再次更新为上排“SA SA”、下排“LE LE”，白底红字及应用名称SASALELE保留。最新APK：2026-10-13T14:52:53+08:00，SHA-256 `e3102a60b29ef8b905b34ba34811a2d470e73ae2f6fed8c422ef28fd76052301`；构建成功，未再次验证桌面显示。
