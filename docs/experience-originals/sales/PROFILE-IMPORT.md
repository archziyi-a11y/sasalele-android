# 客户画像抽样导入

0.1.2 专用测试包已按用户要求内置两条已核验身份样本，手机无需导入。以下流程仅用于更新内置数据或追加样本。构建时将授权 JSON 复制到被忽略的 private/bundled-assets/customer-identity-sample.json，并显式传入 -PbundleCustomerSample；不把真实资产提交到源码库。内置 APK 本身仍可提取出身份信息，卸载不会删除手机另存的安装包文件。

本工具只读复用 mktci 的「客户画像」飞书 Bitable 表，并用方舟好友详情接口核验每条客户身份的 `wechat_account_id + friend_id` 双键。它不读取聊天正文生成新画像、不调用模型、不写回方舟或飞书。画像摘要为空时仍可导出身份和别名，`profile_summary` 保持空字符串，来源明确写为“方舟好友资料（无画像摘要）”。

默认目标为工作号尾号 [已脱敏]，最多 10 条；[已脱敏] 仅用于显式指定 `--account <工作号尾号>` 的跨工作号匹配探针。画像表中的每条候选需有完整账号与客户引用键，且 `getByWechatFriendId` 返回的账号与好友双键完全一致。若画像表无目标[客户标识已脱敏]行，脚本仍会单独查验已确认的目标好友 ID；只有方舟详情完成双键核验后才导出该身份。失败原因只输出计数，不打印真实画像正文或密钥。

## 运行

从仓库根目录运行：

```powershell
python project/modules/qm-sales-assistant/tools/export_profile_sample.py
```

可显式选择工作号做账号隔离样本：

```powershell
python project/modules/qm-sales-assistant/tools/export_profile_sample.py --account <工作号尾号>
```

成功时写到本模块被 `.gitignore` 忽略的 `private/profile-sample.json`。也可用 `--output` 指定仓库 `output/` 下的路径；程序拒绝写入其他位置，避免真实客户数据落入 Android assets 或源码。

输出结构：

```json
{
  "schema_version": 1,
  "customers": [
    {
      "account_id": "<真实工作号 ID>",
      "friend_id": "<方舟私聊好友 ID>",
      "customer_ref": "<已有画像中的客户引用>",
      "display_name": "<已有显示名或方舟备注/昵称>",
      "aliases": ["<已有显示名>", "<方舟备注>", "<方舟昵称>"],
      "profile_summary": "<仅来自既有客户画像表>",
      "profile_source": "mktci_feishu_customer_profile_bitable",
      "profile_updated_at": "<已有画像更新时间；缺失则 null>",
      "chat_verified": true
    }
  ]
}
```

`chat_verified` 表示方舟好友详情返回的双键通过严格核验；它不证明微信当前顶栏对应的就是该好友，也不代表摘要已人工复核或销售效果验收通过。`profile_updated_at` 仅使用画像更新时间，缺失保持 null；最后联系时间单独保存在 `last_contact_at`，不代替画像更新时间。

没有摘要时导出的身份仍可用于名称匹配探针，界面显示“暂无摘要”。表配置不可用或方舟好友详情无法双键验证时，脚本失败关闭；不得把测试会话聊天临时加工成画像摘要。此时 Android 的真实客户匹配仍待验证，可以使用清楚标记的虚构样本测试界面流程。
