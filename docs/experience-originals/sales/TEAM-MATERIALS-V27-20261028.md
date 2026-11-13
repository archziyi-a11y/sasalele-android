# SASALELE 团队物料库 v27

日期：2026-10-28，北京时间。状态：本地实现、构建与自动化验证完成；生产服务器尚未部署，真实工作机联验尚未完成。

## 员工怎么用

1. 打开销售物料，默认进入「团队物料」。所有已授权工作机使用同一公司物料库，不按当前微信工作号拆分。
2. 文字图标可新增文本或完整链接，点击「发布到团队库」后共享。
3. 图片图标先导入图片到「本机物料」。点击对应卡片「上传到团队库」，可以修改团队物料标题，再确认上传。旧版本已有素材同样使用这个入口；不会自动上传。
4. 其他工作机重新进入团队物料或点击「刷新团队库」即可获取。当前版本没有实时推送。
5. 可按标题搜索，也可通过漏斗筛选图片、链接和纯文本。
6. 图片通过文件复制／分享，不把内部下载地址当图片内容发送。文字和链接仍可复制。
7. 收藏只影响这台手机。删除本机素材不会删除团队素材；公共素材由管理员下架。
8. 网络异常时保留上次完整同步的列表；已下载且仍在缓存中的图片可继续使用，未下载的图片需要联网。图片缓存上限约150MiB，旧图片可能被回收。

## 上线顺序与产物

**必须先部署服务器，再安装／批量分发 v27 APK。** 新界面依赖新增团队库接口，旧服务器没有这些接口；单独更新 APK 无法启用团队共享。

产物均在 `D:/Dev/AndroidProjects/SASALELE/deliverables/`：

| 文件 | SHA-256 |
| --- | --- |
| sasalele-team-materials-20261028.tar.gz | fceff932d8e07f7674f4c1b6846979d05bee7cce79f5f04552a36059fc91d818 |
| SASALELE-1.8-team-materials-v27.apk | 2b7fbe076eb99decd5b042b40b676fe7e0d75317e2157868c96ae83f7cc45e49 |

APK：versionCode=27，versionName=1.8-team-materials；延用正式签名，证书 SHA-256 为 `b71a52372487906863d4f2071cd211df2bc4bb5c667e39127f664587626f3957`。覆盖安装保留本机原有数据库，不需要卸载。

管理员把服务器压缩包上传到原服务器的 `/home/admin/sasalele-upload` 后，在该服务器 Workbench 终端执行：

```bash
cd /home/admin/sasalele-upload
printf '%s\n' 'fceff932d8e07f7674f4c1b6846979d05bee7cce79f5f04552a36059fc91d818  sasalele-team-materials-20261028.tar.gz' | sha256sum -c - &&
mkdir -p /home/admin/sasalele-team-materials-20261028 &&
tar -xzf sasalele-team-materials-20261028.tar.gz -C /home/admin/sasalele-team-materials-20261028 &&
sudo bash /home/admin/sasalele-team-materials-20261028/install.sh
```

脚本依赖原有部署目录、基础镜像和服务器上的密钥文件；缺少时停止。构建不拉取新基础镜像，不改模型密钥。更新前备份 SQLite 和原部署配置；更新 API、重启入口代理后检查健康状态、功能版本和原 SPARQL 服务。代理重启期间会有短暂连接中断。更新失败时尝试恢复原镜像和配置，最终结果仍应核对终端输出。

部署成功后，沿用方舟批量安装入口分发 APK。先在两台已授权工作机完成下述验收，再扩大分发。

## 管理员下架

在服务器执行，先列出物料 ID，再只下架指定条目：

```bash
cd /home/admin/sasalele-release-20261014/project/modules/qm-sales-assistant/deploy
sudo docker compose --env-file deployment.env -f compose.yaml -f compose.prebuilt.yaml exec -T api python project/modules/qm-sales-assistant/tools/manage_team_materials.py --data-dir /var/lib/sasalele list
```

