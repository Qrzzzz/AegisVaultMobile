# Mobile 1.2 合约 / Contract

versionName **1.2**, versionCode **4**；发布说明见 [v1.2](releases/v1.2.md)。基于当前 main `b274ffa991190ca30d1361c1b2afb0a74616c945`，保留 1.1 生命周期及资源修复。公开 v1.1 的应用源是 `34c582a2f6b46309f64a632471749a3f71d695f2`，其后的 main 提交仅修复发布自动化。

## 正式文本协议 / Formal text protocol

冻结桌面源 `c6664e2b7e6a11b7dfb55f0881136131e1d2bc74`，原始合约及摘要见 `contracts/desktop-c6664e2/`。生产加密：`AGV1.` + 无填充 URL-safe Base64(`AGVTEXT\x01` + uint32 BE header length + UTF-8 JSON header + AES-GCM ciphertext/tag)。Nonce 12 bytes，tag 16 bytes。header 包含 `algorithm=AES-256-GCM, created_at, format=aegisvault.text, kdf={length,n,p,r,salt,type=scrypt}, nonce, version=1`，salt/nonce 为标准 Base64。默认 N=32768,r=8,p=1,key=32,salt=16。

Writer 按键排序、紧凑 JSON、UTF-8，不转义正常 Unicode；实际 production 入口始终生成 SecureRandom salt/nonce 和 UTC seconds timestamp。内部低层测试入口接受固定输入，以冻结向量逐字节核对，不存在生产固定随机数开关。Reader 使用收到的**原始 header bytes**作为 AAD；允许合法非规范 JSON 排版但绝不通过重新序列化来认证。

`ProtocolJson` 实现独立 RFC 8259 语法，生产解析不依赖平台 `org.json`。拒绝重复键（包括 escape 后重复）、非有限/溢出数字、异常嵌套、错误真实类型、尾随内容、注释、单引号、控制字符和非法 UTF-8/surrogate。整数字段只接受 Long 表示的 JSON 整数并检查 Int 范围；`1.0`/`1e0`/bool/string/null 不作为整数。严格检查 format/version/algorithm、timestamp 类型/非空/长度/NUL、KDF、实际盐长和固定 32-byte key；不会降低传入 KDF 成本以解密。认证后的非法 UTF-8 返回 UTF8_INVALID，不能替换字符。

生产 decrypt 不再含旧 SHA256 派生、AK parser、usedLegacyAk 或自动 fallback。旧 Android JSON 同前缀、AES/AK 文本由 MIGRATION 中英提示引导独立工具；未知正式版本、损坏正式协议由 PROTOCOL 拒绝。迁移工具与 App 不共享旧 reader 或依赖。

## 预算 / Budgets

| Boundary | Mobile 1.2 | Reason |
|---|---:|---|
| Plaintext / password UTF-8 bytes and UTF-16 units | 1,048,576 | 保留 1.1 暂行设备策略 |
| Encoded input including prefix, before trim | 2,097,152 characters | 保留输入总预算 |
| Raw UTF-8 header | 65,536 bytes | 与冻结桌面协议头上限一致，脱离正文单独校验 |
| JSON container nesting | 32 | 分配对象前控制递归深度 |
| Ciphertext including GCM tag | 1,048,592 bytes | plaintext +16 |
| Binary package | 1,114,140 bytes | 8 magic +4 length +65,536 header +1,048,592 cipher |
| Worst writer token | 1,485,525 characters | 5 +ceil(4*binary/3)，移除双层 Base64 约束 |
| scrypt memory `128*N*r` | <=64 MiB | 未获得真机证据，不扩大到桌面 256 MiB |
| scrypt work `N*r*p` | <=4,194,304 | Long 预检 |

Body/header/token 都在解码、派生或 AES 分配前预检；正文仍只承载 Mobile 1 MiB 明文。**格式互通不等于支持桌面所有最大资源参数**：桌面有效但超 Android 正文/内存预算返回明确 RESOURCE。没有导入桌面 IPC、JSON Lines 或文件功能；64 MiB 不是峰值测量，也不是所有设备可成功分配的保证。

Base64 工具及空白语义保留现状，仅正式协议接入处强制有效 UTF-8。完整 strict/ignore-whitespace/padding 语义属 1.3；设置恢复与整体验收属 1.4。generation、取消和过期提交丢弃保持 1.1 合约。

## 独立迁移与证据 / Migration and evidence

工具：[migrate-legacy.py](../scripts/migrate-legacy.py)。使用 1.1 冻结人工样本 `app/src/test/resources/android-historical.json`，不替换原样本。历史 JSON 使用独立受控 reader，16 KiB metadata/32 nesting；旧 AES/AK 仅显式指定格式，SHA256 key 仅用于数据恢复。所有恢复都先认证并严格验证文本/资源，再输出新正式 token，工具自己回验 AES-GCM 后才创建输出。错误不产生成功输出，不覆盖原件，不记录 token/密码/明文。

[中文迁移说明](migration-1.2.zh-CN.md) / [English migration instructions](migration-1.2.en.md)。实际桌面 CryptoService 双向交换、迁移和 Android 构建证据见 [validation-1.2.md](validation-1.2.md)。冻结向量与原始头 AAD 回归测试不能代替实体设备测试。
