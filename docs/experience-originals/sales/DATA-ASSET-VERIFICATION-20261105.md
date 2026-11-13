# 数据资产可用性与替代关系核验

核验日期：2026-11-05（Asia/Shanghai，客户端日期）。本次仅核对本地代码、历史审计和部署记录；未查询远端生产、读取聊天正文、启动采集或模型。历史统计不是今日实时统计。范围为 SCS 销售画像、微信教研离线资产和 Prism；未改 UI、生产或 Prism 文件。

## 结论

目前没有任何一组资产满足“覆盖已核对且实际消费已证实”的完整替代条件。销售助手已经实现优先使用新生成画像、缺失时读取飞书历史的关系；这是代码优先级，不是历史资产已被全面替代。微信教研包与飞书教师笔记在 Prism 规划中是并列来源，不能因有 staging 资产便停用原始包。

## 分项判断

| 资产 | 已确认证据与口径 | 处置 | 当前缺口 |
|---|---|---|---|
| 方舟→飞书历史画像 | 2026-10-12 17:23:40 +08:00 历史全分页审计：102条、13账号、102唯一双键、0重复；摘要21条（20.6%）；会话关联及预约时间全空 | 继续使用（历史回退），修复时效标识 | 未与当前 portraits 做匿名双键交集；无今日表统计、回退实际命中量 |
| 销售助手聊天生成画像 | 当前代码按账号+客户存储 portraits；来源 fangzhou_chat_generated；记录生成时间、窗口、模型、提示词和 revision；2026-11-04 状态记录称真实客户已生成、提示词 sales-business-v3 | 继续使用；覆盖待核实 | 缺线上库聚合、目标客户分母、人工质量抽检及真实展示/回复消费计数；不能报告替代率 |
| 销售助手缓存 | 新文本不足时可沿用旧画像且不推进覆盖时间；消息hash、模型和提示词版本一致时复用；优先返回生成画像，再尝试飞书历史 | 继续使用，核验回退观测 | 缓存返回不等于新生成；未证明线上每个版本与本地代码一致；未量化缓存年龄及失败率 |
| 微信教研图片/笔记采集 | jyq 文档定义四群白名单、WeNote文本/XML与best-effort附图、图片、manifest；离线 inbox 用内容hash生成import标识 | 继续保全，修复完整性统计 | 功能验收done不能证明持续采集在运行；未取得当前manifest计数、附件成功率、最后成功时间 |
| 微信教研整理/离线包 | Prism CURRENT明确校长群包为总结性正式笔记、三科图片为同级素材；与飞书source_note并列 | 试接（须沿既定入库流程）；待核实 | 本轮无包清单及校验和交集，无法量化三科覆盖、重复、损坏或实际入库；不得据此删除源包 |
| 飞书教师库离线导出 | 2026-09-12历史审计：166节点（161 Docx、5文件仅元数据）、2153 blocks、1136图片引用、1053唯一媒体；1381文件、2,977,812,121 bytes、0导出错误 | 继续保全；与微信包分列 | 只覆盖三个授权科目子树；5附件二进制未取；不证明持续更新或微信包被覆盖 |
| Prism飞书staging | 后续隔离注入123 resources/123 events、937唯一assets，2,772,414,681 bytes；历史当时生产为0/0/0 | 继续验证；生产使用待核实 | 节点、候选笔记、最终资源、图片引用与唯一媒体是不同分母；不可把差额直接当丢失 |
| Prism检索及消费 | 2026-09-23 CURRENT：B1技术复核74 tests通过但in_progress，未部署/未开放公网；HEAD 5694ac4同日部署边界记录 | 待核实部署；试接只读查询 | 9月12日“API不存在”已被后续实现推进，不能沿用为当前代码结论；仍无生产检索命中或教师消费证据 |

## 时效、质量、重复与互补

飞书画像last_enriched_at最早2026-07-07、最晚2026-07-18，是作业写入时刻，不证明摘要生成时刻；最后联系也不能代替更新时间。旧审计不能证明11月仍未更新。方舟原生详情已有两客户只读验证，但未含完整销售摘要；原生身份资料、聊天生成画像与飞书历史是不同层。

新画像优先返回意味着即使已有生成画像较旧，也不自动证明飞书历史更差；应按覆盖窗口和证据评审。生成真实发生、用户看到、生成回复引用、员工采用分别需要证据，不能互换。

媒体SHA-256去重仅证明字节重复；跨微信与飞书的裁剪、压缩图片可能内容相同但hash不同。节点幂等、画像双键唯一、文件hash去重均不能证明跨来源语义无重复。校长总结、教师原笔记和三科素材图具有业务互补关系；这一点来自产品裁决，尚非匿名内容对照结果。

## 工作台可展示字段

建议展示：asset_family、source_system、source_role、environment（offline/staging/production）、status（继续使用/修复/试接/已替代/待核实）、evidence_level（代码/历史真实操作/当前生产观测）、count、count_unit、denominator、coverage_window_start/end、source_updated_at、generated_at、observed_at、timezone、clock_source、unique_key_count、duplicate_count、missing_count、last_success_at、cache_age、fallback_hit_count、search_hit_count、consumption_count、evidence_path、evidence_date、limitations。未知值为null并显示“未观测”，不得填0；历史生产0只能附2026-09-12时点。

“已替代”须同时提供：授权目标集合分母、匿名双键或资产稳定键交集、缺口及质量对照、覆盖窗口、生产可检索验证和真实消费记录。当前无项达到。

## 证据与后续最小核验

本次来源：
- SCS project/modules/qm-sales-assistant/docs/FEISHU-PROFILE-AUDIT-20261012.md
- 同目录 FANGZHOU-DIRECT-PROFILE-20261012.md、PROJECT-STATUS-AND-RETROSPECTIVE-20261104.md
- SCS project/modules/marketing-chat-insight/src/mktci/sales_service.py（_profile）、sales_generation.py（_ensure_profile）
- SCS project/modules/jiaoyan-qun-archive/docs/MIC.md、DEV_ENTRY.md
- Prism docs/audits/feishu-teacher-notes-import-phase-b.md、feishu-wiki-structured-export-success-20260912.md
- Prism workspace/agent_bridge/CURRENT.md、decisions_log.md D015/D016、PROJECT-SNAPSHOT.md

需补证：在获授权的只读环境内，对飞书历史双键与线上portraits做同盐匿名交集；只输出双方总数、交集、摘要可用数、窗口和年龄分布。对微信包manifest与Prism来源映射做稳定键/校验和聚合；分别核对飞书与微信来源。查询日志只统计限定来源的成功检索、返回及采用事件，并明确统计区间与日志留存边界。没有日志只能标记“无观测”，不能称“零使用”。本报告未执行这些尚缺环境授权或明确数据入口的操作，核心替代验收保持未通过。
