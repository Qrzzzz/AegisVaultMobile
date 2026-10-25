审计基线：`ae4d6e41498c214e2bcea5940850d7b1ce3f33e3`，正式版 v1.5。优先级 P2，涉及解密明文的视觉泄露。证据类型：确定的源码缺失 + Android 官方平台行为；本轮没有设备截图/录像。

## 问题与证据

[复制结果直接使用 ClipData.newPlainText 并 setPrimaryClip](https://github.com/Qrzzzz/AegisVaultMobile/blob/ae4d6e41498c214e2bcea5940850d7b1ce3f33e3/app/src/main/java/com/aegisvault/mobile/ui/AegisVaultRoute.kt#L120-L135)，没有给 ClipDescription.extras 设置 EXTRA_IS_SENSITIVE。这个统一出口既复制密文，也复制 AES 解密结果和 Base64 解码后的潜在敏感明文。

[Android 官方剪贴板文档](https://developer.android.com/develop/ui/views/touch-and-input/copy-paste#sensitive-content)说明 Android 13+ 会显示复制内容预览，敏感内容应在发布剪贴板之前添加该标记，以便系统隐藏预览。应用窗口的 FLAG_SECURE 不能代替剪贴板条目的敏感标记。

## 设备复现步骤（待执行）

1. 在有系统剪贴板预览的 API 33+ 设备打开应用；仅使用合成测试文本。
2. 解密一个含合成敏感明文的 AGV1，点击复制结果。
3. 检查系统复制预览是否明文显示，并检查剪贴板 ClipDescription 是否带敏感标记。
4. 开启 Protect screen 后重复，确认窗口保护与剪贴板预览是分别验收的。

## 修复与验收

- 为结果复制出口构造带 `ClipDescription.EXTRA_IS_SENSITIVE=true` 的 ClipData，再调用 setPrimaryClip；保守地标记全部结果亦可。
- API 33+ 断言实际 ClipDescription 标记存在，系统预览隐藏内容；API 26–32 保持兼容，复制文本仍可正常粘贴。
- 不把该标记描述为阻止所有剪贴板读取；保留既有清理和风险提示。
