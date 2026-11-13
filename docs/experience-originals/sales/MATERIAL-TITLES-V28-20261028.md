# 物料标题修改 v28

2026-10-28（北京时间）。本地实现与正式构建完成，服务器尚未更新，手机尚未安装本次更新。

- 本机物料卡片新增「修改标题」，修改本机数据库记录，不影响已上传的团队版本。
- 团队物料卡片新增「修改标题」，明确提示保存后其他工作机刷新可见；所有已授权工作机可修改。公共删除仍只由管理员执行。
- 标题去除首尾空格，不允许空标题，最多120字符。不重新上传图片，不改变物料ID、收藏或图片摘要。
- 修改带原始标题；原始标题与服务器不一致时返回409，要求刷新，避免无提示覆盖他人修改。
- 同步版本摘要包含有效条目的ID与标题，分页期间标题变化会触发保留旧列表、重新刷新。

服务端6项物料测试通过，包含双生产凭据的共享改名、无凭据拒绝、冲突拒绝、重启持久化、图片内容保留。release构建成功，正式签名一致，APK版本核对28／1.8.1-material-titles。本轮尚未完成新增入口的模拟器或真实工作机交互验收，不把构建通过当作完整验收。

交付目录：`D:/Dev/AndroidProjects/SASALELE/deliverables/`。

- `sasalele-material-titles-20261028.tar.gz` SHA-256：`0209935587dabf7c462ca151d84d137fc767f806b2124352552c53ae7910a122`
- `SASALELE-1.8.1-material-titles-v28.apk` SHA-256：`c360c3c63c2d14d240870cca183d286813b0eb1cfe733e923d7778a8058b4f6d`

必须先更新服务器，再覆盖安装APK，否则团队标题保存接口不可用。本机标题与团队标题是独立副本，修改一个不会自动修改另一个。

把服务器压缩包上传到 `/home/admin/sasalele-upload`，在原服务器终端执行：

```bash
cd /home/admin/sasalele-upload
printf '%s\n' '0209935587dabf7c462ca151d84d137fc767f806b2124352552c53ae7910a122  sasalele-material-titles-20261028.tar.gz' | sha256sum -c - &&
mkdir -p /home/admin/sasalele-material-titles-20261028 &&
tar -xzf sasalele-material-titles-20261028.tar.gz -C /home/admin/sasalele-material-titles-20261028 &&
sudo bash /home/admin/sasalele-material-titles-20261028/install.sh
```

安装脚本沿用团队库v27的配置校验、数据库备份与失败回滚流程；本次镜像标签为 `sasalele-sales-api:20261028-material-titles-v1`。完成后须实测本机图片改名、团队图片改名、另一台刷新显示、图片复制仍可用。
