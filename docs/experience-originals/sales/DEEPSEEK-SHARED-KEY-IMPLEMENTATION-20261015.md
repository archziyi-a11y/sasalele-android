# 复用既有DeepSeek话术密钥：实施与交付

2026-10-15，Asia/Shanghai。

## 配置纠正

用户此前已提供DeepSeek话术通路。原位置由tools/deepseek_relay.py与SIMULATOR-SETUP.md确认：当前Windows用户AppData/Local/QMYL/deepseek-key.txt。本轮安全核实该文件存在且非空，并在内存比较确认新销售配置使用同一密钥。未要求重新提供、未显示密钥值。

此前错误沿用了旧画像模块config/.env中的Kimi配置；Kimi的额度错误不应成为SASALELE闭环的必要阻碍。本轮新增sales_settings.py，仅覆盖销售服务模型配置，画像、帮我回和润色共用DeepSeek配置。原飞书画像作业和其他业务的Kimi配置未改，旧探针未改。

销售服务不再静默回退旧MKTCI模型凭据：

- 优先SASALELE_LLM_API_KEY，或SASALELE_LLM_KEY_FILE引用的服务端私有文件。
- Windows开发默认读取已有话术key文件（尊重QMYL_DEEPSEEK_KEY_FILE）。
- 模型默认沿用既有话术通路deepseek-chat，地址https://api.deepseek.com；本轮未进行模型比较。
- 生产环境必须显式挂载私有文件，缺失时提示模型未配置，不使用Kimi顶替。

## 实际结果

1. 真实DeepSeek＋纯合成输入：画像、帮我回、润色均成功。
2. 用户指定活跃客户：单一匹配，双标识/昵称/微信尾号核验通过。读取11条消息、其中7条文本；真实生成289字画像、134字帮我回草稿、24字润色草稿，全部返回成功。润色输入为测试者补充的一句合成原文，并非客户消息。结果仅在Git忽略的本地私有测试数据库保存。
3. 同一请求ID再次提交复用了已成功任务；它不是一次新的画像缓存检查，不能将该次0秒等待解释为模型推理时间。
4. 关闭并重新创建服务后，使用不同任务ID且禁止任何模型调用做缓存检查：一次源读取失败，第二次成功，cache_hit=true、model_calls=0。证明真实样本的画像持久缓存可复用；源读取失败记录未删除。
5. 服务端55项测试通过，包括配置隔离、缺失密钥不得回退旧凭据、文本读取、生成、429分类与旧画像作业回归。
6. Android实际FastAPI业务接口的合成测试10项通过，含连续5轮取消后重新生成。该验证来自上一诊断轮，不能当作DeepSeek生产HTTPS或真机通过。
7. 新版本11正式签名构建通过。旧签名证书保持一致。APK额外扫描确认不包含该DeepSeek密钥；源包扫描包含DeepSeek及既有已知秘密值，未发现明文。

真实数据覆盖为1位客户，不是全量画像，也不是画像质量评测通过。输出检查能看到对信息不足、需求/意向未知的表述；客户身份关系和建议仍须业务人工核对，不能据生成成功认定内容全部准确。

任务观测耗时为开发电脑time.monotonic从提交至终态：首次画像3.78秒、帮我回2.95秒、润色1.91秒；各一次，不作性能承诺，不与手机时钟比较。

## 容器配置与加密迁移

部署包引用服务器此前配置迁移所用公钥，RSA-OAEP-SHA256加密现有DeepSeek key，只携带密文。安装脚本先校验服务器/opt/sasalele-private/transfer-key.pem对应的公钥指纹与密文哈希，解密后直接写固定私有文件，不向终端打印。服务器解密与公钥匹配尚未实际执行验证。

目标私有文件为/opt/sasalele-private/sales-deepseek-key-20261015.txt，root:10001、0640；同内容支持重复执行，存在不同内容则停止而非覆盖。Compose只读挂载到/run/secrets/sasalele-deepseek-key。

本机Docker直接绑定Windows用户目录密钥时被映射为目录，不能用此结果判断Linux部署失效；未继续修改用户目录权限。改用项目目录中的合成密钥，模拟root:10001/0640后切到UID/GID10001，容器配置读取通过。它验证权限与配置机制，不是目标服务器的实测。

Compose合并config --quiet通过。本地镜像构建成功，但未更新服务器、未覆盖安装工作机。

## 产物

- 源码与加密配置包：D:/Dev/smart-customer-service/project/modules/qm-sales-assistant/private/deepseek-release-20261015/sasalele-deepseek-deploy-20261015.tar.gz
- 大小51,128字节；SHA-256：9dd47473bf085052ea069f33a417f3a1c324bd99021455cb03701413f73dff4d。
- 镜像sasalele-sales-api:20261015-deepseek，ID sha256:34139ee7ff4a799efe27d1bda329ebadd75e225e4af2dd2ca10f293409da1571。
- APK：D:/Dev/AndroidProjects/SASALELE/app/build/outputs/apk/release/app-release.apk，versionCode=11。
- APK大小64,101,815字节；构建产物文件时间2026-10-15 16:54:02.994850 +08:00，开发电脑文件系统时钟。
- APK SHA-256：82f297a5942aa26327c607ea65311a08f80f0205abea2b4ddda2ed3eccd05a37。

## 证据

目录D:/Dev/smart-customer-service/project/modules/qm-sales-assistant/private/chat-read-audit-20261015/：

- deepseek-active-customer/verification-metadata.json：实际画像/草稿任务元数据，无正文。
- deepseek-active-customer/cache-verification.json与cache-verification-2.json：保留失败与成功缓存核验。
- deepseek-active-customer/sales-generation.sqlite：包含真实画像与草稿的私有测试库，不提交Git、不打包。
- tests-deepseek.txt：55项通过。

合成真实模型结果：D:/Dev/smart-customer-service/project/modules/qm-sales-assistant/private/profile-reply-20261015/deepseek-synthetic/results.json。

交付检查：D:/Dev/smart-customer-service/project/modules/qm-sales-assistant/private/deepseek-release-20261015/artifacts.json、apk-verification.json、deepseek-apk-secret-scan.json、model-mount-verification.json。

## 下一步

上传该小型加密部署包，核对哈希，解密模型key；备份现有SQLite，基于已存在的20261014镜像离线构建，使用compose.deepseek.yaml仅更新api，保留设备授权和数据卷。必须先验证模型配置、HTTPS、实际生成与重启缓存，再覆盖安装版本11，进行PKW110 Android15真机验收。

现有工作机版本9仍可使用原功能；版本11不应在后端更新之前当作完整闭环验收包安装。无须解决旧Kimi账户额度来推进本功能。

本轮未恢复任何夜间批量或历史回补任务，未写飞书/方舟，未自动复制或发送微信消息。
