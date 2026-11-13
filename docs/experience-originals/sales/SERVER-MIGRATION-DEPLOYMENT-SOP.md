# SASALELE 服务器迁移与部署 SOP

编写日期为 2026-11-07，北京时间。适用对象为负责 SASALELE Linux 服务器的管理员。

本文依据仓库 Compose、Dockerfile、Caddyfile、部署说明、历史安装脚本和项目部署回执整理。首次部署及多次升级有历史执行记录；整机迁移流程是基于现有架构整理的操作方案，尚待目标环境演练。

## 1. 确定操作类型

| 场景 | 操作路径 |
| --- | --- |
| 新建独立环境 | 准备资源、安装 Docker、导入镜像、配置服务、签发设备、业务验收 |
| 原机发布功能 | 校验对应更新包、备份、执行安装、核对版本、业务验收 |
| 已有业务迁到新服务器 | 盘点、预备目标机、停写、完整备份恢复、切换域名、业务验收 |

历史部署参考值如下。必须在操作前重新核对目标机器及目录，错误套用可能操作其他环境。

| 项目 | 历史值 |
| --- | --- |
| 系统 | 阿里云 Ubuntu 22.04，linux/amd64 |
| 管理用户 | admin，通过 Workbench 或 SSH |
| 服务域名 | xiaoshou.yilihuashi.com |
| 原公网 IP | [已脱敏] |
| 原资源规模 | 约 1.6 GiB 内存、40 GB 云盘 |
| 上传目录 | /home/admin/sasalele-upload |
| 原部署目录 | /home/admin/sasalele-release-20261014/project/modules/qm-sales-assistant/deploy |
| 私有配置 | /opt/sasalele-private/upstream.env |
| 容器业务数据目录 | /var/lib/sasalele |
| Compose 项目名 | sasalele-sales |
| 服务名 | api、edge |
| 共存服务 | sparql-proxy.service，监听 5000 |

资源规模仅为历史记录。规划新服务器时还要计入图片、数据库、备份、旧镜像及日志增长。

## 2. 操作前准备

登记源服务器、目标服务器、管理账号、域名、DNS 管理人、当前镜像 ID、发布包版本、维护窗口和回滚负责人。记录时间应带时区，统一使用北京时间。网络耗时由发起请求的机器计量，不用不同机器的时间差估算。

必须准备以下材料，缺失会造成启动或恢复不完整。

- 完整 API 镜像或可构建的完整代码包，以及版本与 SHA-256。
- 实际部署的 compose.yaml、compose.prebuilt.yaml、Caddyfile、deployment.env 和其他覆盖文件。
- 上游私有配置及其引用文件，包含方舟、模型服务等实际依赖。
- 业务数据卷、Caddy data/config 卷、其他 bind mount 文件。
- 实际启用的知识包、实验配置、定时器、环境文件、运维脚本。
- 一台有效授权的工作机、一个已接入方舟且近期聊天可查的工作号与客户。

密钥、设备配置和数据通过受控通道传输。普通文档和 Git 记录位置与版本。Android 签名材料由发布管理员另行保管。

历史功能更新包常依赖原目录、基础镜像 sasalele-sales-api:20261014 和私有文件。必须审查 install.sh 前置条件后执行，避免把增量更新包用于缺少基础环境的新服务器。

## 3. 服务器预检与 Docker

以下命令由管理员在目标 Linux 服务器 Bash 中执行。

```bash
whoami
uname -m
free -h
df -h /
sudo -n true
sudo ss -lntup
systemctl list-units --type=service --state=running --no-pager
command -v docker
command -v curl
```

独立 Caddy 方案需公网 TCP 80/443。检查云防火墙的实际规则、操作系统防火墙和监听进程。SSH 按管理入口配置。API 的 8876 通过容器网络供 Caddy 访问。原机 5000 属于另一个服务，应单独登记其归属。

必须确认 80/443 可用；若已有入口代理，应先适配现有入口，直接运行当前 Compose 会造成冲突。

历史 Ubuntu 安装路径如下，软件包是否可用以目标机器输出为准。

```bash
sudo apt-get update
apt-cache policy docker.io docker-compose-v2
sudo apt-get --simulate install docker.io docker-compose-v2
sudo env NEEDRESTART_MODE=l apt-get install -y docker.io docker-compose-v2
sudo systemctl enable --now docker
sudo docker version
sudo docker compose version
```

