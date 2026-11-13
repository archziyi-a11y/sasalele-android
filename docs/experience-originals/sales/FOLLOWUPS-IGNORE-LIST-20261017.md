# 联系人忽略列表（2026-10-17）

## 实现

用户要求在提醒列表刷新/全部活跃右侧增加忽略列表，用于手动屏蔽同事等联系人，并可移回提醒列表。未自动按姓名识别或屏蔽[联系人已脱敏]等真实联系人。

服务 followups-v2：新增工作号隔离、联系人ID级 ignored_contacts 持久映射。已有可恢复的消息级忽略动作升级为联系人忽略。新消息、更换最后消息ID、定期同步、服务器重启均不解除忽略；即使离开15日活跃窗口也保留忽略名单和必要展示信息。GET /sales/followups 同时返回 ignored_contacts，活跃联系人状态被覆盖为ignored，不成为待查看或通知事件。POST action 新增 restore；恢复按联系人解除，不受最后消息ID变化阻挡，恢复后遵循实际最新方向，已回复联系人不伪造成未回复。超过15日的联系人解除忽略后不强行塞进15日活跃列表，下次有消息可正常进入。

APP v22（1.6-followups）：刷新列表/全部活跃/忽略列表三个入口使用可换行布局，适配面板宽度；普通列表和待查看排除已忽略联系人，忽略页提供移回提醒列表。全部活跃内的已回复等联系人也能直接忽略。忽略名单随服务数据存入原私有缓存，切号清空视图；同步时关闭包含非活跃忽略联系人在内的已有本机提醒，避免历史提醒残留。复制卡片名称与处理/稍后原功能保留。

## 验证

Python101项回归通过，覆盖新消息后仍忽略、恢复、重启、跨工作号隔离、15日过期保留、已回复恢复不虚构未回复、旧忽略迁移，以及已有生成和跟进逻辑。Android组合交互测试包括复制、忽略后消失、打开忽略页、移回提醒、已处理和切号隔离；初次与最后构建结果见evidence。仅合成数据，没有调用真实模型/方舟或替用户屏蔽真实联系人。

## 交付状态

必须先更新服务器，再安装v22；旧后端没有联系人忽略持久化与restore接口。当前服务器仍是followups-v1.1，实际工作机仍是v21，本轮尚未做生产更新或真机验收。服务器更新包复用business-v3/guidance-v2和现有key，备份SQLite及followups元数据后更新API，启动失败回滚镜像。

服务器包：D:/Dev/AndroidProjects/SASALELE/deliverables/sasalele-ignore-list-20261017.tar.gz
SHA256：b09cb8b0cea451f74e7cfe5bda5944b75adbdc0cd2e8dad26c81b61db813da0a
APK：D:/Dev/AndroidProjects/SASALELE/deliverables/SASALELE-1.6-followups-v22.apk
SHA256：4cc60d608aed8b629e272325b57926290b3152e9c19a025209f99fdf9d2bf8e1
签名沿用原正式证书。

## 部署回执

2026-10-17（北京时间）：用户提供Workbench回执，包校验通过，followups-v2镜像构建成功且API Healthy，business-v3/guidance-v2保留，HTTPS存活alive，既有代理active。SQLite备份目录 before-followups-v2-1792209178405799535。

随后本机ADB重新核对 PKW110 / [设备标识已脱敏]，APK校验值匹配交付值，保留数据覆盖安装返回Success。真实联系人忽略/移回操作仍需用户验收，未自动屏蔽[联系人已脱敏]或其他真实联系人。
