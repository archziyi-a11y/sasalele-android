# SASALELE 交互原型第一版

2026-11-02，北京时间。主要实现：GPT-6.1 Terra；任务指挥、源码审计与独立浏览器验证：主代理。

## 入口

[打开交互原型](../prototypes/interaction-v1/index.html)。用浏览器打开即可，所有资源均在同目录下，无需联网。分享给他人时保留整个 interaction-v1 目录。

## 本版体验重点

- 回复：底部面板可展开，生成结果优先；输入与编辑稿按客户、功能分别保留，失败可重试。
- 物料：侧栏取用，图片预览、搜索筛选、个人标题编辑、保存到本机；个人改名保持团队原名。
- 跟进：待查看／全部／忽略切换，姓名复制、已处理、稍后、忽略和恢复。
- 客户资料与销售指导可进入；指导补充问题和生成结果随客户保存。
- 原型外壳可切客户、尺寸、深浅主题，并组合大字号与模拟键盘。

## 审计结果

主代理实际执行三组浏览器检查，记录于 [证据目录](evidence/interaction-prototype-v1-20261102/)。基本组 5 项、扩展组 13 项、补充组 3 项均通过，部分检查含重叠场景。

其中覆盖：草稿关闭重开、生成中切换客户、生成中编辑保护、失败与重试、个人／团队标题隔离、新增文字、模拟选图收起浮窗、跟进处理／忽略／稍后恢复、侧栏外聊天滚动、指导内容保留、小屏大字深色与模拟键盘组合。脚本未发现浏览器异常。

审计期间已修复：生成结果归属错误、个人改名改变团队标题、指导内容随布局丢失、稍后与已处理混用、键盘遮挡底部按钮、切换尺寸重置字号。最终文件哈希保存在证据目录。

## 原型模拟范围

聊天、客户、海报和生成结果都是合成示例；生成在浏览器本地模拟。文本复制使用浏览器剪贴板，权限不足时提供手动复制；图片复制和分享仅演示反馈，导入图片使用内置教学海报。工作号选择目前为单账号展示。主要业务数据保留于本页会话，重新加载会重置。

本次交付用于评审交互与布局。原生 Android、真实模型、系统相册和微信分享将在 APP 实现阶段验证；本次未更新工作机或生产服务。

## 预览

- [回复面板](evidence/interaction-prototype-v1-20261102/final-reply.png)
- [物料侧栏](evidence/interaction-prototype-v1-20261102/final-materials.png)
- [跟进列表](evidence/interaction-prototype-v1-20261102/final-follow.png)
- [小屏／大字号／深色／键盘](evidence/interaction-prototype-v1-20261102/small-dark-keyboard.png)

## 后续状态：原生 v31 预览

用户认可原型后，已迁入首轮 Android 界面，主实现为 GPT-6.1 Terra，主代理审计与打包。安装包为 [SASALELE-1.10-ui-preview-v31.apk](D:/Dev/AndroidProjects/SASALELE/deliverables/SASALELE-1.10-ui-preview-v31.apk)。

编译、单元测试、合成客户草稿保留测试及模拟器窗口／键盘／返回／小屏适配已完成；真实微信工作机业务验收尚待进行，未全员发布。详见 [原生实现与验证记录](D:/Dev/AndroidProjects/SASALELE/docs/NATIVE-UI-V31-IMPLEMENTATION.md)。此状态更新不改变上文浏览器原型证据的适用范围。

## 当前版本：v32 窗口切换修正

已修复 v31 打开跟进、设置时内容先挤入窄工具条再展开的问题，并修正关闭后窗口高度与位置恢复。最终 release 包已通过模拟器录屏、打开关闭边界、键盘及小屏检查；尚未安装到真实工作机或全员发布。安装包及证据详见 [v32 实现与验证记录](D:/Dev/AndroidProjects/SASALELE/docs/NATIVE-UI-V32-TRANSITION.md)。

## 当前开发版本：v34 客户识别与画像交互（2026-11-03）

用户批准名称点选、客户确认简化及画像主界面信息精简，Terra6.1实现、主代理审查。已完成构建、单元测试及正常/小屏合成接口流程；真实工作机待验收，未批量发布，员工发布版本仍为v33。详见 [v34记录](D:/Dev/AndroidProjects/SASALELE/docs/CUSTOMER-IDENTIFICATION-V34.md)；[APK](D:/Dev/AndroidProjects/SASALELE/deliverables/SASALELE-1.11-customer-flow-v34.apk)。

### 2026-11-03：v36 深色图标与拖动修复

修复工具条图标固定深灰与拖动仅半屏的问题。Terra 实现，主代理审计并补充配置变化时手势边界更新。正式签名 APK 已构建；模拟器深浅主题、标准/小屏、展开/收起左右边缘 16 项检查通过。实体工作机未连接，真机验收和发布待进行；全量发布记录仍是 v35。

交付：D:/Dev/AndroidProjects/SASALELE/deliverables/SASALELE-1.11.1-theme-drag-v36.apk；详细证据：D:/Dev/AndroidProjects/SASALELE/docs/NATIVE-UI-V36-THEME-DRAG.md。


## 2026-11-04 后续状态索引

后续用户确认窗口交互流畅，并在最小化恢复修复后反馈正常，随后授权批量发布；最近批量记录为v35，实际版本覆盖仍待确认。v37已装单台，包含后续功能；不能将此替代v36深色和拖动的实体机专项验收。详见[当前状态与阶段复盘](PROJECT-STATUS-AND-RETROSPECTIVE-20261104.md)。