Compose 必须支持覆盖文件中的 !reset，后续以 config --quiet 验证。系统提示重启需求时另行安排维护窗口。

## 4. 首次部署

### 4.1 上传和校验

上传到 admin 可写目录。以下大写占位符必须替换为本次发布的真实值；文件名和 SHA-256 必须与交付清单一致，校验失败应重新取得文件。

```bash
mkdir -p /home/admin/sasalele-upload
cd /home/admin/sasalele-upload
printf '%s\n' 'EXPECTED_SHA256  RELEASE.tar.gz' | sha256sum -c -
tar -tzf RELEASE.tar.gz
mkdir -p /home/admin/sasalele-release-RELEASE
tar -xzf RELEASE.tar.gz -C /home/admin/sasalele-release-RELEASE
```

查看归档结构后再解压，确认目标目录。Workbench 曾发生 OSS 内网连接导致传输失败，可改用已授权的 SSH/SCP 通道。

### 4.2 导入镜像

小内存服务器可使用构建机预先构建并验证的镜像。架构必须与目标匹配。迁移已有环境时导出实际运行镜像，避免误用最初基础版本。

```bash
sudo docker load -i /home/admin/sasalele-upload/API_IMAGE.tar.gz
sudo docker image inspect API_IMAGE_TAG --format '{{.Os}}/{{.Architecture}} {{.Id}}'
```

Caddy 使用本次交付 Compose 中的精确镜像摘要，提前拉取或离线导入。在 deploy 目录保存 compose.prebuilt.yaml，替换 API_IMAGE_TAG。

```yaml
services:
  api:
    image: API_IMAGE_TAG
    build: !reset null
```

源码构建需保留 Dockerfile 要求的仓库相对目录，并检查构建资源、基础镜像与依赖下载条件。当前 Dockerfile 的上下文是仓库根目录。

### 4.3 安装私有配置

SECURE_SOURCE_ENV 必须替换为通过受控方式传入的真实配置文件。

```bash
sudo install -d -o root -g 10001 -m 0750 /opt/sasalele-private
sudo install -o root -g 10001 -m 0640 SECURE_SOURCE_ENV /opt/sasalele-private/upstream.env
sudo stat -c 'UID=%u GID=%g 权限=%a' /opt/sasalele-private/upstream.env
```

必须让容器组 10001 可读取文件及父目录，否则配置无法加载。组名显示 UNKNOWN 时核对数值 GID。历史正确输出为 UID=0、GID=10001、权限640。

首次上线曾采用目标机生成密钥、加密配置包传输、目标机解密安装的流程。复用时必须持有对应脚本及匹配密钥，并检查解密后的权限。

在 deploy 目录创建 deployment.env。

```bash
umask 077
cat > deployment.env <<'EOF'
SASALELE_DOMAIN=SERVICE_DOMAIN
SASALELE_CERT_EMAIL=CERT_EMAIL
SASALELE_UPSTREAM_ENV_FILE=/opt/sasalele-private/upstream.env
EOF
```

替换真实域名与证书联系人。服务配置、数据库和设备文件都可能包含秘密信息，避免在排障输出中打印其全文。

### 4.4 DNS、HTTPS 和启动

域名 A 记录指向目标公网 IPv4；已有 AAAA 时也要核对实际去向。保留原记录值用于回滚。独立 Caddy 需满足公网证书签发和访问条件。

在 deploy 目录定义 Bash 数组。后续 dc 命令都要求位于该目录并已定义此数组；如实际另有覆盖文件，必须加入同一组命令，避免启动错误版本。

```bash
dc=(sudo docker compose --env-file deployment.env -f compose.yaml -f compose.prebuilt.yaml)
"${dc[@]}" config --quiet
"${dc[@]}" up -d --no-build --pull never --wait --wait-timeout 120
"${dc[@]}" ps
curl --noproxy '*' --connect-timeout 5 --max-time 20 https://SERVICE_DOMAIN/_health/live
```

alive 证明进程可响应，继续执行业务验收。失败时依次检查镜像、配置权限、挂载、日志、DNS、端口。对外分享日志前脱敏。

## 5. 发机和业务验收

在部署目录使用同一 dc 命令签发测试工作机。

```bash
"${dc[@]}" exec -T api python project/modules/qm-sales-assistant/tools/manage_work_devices.py \
  --data-dir /var/lib/sasalele issue --label TEST_DEVICE_ASSET \
  --service-url https://SERVICE_DOMAIN
```

