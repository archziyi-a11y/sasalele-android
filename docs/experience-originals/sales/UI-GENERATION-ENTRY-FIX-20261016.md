# 真机发现的生成入口误导修复

2026-10-16（Asia/Shanghai）。用户在 PKW110 测试“[真实客户标识已脱敏]”时报告帮我回、润色、销售指导均提示真实模型未接入。

源码确认：该文字只存在旧 BusinessContextPanel。销售指导图标进入此面板，面板内又提供帮我回/润色/销售指导三个选项，实际只调用 context 接口。真正生成入口为 SMART_REPLY 的 BusinessGenerationPanel。服务器模型并非因此未配置。

修复：移除旧上下文面板的生产入口与重复选项，改为 SalesGuidancePanel 明确说明销售指导尚未接入生成，并提供导航至真正帮我回/润色的按钮。销售指导没有新增模型能力；后续是否实现是单独需求，不能用隐藏提示冒充功能完成。未改模型、提示词、业务接口或服务端。

版本13，1.2.1-ui；assembleRelease/assembleDebug/assembleDebugAndroidTest 和 lintVitalRelease 通过。新增模拟器 instrumentation 用例 guidanceLinksToRealGenerationWithoutLegacyTabs，验证状态说明、无旧三选项、按钮回调执行，1/1通过。它不是完整业务回归，旧OCR失败未在此解决。

正式APK已通过签名校验，与既有证书一致；PKW110覆盖安装Success、保留应用数据。安装后启动/版本信息见当轮工具输出。实际模型生成、键盘编辑复制和客户身份仍需按限定会话继续验证。

APK：D:/Dev/AndroidProjects/SASALELE/deliverables/SASALELE-1.2.1-ui-v13.apk
SHA-256：13251e65596f9f98aa2df4862adac2f80688408bee78746357f814166939c207。

证据：private/device-v12-acceptance-20261016/build-v13.log、v13-entry-fix.json。未读取其他聊天，未自动发微信。