```bash
sudo docker compose --env-file deployment.env -f compose.yaml -f compose.prebuilt.yaml exec -T api python project/modules/qm-sales-assistant/tools/manage_team_materials.py --data-dir /var/lib/sasalele remove --id 实际物料ID
```

手机刷新后不再显示该条目。当前采用逻辑下架，图片文件仍保留，不自动回收服务器空间；已复制到微信或保存的外部副本不受影响。没有向普通工作机开放删除接口。

## 实现与边界

- 服务端在既有持久卷中建立 `team-materials/materials.sqlite` 和 `team-materials/images/`；不依赖飞书，不调用方舟或模型来管理物料。
- 文字发布、图片上传、列表和图片下载均复用工作机授权。图片不是公开静态链接。当前是单公司单部署共享，不支持多公司租户隔离。
- 上传请求带幂等 ID；同次重试不会重复创建。列表分页同步，全部成功且版本一致才替换手机列表缓存，失败保留旧缓存。
- 图片支持 PNG/JPEG/WebP，单张不超过25MiB；服务端检查文件头、大小和内容摘要，手机上传及使用前解码。文件头检查不等同于完整图片安全扫描。
- 标题最多120字符，文本最多16000字符；最多5000条有效物料；服务端记录的图片累计大小上限2GiB。下架不会扣减累计大小；容量回收及运维后台留待后续。
- Caddy 为图片上传单独放宽请求体限制，其他业务接口继续保留原限制。
- 本机素材数据库不迁移、不自动共享；本机删除与团队下架分离。首次团队库为空是正常状态。
- 安装脚本备份数据库和相关配置，不是完整图片灾备方案；整卷备份与恢复演练尚未验收。

## 已完成验证

测试日期为2026-10-28；耗时来自本机测试工具，不能当作真实工作机或生产网络性能指标。使用合成文本、图片和隔离数据目录，未读取客户聊天，未调用真实模型。

- 服务端销售模块回归109项通过；最终分页优化后，团队库5项再次通过。
- 团队库 Android 三项测试通过：两个独立客户端缓存共享文字和图片、图片解码与摘要校验、收藏隔离、断网保留；本机不自动上传／取消不发布／确认发布且原素材保留；发布文本、类型筛选、无公共删除入口。
- 原有新增物料与筛选共三项回归单独运行通过。最初六项组合执行时一项出现 Compose SnapshotStateObserver 线程异常；未把该次组合记作全通过，后续分组验证。
- 最终 release/debug/test 构建成功；正式 APK 签名验证通过；更新镜像本地构建成功；Caddy 配置验证及安装脚本语法检查通过。
- Android 使用专用 Android36 模拟器 emulator-5558。不是两台真实工作机；双生产设备凭据的共享与撤销由服务端测试覆盖。
- 模拟器页面截图检查了标题、搜索、筛选、刷新和卡片布局；未宣称真机微信内完整体验验收。

证据目录：`docs/evidence/team-materials-20261028/`。

## 尚需真实工作机验收

1. A 工作机上传一条文字、一张图片；B 工作机刷新后看到同样标题和内容。
2. B 把团队图片复制／分享给微信文件传输助手，核对收到的是图片内容。
3. A 收藏不改变 B；本机删除不删除团队条目。
4. 图片已下载后断网，列表与该图片仍可用；恢复网络后刷新。
5. 管理员下架一条测试素材，两台手机刷新后均消失。
6. 旧版本本机素材、当前工作号及设备授权保持可用。

生产部署、方舟批量安装、真实微信图片复制／分享、上述双机验收尚未执行。没有物理工作机连接作为本轮验收证据。


## 2026-11-04 后续状态索引

用户已提供团队库及后续标题、别名版本的生产安装回执，并确认上传正常、第二台保存和改名可用；v30交互修正后反馈“没问题”。原文未部署状态为创建时历史状态，不代表当前状态。离线补传等未逐项提供真机回执的项目不据此整体勾选完成。详见[当前状态与阶段复盘](PROJECT-STATUS-AND-RETROSPECTIVE-20261104.md)。
