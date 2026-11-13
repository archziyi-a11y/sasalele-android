# 正式 UI 改版前基线

2026-10-17：1.6.3-materials / versionCode 25。

包含现有画像、销售指导、回复/润色、工作号选择缓存、跟进提醒与忽略列表、物料删除/图片文件复制分享、类型筛选和文本/链接新增。

APK：deliverables/SASALELE-1.6.3-materials-v25.apk（本地产物，不提交二进制）。SHA-256：715ddc0534ce3fd08f8dfcd8d674b7653765aa5bd28e57a090c5d575339ee29a。

正式签名构建已通过；新增及筛选测试 3/3 通过。此前图片剪贴板模拟器回读测试仍失败，原因未确认；用户已确认 v23 真机微信图片复制发送成功，不能替代 v25 验收。当前版本保留现有界面布局，尚未进行正式版信息层级和交互整理。

对应服务端：sales-business-v3、sales-guidance-v2、work-account-v1、followups-v2。服务端代码在 D:/Dev/smart-customer-service 独立仓库；本提交不含凭据、签名私钥或真实聊天快照。