输出包含 device_id 和容器内 private_file 路径。用 Compose cp 取回受控目录，将文件交给对应工作机，从“管理员发机配置”导入。逐机签发，历史默认有效期为90天，以实际签发结果为准。传输副本验证后按受控流程清理。

必须先准备观测记录再请操作员测试，记录 APP 版本、镜像 ID、工作号、客户及时间。

1. 外网 HTTPS 与证书校验成功。
2. 工作机检查服务通过，授权工作号范围正确。
3. 当前工作号与客户匹配正确，近期聊天可读。
4. 画像生成、再次读取和失败反馈符合预期。
5. 帮我回、润色、销售指导各完成一次，验证展示、编辑、复制。
6. 跟进列表可加载，忽略及恢复可用。
7. 团队物料可读，受控测试素材上传及个人标题修改可用。
8. API 重启后授权、已确认对象和已保存数据可继续使用。
9. 核对知识实验开关、预生成任务等发布状态。
10. 原机共存服务按原用途核验。

迁移验收应包含原已授权工作机；只测新签发设备无法验证旧授权是否恢复。

## 6. 原机升级

升级前记录运行镜像 ID、Compose 文件组合、Caddyfile、配置版本与备份位置。校验对应发布包，再审查安装脚本的目录、基础镜像和私有文件要求。

历史脚本通常构建新镜像、使用 SQLite backup API 备份数据库、更新 API、必要时重启 edge，最后输出功能版本和健康结果。必须核对备份实际范围；恢复团队物料还需要图片文件。

按本次发布说明执行 install.sh，保存退出状态、镜像 ID 和功能版本。Healthy 和 alive 之后仍需验证受影响业务。旧版构建器弃用及 Caddy 格式提示属于工具维护信息，业务结果需结合退出状态与实际验证判断。

回退使用提前保存的旧镜像与旧配置。必须核对旧代码与现有数据库结构兼容性，再决定是否恢复数据快照。

## 7. 整机迁移

### 7.1 盘点源站

在源服务器实际部署目录定义 dc，记录镜像与挂载。

```bash
api_id=$("${dc[@]}" ps -q api)
edge_id=$("${dc[@]}" ps -q edge)
sudo docker inspect "$api_id" --format '{{.Image}} {{json .Mounts}}'
sudo docker inspect "$edge_id" --format '{{.Image}} {{json .Mounts}}'
```

必须按 Mounts 核对卷名。当前默认名称是 sasalele-sales_sales-data、sasalele-sales_caddy-data、sasalele-sales_caddy-config，实际部署参数可能改变它们。

业务卷需整体保留，包括授权、缓存、画像、跟进、团队物料与行为数据、实验状态等实际文件。知识包和其他 bind mount 单独归档。卷内 backups 目录还需要复制到另一台机器，才能覆盖源机故障场景。

将实际 API/edge 镜像分别 docker tag 为迁移专用标签，用 docker save 导出。给镜像和归档生成 SHA-256，记录与镜像 ID 的对应关系。

### 7.2 停写和一致性备份

先完成目标机器环境准备及镜像传输。维护窗口开始后暂停实际启用的预生成定时器和其他写任务，等待进行中的生成任务结束，再停止源站。

```bash
"${dc[@]}" stop edge api
```

必须停止数据写入后执行全卷备份，直接拷贝运行中的 SQLite、WAL 和图片可能得到跨时间状态。该流程的业务停机持续到目标验收或源站回滚完成。

以下以本机已导入的 Python API 镜像作为 tar 辅助容器，先验证镜像有 tar。ACTUAL_VOLUME、ABS_BACKUP_DIR、API_IMAGE_TAG 和 VOLUME_BACKUP.tar.gz 均需替换，对三个卷分别执行并使用不同文件名。

```bash
sudo docker run --rm --network none --user 0 --entrypoint tar \
  -v ACTUAL_VOLUME:/source:ro -v ABS_BACKUP_DIR:/backup \
  API_IMAGE_TAG -czpf /backup/VOLUME_BACKUP.tar.gz -C /source .
```

备份目录预先建立，权限0700，归档仅管理员可读。校验并检查归档可列出。配置、秘密文件、外部挂载、数据卷及镜像作为同一迁移批次保存。

### 7.3 目标恢复

