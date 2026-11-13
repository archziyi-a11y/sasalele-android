# 知识灰度试验实施

2026-11-03，北京时间。目标：每次新业务任务50/50分配，客户/功能变更开启新任务；同任务编辑、重生成、网络重试保持分配。三功能独立分析。

约束：默认关闭，旧客户端不纳入；同组相同基础提示词，仅知识检索注入有差别。自动复制与主动复制分开。不得监听剪贴板；不新增保存聊天正文。事件失败不能改写已生成/复制成功事实。

验收：稳定分组、跨客户与设备隔离、关闭生效、事件去重、失败分母、重试任务归属、知识无命中仍在实验组、Android任务生命周期、生成到显示的同设备单调时钟耗时。生产发布前先完成联调；不以请求接受当作手机更新成功。

## 实现与验证结果

- 服务端每个设备/工作号/客户/功能/任务UUID形成独立任务分配，随机50/50写入SQLite，重生成和重启不重抽。旧客户端没有UUID保持原流程。两组使用相同基础提示词，差异为知识检索与注入。
- 开关文件knowledge-experiment.json默认关闭；关闭后新开始执行的任务不挂知识，已进入模型请求的任务不强行中断。报表将disabled排除出两组有效对照。
- 事件接收校验设备、工作号和客户归属，按event_id去重；保存散列账号/客户标识及数字指标，不新增记录草稿或聊天正文。后台元数据写入失败不改写成功生成事实。
- Android保持任务内编辑/重生成分组，切换客户/功能或“开始新任务”更换UUID。网络自动重试沿用request_id。复制与正在生成job分开，避免旧草稿复制错误归到新结果。
- displayed、manual_copy、regenerate、panel_closed、wait_cancelled；当前正式面板没有自动复制，不虚报auto_copy。关闭面板不是拒绝；未收到事件是未知。编辑比例本机后台计算Levenshtein/maxLen，最长4000字符。
- 本机事件队列上限200条、保留7天，固定ID重试；满额/过期/清数据仍可能丢事件。因此报表不得把缺失当不满意。
- 耗时：客户端elapsedRealtime从点击生成到结果显示；服务端created/updated是服务器wall clock，包含排队及执行，不作跨设备相减。
- 相关服务端回归46项通过；后补灰度路由校验后灰度专项4项通过。Android任务及编辑距离3项通过；assembleRelease、productionPreflight、lintVitalRelease通过。安装脚本bash -n通过。尚无生产Docker安装验证或真机端到端验收。

交付APK：D:/Dev/AndroidProjects/SASALELE/deliverables/SASALELE-1.12-knowledge-trial-v37.apk
SHA256：40aaddc991ccb4fc20fe9ba7798747fb19152bd0d336d16fa7e47708508e56a7
服务端包：project/modules/qm-sales-assistant/deliverables/sasalele-knowledge-trial-20261103.tar.gz
SHA256：efdceb5f5f749ddd56203da15a2f6c6e7269f35b60df634d00483a9c8eb6a062

## 部署顺序

先将服务端包上传到/home/admin/sasalele-upload。在服务器执行：

```bash
cd /home/admin/sasalele-upload
printf '%s\n' 'efdceb5f5f749ddd56203da15a2f6c6e7269f35b60df634d00483a9c8eb6a062  sasalele-knowledge-trial-20261103.tar.gz' | sha256sum -c - &&
mkdir -p /home/admin/sasalele-knowledge-trial-20261103 &&
tar -xzf sasalele-knowledge-trial-20261103.tar.gz -C /home/admin/sasalele-knowledge-trial-20261103 &&
sudo bash /home/admin/sasalele-knowledge-trial-20261103/install.sh
```

安装默认关闭试验，保留现有镜像为基础，不改Caddy和业务数据；备份数据库，启动失败恢复原compose。安装成功只证明服务正常，不代表知识效果合格。

随后在一台已配置工作机覆盖安装v37，检查生成、编辑、复制、重生成、切换客户、关闭面板的事件与任务归属。开启后先少量检查命中与生成正确性，再批量部署新版。只有收到新版客户端任务才纳入50/50，不能把旧版手机算作对照组。

管理命令（服务器）：

```bash
cd /home/admin/sasalele-release-20261014/project/modules/qm-sales-assistant/deploy
# 完成端到端核对后开启；立即关闭时把on替换成off
sudo docker compose --env-file deployment.env -f compose.yaml -f compose.prebuilt.yaml exec -T api python /app/manage_knowledge_experiment.py --data-dir /var/lib/sasalele on
# 最近7天观察摘要
sudo docker compose --env-file deployment.env -f compose.yaml -f compose.prebuilt.yaml exec -T api python /app/manage_knowledge_experiment.py --data-dir /var/lib/sasalele report --days 7
```

报告按功能与随机组统计任务、工作号、客户、成功失败、显示、主动复制和重生成；展示耗时保留同设备测量值。日报用于运行观察，不自动宣告赢家或调流量。尚需脱敏案例盲审才能评价内容质量；本实现没有自动采集聊天正文作为盲审样本。

历史交付时状态：未部署、未开启、未推送手机。后续更新见下。


## 2026-11-04 状态补记

根据用户生产安装回执，knowledge-trial镜像已部署且健康，安装时开关关闭。单台PKW110已覆盖安装v37并核对版本。用户提供的SQLite输出确认帮我回显示/复制/关闭、润色显示/重生成/编辑后复制事件，同一润色任务保持task与分组。所有提供样本effective=disabled。知识组有效注入、开启状态、销售指导及全员v37覆盖尚未确认；此前“尚无生产安装验证”仅适用于原交付时点。详见[统一状态](PROJECT-STATUS-AND-RETROSPECTIVE-20261104.md)。
