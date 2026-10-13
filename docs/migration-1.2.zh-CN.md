# 旧 Android 文本的离线迁移

从 1.2 起，新版 App 只解密正式桌面 AGV1；1.1 及以前 Android JSON 型 AGV1、旧 AES、AK# 都需要迁移，不能直接粘贴到新版 App 解密。

先保留原始密文的独立备份，使用副本。`AK#` 在密文本身嵌入口令，知道该文本的人也知道口令；迁移不能撤销已经泄露的原始内容。不要把 AK# 放入命令参数、聊天、日志或网络服务，也不要复制到会同步的剪贴板。

在可信离线电脑准备 Python 3.11+、`cryptography>=42.0` 和仓库脚本 `scripts/migrate-legacy.py`。下载依赖是环境准备；**迁移操作本身不联网**。不要求桌面 App 或服务器。脚本只处理一个文本 token 的显式恢复；这不是 App 文件加解密或批处理功能。

把一个旧 token 保存到本地 `old-token.txt`（只包含 token），在支持隐藏口令输入的交互终端执行：

```powershell
python scripts/migrate-legacy.py --format android-json --input old-token.txt --output new-token.txt
```

- Android JSON `AGV1.`（1.1 及以前）：`--format android-json`
- 旧 AES（标准 Base64 nonce+ciphertext/tag）：`--format aes`
- 旧 `AK#口令#密文`：`--format ak`；此格式从 token 提取旧口令，不另提示旧口令

旧 AES/JSON 提示旧口令；所有格式均提示新口令并再次确认。输入隐藏，口令不作为命令参数。脚本拒绝口令回显 fallback，请使用正常交互终端，不重定向终端口令输入。对 AK 建议新口令与旧口令完全不同。输出文件必须不存在；脚本不能覆盖输入或已有输出。

解密认证、严格 UTF-8 和资源检查成功后，才生成新随机 salt/nonce 的正式 AGV1 并写出 token。控制台仅显示成功或固定错误类别，不显示原文/密文/口令。把 `new-token.txt` 的 token 先分别在新版 Android 和桌面版用新口令解密，核对完整原文，再考虑删除旧副本；保留原始备份直到确认恢复无误。

错口令、损坏、格式不符或超预算返回失败且不生成新 token。工具保留 Android 1 MiB 正文/口令、2 MiB 输入、64 MiB scrypt/4,194,304 work 预算，JSON 旧格式保留 16 KiB 元数据和32层容器限制。不会静默降参、自动猜格式或尝试其他口令。失败时保留原件，不反复放宽检查。

Python/密码学实现不能保证即时擦除所有内存副本；请退出工具并妥善保护原始和迁移后的文本文件。测试材料为公开人工样本，真实敏感数据不得放入仓库测试或互通证据。
