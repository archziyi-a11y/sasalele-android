# Android 历史研究补充

本目录是独立研究材料，不覆盖、不接入当前 Android 应用。`source/` 保存按历史清单 SHA-256 精确匹配的 16 个历史版本；它们与当前发布版本不同，不能当作当前实现。原清单另外 29 项与当前发布源码一致，无需重复复制。45 项源码现均有精确对应。

`xml/` 保存四份原始 UI 层级 XML。原有研究副本将通用 UI 文案和无障碍描述全部替换成占位符，丢失了界面语义；复查四份 XML 的全部元素属性后，保留通用文案。未发现实际服务地址、账号值、凭据或具体客户数据；`com.example.sasalele` 是应用包名。该判断仅针对这四份文件，不代表其他资料可以直接公开。

逐项来源目录短名、哈希、去向和处置见 [MANIFEST.json](MANIFEST.json)。路径以仓库根目录为基准；`not-found` 的目标为 `null`。补充文件已逐项核对源与目标 SHA-256；此处不表示编译、运行或生产验收通过。

## 2026-11-13 找回的历史版本

以下 8 项此前未找到，扩大搜索后在 `SASALELE-business-20261013/android` 备份中全部找到，逐文件 SHA-256 与历史清单完全一致，无需换行转换。现已复制到 `source/` 下对应路径；原备份未修改。源码内模拟客户、模拟报价和公共占位图片属于演示内容，不是实际客户资料。此补充不改变当前应用代码，也不代表历史版本已重新编译验证。

- `app/src/main/java/com/example/sasalele/AcquisitionUi.kt`
- `app/src/main/java/com/example/sasalele/CapturePermissionActivity.kt`
- `app/src/main/java/com/example/sasalele/FloatingWindowContent.kt`
- `app/src/main/java/com/example/sasalele/FloatingWindowService.kt`
- `app/src/main/java/com/example/sasalele/MainActivity.kt`
- `app/src/main/java/com/example/sasalele/ScreenCaptureService.kt`
- `app/src/main/java/com/example/sasalele/data/CaptureSession.kt`
- `app/src/main/java/com/example/sasalele/data/ImageTextRecognizer.kt`
