# 物料删除与图片分享（2026-10-17）

v23新增每项物料删除及面板内确认/取消，移除空列表自动补种演示内容。只清理本应用materials目录内且没有其他物料引用的原件，不删除图库原图或目录外文件。

网络图片不再以text/plain URL发送。MaterialImages先取得图片字节、校验实际图片与MIME，再通过限定material-share缓存目录的FileProvider、EXTRA_STREAM、ClipData和只读权限分享。新增复制图片，剪贴板保存图片URI而非网址。分享前收起物料面板。当前单张25MB上限，失败明确提示，不降级发送链接。

4项合成模拟器测试通过：图片字节/MIME/分享Intent/剪贴板类型；非法图片拒绝；删除取消/确认/重开不补种；共享原件最后一个引用删除才清理。发布构建与正式签名校验通过。未操作真实微信发送，微信输入框是否接受图片剪贴板、真实图片接收仍待用户验证。缓存长期清理尚未实现。

已覆盖安装至PKW110 / [设备标识已脱敏]，ADB返回Success并核对versionCode=23、versionName=1.6.1-materials。保留既有配置和物料，不需要服务器更新。

APK：D:/Dev/AndroidProjects/SASALELE/deliverables/SASALELE-1.6.1-materials-v23.apk

SHA256：85d6831656ff15a5e7f542c872989eced1809fd6b1db6e57fb470517b433afa6

测试原始记录：project/modules/qm-sales-assistant/private/materials-20261017/test.txt；构建记录同目录final-build.log。

图片分享合同参考：[Android官方文档](https://developer.android.com/develop/ui/compose/sharing/send)。不能由文件URI分享实现推断所有微信版本均提供图片粘贴入口。

## 用户真机反馈

2026-10-17，用户明确反馈“微信可以复制图片，可成功发送”，确认本次工作机及其微信环境的图片复制发送路径可用。该反馈不外推到所有设备/微信版本。
