审计基线：`ae4d6e41498c214e2bcea5940850d7b1ce3f33e3`，正式版 v1.5。优先级 P2。范围为进程存活、应用可访问剪贴板时的计时和所有权，不要求在进程被杀或平台禁止访问时保证清理。

## 问题与证据

[Route 使用 rememberCoroutineScope](https://github.com/Qrzzzz/AegisVaultMobile/blob/ae4d6e41498c214e2bcea5940850d7b1ce3f33e3/app/src/main/java/com/aegisvault/mobile/ui/AegisVaultRoute.kt#L61)，[每次复制都单独 launch 一个 delay 清理任务](https://github.com/Qrzzzz/AegisVaultMobile/blob/ae4d6e41498c214e2bcea5940850d7b1ce3f33e3/app/src/main/java/com/aegisvault/mobile/ui/AegisVaultRoute.kt#L131-L135)。没有可恢复的截止时间、当前复制代次或前一计时 job；仅比较字符串相等。

由此有两个同一计时管理缺口的表现：

1. 配置重建使旧 Composition 被释放；其 scope 取消后，已设置的 30 秒清理任务消失，新 Route 不会恢复它。应用仍存活、回到前台也不会补清。
2. 同一文本先在 t=0 复制，再在 t=20s 复制。旧计时到 t=30s 时字符串仍相同，会清掉第二次仅复制了 10 秒的内容，未按最新复制更新截止时间。

[Compose 官方 scope 合约](https://developer.android.com/develop/ui/compose/side-effects#remembercoroutinescope)明确其离开 Composition 时会取消；[配置重建会释放旧 Composition](https://developer.android.com/guide/components/activities/state-changes#configuration-change)。这是应用内生命周期/计时状态问题，不只是文案中 best effort 所指的平台访问限制。

## 已执行的模型复现

使用 coroutines 1.8.1 虚拟时间，按当前 `delay + 字符串比较` 算法建立窄模型，并以 scope.cancel 模拟文档规定的 Composition 释放：

```text
COMPOSITION_RECREATED atMs=31000 copiedValueStillPresent=true
REPEATED_SAME_COPY elapsedSinceSecondMs=10001 clipboardEmpty=true
```

这不是 Android ClipboardManager/旋转实测，也没有运行 Compose 渲染器；它验证计时算法的反例。实际 ActivityScenario.recreate 和系统剪贴板集成仍需设备验收。本地模型为 `docs/audits/2026-10-25/probes/ClipboardTimerModelProbe.kt`，尚未提交。

## 修复与验收

- 为应用当前拥有的复制条目维护单一清理任务/代次和截止时间，重复复制应更新期限，旧计时不得清除较新的条目。
- 配置重建后继续计时或补做已到期清理；不要为恢复计时把敏感明文写入持久化存储。
- 保留“剪贴板已被用户/其他应用改写则不清除”的保护，明确进程死亡/后台访问限制的 best-effort 边界。
- 验证 30 秒前 Activity 重建、相同/不同文本连续复制、其他应用改写、到期返回前台及清理设置变化；补真实系统剪贴板测试。
