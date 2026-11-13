# 活跃客户读取、模型429与Android返回诊断

2026-10-15，Asia/Shanghai。更新此前实施记录中的待验证判断；不替代生产或真机验收。

## 活跃客户样本

用户在方舟截图指定同一工作号下的新样本。仅搜索该名字并限定该工作号，得到1条匹配；详情双标识、昵称及微信ID尾号与截图吻合。没有扫描其他会话。

读取窗口2026-09-15 16:31:42至2026-10-15 16:31:42 +08:00，时钟来源为开发电脑time.time；不是手机时钟。实际取得11条记录，7条text、4条other；3条inbound、8条outbound，均有消息ID，响应双标识未发现冲突，规范化拒绝0条。

发生时间为2026-09-29 20:29:56至20:49:35 +08:00，与用户截图可见的当晚时间相符。未保存或输出正文，未发送客户数据给模型。样本足以验证读取链路，不代表完整历史、充分画像质量或全部工作号覆盖。

此前6月客户在120天窗口只取得1条非文本记录，与方舟网页中的系统分配事件吻合；不能据此判断平台聊天保留期限。历史回补与批量同步本轮未执行。

## 模型429

一次专门诊断只发送合成文本，使用既有kimi-k2.6配置。上游HTTP429，安全解析得到error.type=`exceeded_current_quota_error`；错误文本存在额度/余额提示，不存在已匹配的并发/频率提示，无Retry-After。

因此现有证据支持账户调用额度问题，而不是仅凭429推断并发限流。尚未读取模型账户账单，不能精确断言充值余额、赠送额度或账户其他配额中哪项耗尽。停止真实请求；不擅自更换模型或凭据、不自动充值。

服务端新增明确错误映射：model_quota_exceeded、model_rate_limited及未知429兜底；Android对额度问题提示管理员核实账户，不再建议反复重试。3种错误分类用假HTTP响应验证，未暴露上游原始错误文本。

## Android草稿返回

之前的“8/9失败”来自自编Python HTTP桩，不能直接视为生产业务接口已经失败。

本轮观测发现：成功用例单独运行通过；组合序列曾返回ProtocolException。随后相同HTTP桩又出现3/3、9/9通过，未做可证明因果的协议修复，故不能称旧异常根因已查清。

为避免继续围绕不稳定HTTP桩猜测，改用项目实际sales_http FastAPI路由、实际SalesGeneration任务及SQLite缓存，替换方舟和模型为合成实现，在主机127.0.0.1:18878运行；模拟器通过10.0.2.2访问。

模拟器serial=emulator-5554，AVD=Zibai_Pixel_API_36，独立包com.example.sasalele.generationtest。实际HTTP接口10项通过，包含提交/轮询成功取得草稿、模型失败中文提示、取消等待，以及连续5轮取消旧请求后再生成新草稿。业务规则6项包含在10项中。

这证明当前Android与真实服务路由的合成闭环可运行，且不能将旧桩的异常笼统写成APP确定缺陷；未证明生产HTTPS、真实模型与PKW110真机通过。旧ProtocolException仍保留为未完全定位的测试链路问题。

Android增加只保留异常类别、框架方法名和固定协议类别的内部diagnosticCode，不包含异常原文、请求正文或凭据；日常反馈不显示该内部字段。

## 读取代码修正与回归

- searchFriendMessage返回结构异常时明确报错，不再默默变成空列表，避免错误宣称没有历史。
- 首次方向为空时，在现有页数预算内尝试反方向；不增加无限请求，coverage记录是否回退。
- 空结果明确标注“本次窗口与接口未取得消息，不证明没有历史”。
- 服务端最终52项通过，包含新读取/429分类与原查询、生成、画像作业回归。

## 当前状态与后续

已取得活跃客户消息，真实HTTP路由的Android合成测试已通过；没有真实客户画像或草稿生成，未更新服务器、未覆盖安装工作机，未触发任何历史恢复/批量任务。

下一步外部条件是核实并恢复现有模型账户可用额度，安全补齐服务器模型配置。之后先用合成文本验证模型，再仅对用户指定样本生成有限画像、缓存复用和两类草稿，最后部署与真机验收。不能因代码测试通过而宣称生产闭环完成。

版本10旧APK/部署包仍对应上一轮构建，本轮源码新增修正尚未重新发布，不应将旧包视为已包含此次修正。

## 证据完整路径

服务端目录：`D:/Dev/smart-customer-service/project/modules/qm-sales-assistant/private/chat-read-audit-20261015/`

- `active-customer/metadata.json`：活跃样本时间、类型、方向、双标识核验；无正文。
- `model-error-classification.json`：429安全分类；无密钥和上游错误原文。
- `window120-metadata.json`、`normalized-metadata.json`：旧样本限定读取证据。
- `tests-final.txt`：52项通过。
- `synthetic_actual_http.py`：使用实际业务路由的合成服务。

Android目录：`D:/Dev/AndroidProjects/SASALELE/private/profile-reply-20261015/`

- `instrumentation-diagnostic-sequence.txt`：旧HTTP桩ProtocolException。
- `instrumentation-protocol-point.txt`、`instrumentation-protocol-suite.txt`：同桩随后3/3和9/9通过；不能据此认定修复。
- `instrumentation-actual-http.txt`：实际HTTP业务路由10项通过，包含连续5轮取消后生成。
