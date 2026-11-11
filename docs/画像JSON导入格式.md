# 本地授权画像JSON格式

必须只导入你有权用于本地识别的身份/画像文件。必填 account_id（工作号ID）、friend_id（客户ID）、display_name（显示姓名）必须是非空字符串；它们决定身份与显示名称。缺失时整批失败并保留原数据。不同双标识的同名客户不会合并。

```json
{
  "schema_version": 1,
  "customers": [
    {
      "account_id": "synthetic-account-a",
      "friend_id": "synthetic-client-1",
      "display_name": "合成示例客户",
      "aliases": ["合成别名"],
      "profile_summary": "仅用于说明格式的合成摘要",
      "profile_source": "合成示例，非真实客户",
      "company": "公司可选",
      "intent_level": "意向可选"
    }
  ]
}
```

schema_version支持1（省略时按1）。aliases为可选字符串数组；profile_summary/profile_source/company/intent_level/avatar_url为可选字符串。缺失如实显示暂无摘要、来源未提供等。头像仅接受已授权的本地content/file/android.resource URI，不访问远程图片；URI在本设备不可读时无法显示头像。

单次文件最多5MB、20000条；同一文件双标识重复、类型错误、缺少必填字段、非法JSON、空customers、不支持的版本均整批失败。导入以双标识合并：新键新增，已有键整条更新（可选字段缺失会变为空缺提示）；其他键保留。原始customer_ref等额外字段本轮不会用于匹配或资料展示。

工作号只是可选筛选，默认全量。姓名/别名规范化后精确匹配，不用部分姓名猜测。导入文件不会写回原文件；本地结果在应用私有目录identities/authorized-profiles.json，排除云备份，不接全量聊天记录搜索。
