# 工作号选择闪退修复

2026-10-16（Asia/Shanghai）。PKW110 v17选工作号后崩溃。设备crash缓冲确认SASALELE在设备时钟16:27:35与16:27:59出现WindowManager.BadTokenException，栈含Dialog.show与Compose AndroidDialog。保留自身应用崩溃栈，不保留其他应用日志。

根因：WorkAccountBindingPanel从悬浮服务使用普通AlertDialog确认，窗口token=null；此前Activity容器的测试不能覆盖此问题。修复将确认改为原面板内Card，显示账号、切换影响及使用/取消按钮，不新增Dialog/Popup窗口；候选列表在确认时收起以便阅读。服务器、鉴权及保存逻辑未变。

验证：正式/Debug/AndroidTest构建及lintVitalRelease通过。原4项工作号测试通过；新增真实TYPE_APPLICATION_OVERLAY（application Context、ServiceLifecycleOwner、无Activity token）完整选择确认测试首轮因测试代码context被View.context遮蔽而未使用合成配置，超时，记录保留；明确指定测试上下文后该用例1/1通过。没有把首轮超时算成通过。

正式签名沿用，PKW110覆盖安装v18成功、启动Status ok。真实手机重新选择工作号待用户单次核验，未自行猜选工作号，未清数据或卸载。合成测试HTTP已停止。

APK：D:/Dev/AndroidProjects/SASALELE/deliverables/SASALELE-1.4.1-work-account-v18.apk
版本18 / 1.4.1-work-account。
SHA-256：08e24c0203b02f5fef67161c541a46e08c1ed3ebcbc0e173fdd30fa8e9d474c2。

证据：docs/evidence/work-account-crash-20261016/；构建日志private/work-account-build-v18-20261016.log。