1. 导入镜像，恢复部署文件及私有配置，修正机器相关路径。
2. 使用相同项目名与卷配置执行 dc create 创建容器及空卷，暂不启动。
3. 核实目标卷为空、名称正确，再逐卷恢复。
4. 保留数值 UID/GID，业务目录对10001:10001可用。避免把全部备份内容统一改成同一属主。
5. 恢复独立知识包及其他挂载文件，核对定时器引用的 Compose 文件、env 和覆盖文件。
6. 启动目标 API/edge，核验日志与数据。

必须恢复到新建的空目标卷；覆盖已在使用的卷会破坏数据。恢复示例与备份一一对应。

```bash
sudo docker run --rm --network none --user 0 --entrypoint tar \
  -v TARGET_EMPTY_VOLUME:/target -v ABS_BACKUP_DIR:/backup:ro \
  API_IMAGE_TAG -xzpf /backup/VOLUME_BACKUP.tar.gz -C /target
```

保留原域名可让手机继续使用已有地址，原设备授权依赖业务数据恢复。更换域名时安排客户端配置更新和新域名证书验证。

### 7.4 切流

目标已有有效域名证书时，可在切 DNS 前指定目标 IP 验证 HTTPS。

```bash
curl --resolve SERVICE_DOMAIN:443:TARGET_IP \
  --connect-timeout 5 --max-time 20 https://SERVICE_DOMAIN/_health/live
```

证书尚未可用时先检查内部服务，切流后补齐 HTTPS 验收，保持证书校验开启。

更新 DNS，核对 A/AAAA 和不同网络的访问结果，执行第5节业务验收。对比设备、画像、跟进、团队素材及实验状态，抽查对象与图片内容。验证后恢复需要的后台任务。

DNS 缓存期间旧站保持停写，避免两个独立数据库同时接受新数据。旧机和完整备份保留到约定观察期结束。

## 8. 回滚

| 情况 | 处理 |
| --- | --- |
| 切流前失败 | 停止目标，恢复源站及原任务 |
| 切流后目标尚无新增数据 | 恢复 DNS 原值，停止目标后启动源站 |
| 目标已产生新增数据 | 先停写并保存目标快照，制定合并或明确数据回退范围，再回滚 |
| 原机升级后数据库结构变化 | 检查旧镜像兼容性；需要恢复备份时明确备份之后的数据影响 |

必须先处理新数据去向再回退快照，否则可能遗失新增授权、素材及业务记录。DNS 回滚也有缓存期，记录恢复时间和实际访问结果。

## 9. 常见问题

| 现象 | 优先核查 |
| --- | --- |
| Workbench 下载失败 | 传输任务 OSS 网络，可用授权 SSH/SCP |
| 容器健康、业务失败 | 设备授权、方舟配置、工作号范围、模型服务 |
| 模型429 | 实际上游错误码与额度，区分额度不足和请求限流 |
| 微信有聊天、方舟查不到 | 工作号/客户对应关系、同步范围与时间 |
| 原手机授权失效 | 数据卷是否完整、凭证是否到期或撤销 |
| 物料有记录、图片无法查看 | 图片是否迁移、文件权限及挂载 |
| 重启后呈现新环境 | 项目名、卷名、挂载路径 |
| HTTPS 失败 | DNS A/AAAA、端口冲突、edge 日志 |
| 更新脚本缺文件 | 写死目录、基础镜像、私有文件前置条件 |

## 10. 部署记录模板

- 操作类型、负责人、源/目标服务器与管理账号。
- 开始、停写、切流、验收时间，均带时区。
- 代码版本、镜像 ID、包 SHA-256。
- Compose 文件组合、项目名、实际挂载。
- 私有配置版本和受控位置。
- 备份位置、校验及恢复检查结果。
- 变更前后 DNS、实验开关和后台任务状态。
- 工作机版本、测试账号、逐项业务结果。
- 待处理项、回滚点、观察期。

## 11. 来源与适用范围

- [Compose](../deploy/compose.yaml)、[Dockerfile](../deploy/Dockerfile)、[Caddyfile](../deploy/Caddyfile)。
- [部署准备](../deploy/README.md)、[原始执行计划](PRODUCTION-EXECUTION-PLAN-20261013.md)。
- 历史更新脚本位于模块 private/team-materials-20261028/install.sh。
- 本项目首次上线及后续画像、跟进、团队物料、知识实验部署回执。

部署准备文档含最初筹备时期的资源待填写文字。执行时以本次盘点和运行配置为准。工作区的新实现需经发布验证后再登记为现网能力。
