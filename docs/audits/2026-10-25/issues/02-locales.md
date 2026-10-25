审计基线：`ae4d6e41498c214e2bcea5940850d7b1ce3f33e3`，正式版 v1.5，minSdk 26。优先级 P2。证据类型：源码及 Android 官方 API 合约，本轮未执行设备 UI 测试。

## 问题与证据

[MainActivity 继承 ComponentActivity](https://github.com/Qrzzzz/AegisVaultMobile/blob/ae4d6e41498c214e2bcea5940850d7b1ce3f33e3/app/src/main/java/com/aegisvault/mobile/MainActivity.kt#L4-L13)，但唯一生效路径是 [Route 中调用 AppCompatDelegate.setApplicationLocales](https://github.com/Qrzzzz/AegisVaultMobile/blob/ae4d6e41498c214e2bcea5940850d7b1ce3f33e3/app/src/main/java/com/aegisvault/mobile/ui/AegisVaultRoute.kt#L75-L78)。主 Activity 没有使用 AppCompatActivity，也没有其他给当前 Activity 应用配置的 locale wrapper；旧 AppPreferences.wrapContext 不在此启动路径中。

[Android 官方语言文档](https://developer.android.com/guide/topics/resources/app-languages#compose-in-app)明确要求：Compose 使用 AppCompatDelegate 切换语言时，宿主需要继承 AppCompatActivity；API 32 及以前的兼容实现依赖该 Activity 上下文。因此当前实现无法为受支持的 Android 8–12L 提供正确的语言切换路径。不能因为 DataStore 已保存语言或单选项已选中就认为界面资源已更新。

## 设备复现步骤（待执行）

1. 在系统为英文的 API 30/32 设备启动 v1.5。
2. 设置中选择“简体中文”，关闭设置，检查输入标签、操作按钮、错误提示。
3. 强制停止并重启，再次检查；反向选择 English 和 Follow system。
4. API 33+ 另测系统级应用语言与应用内设置交互，不把低版本修复自动视作高版本验收通过。

## 修复与验收

- 使用完整的 AppCompat 宿主/locale 集成，或实现经验证的兼容方案；自有语言存储应与 Activity 初始化顺序及系统应用语言保持一致。
- API 26/30/32 与 33+ 上验证选择中英文、跟随系统、重启及配置重建，实际 stringResource 和上下文错误文本应一致切换。
- 不改变用户已有语言/主题设置；保留双语资源和敏感数据生命周期语义。
