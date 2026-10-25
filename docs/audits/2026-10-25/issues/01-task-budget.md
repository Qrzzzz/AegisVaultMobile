审计基线：`ae4d6e41498c214e2bcea5940850d7b1ce3f33e3`，当前正式版 v1.5。优先级 P1：低内存设备的资源耗尽风险。此问题与已经关闭的 #5 区分：#5 防止旧结果回写；本问题是旧计算没有结束时仍可重复接收新计算，缺少结合移动端内存预算的应用级准入控制。

## 代码证据

- [编辑事件会使任务失效](https://github.com/Qrzzzz/AegisVaultMobile/blob/ae4d6e41498c214e2bcea5940850d7b1ce3f33e3/app/src/main/java/com/aegisvault/mobile/ui/AegisVaultViewModel.kt#L41-L45)，[invalidateTask 立即丢弃 job 引用并解除 busy](https://github.com/Qrzzzz/AegisVaultMobile/blob/ae4d6e41498c214e2bcea5940850d7b1ce3f33e3/app/src/main/java/com/aegisvault/mobile/ui/AegisVaultViewModel.kt#L64-L68)。新操作只检查这个 busy，再派发到 Dispatchers.Default；没有等待旧 worker 实际退出的执行门禁。
- [生产 scrypt 是同步 SCrypt.generate 调用](https://github.com/Qrzzzz/AegisVaultMobile/blob/ae4d6e41498c214e2bcea5940850d7b1ce3f33e3/app/src/main/java/com/aegisvault/mobile/core/AegisVaultEngine.kt#L157-L164)，协程取消不能中断已经进入的计算。默认单次 KDF 约 32 MiB，策略允许单次达到 64 MiB；当前预算约束不限制多个在途计算的总成本。

## 已执行复现

使用未修改的生产 ViewModel、lifecycle 2.8.2、coroutines 1.8.1，在 JVM 上注入确定性暂停、忽略取消的 worker。连续 8 次“修改输入/口令 → Encrypt → dispatch”，然后清空：

```text
VIEWMODEL_OVERLAP active=8 peak=8 latestBusy=true
AFTER_CLEAR active=8 uiBusy=false inputEmpty=true
STALE_WRITEBACK_GUARD retained=true allWorkersReleased=true
```

这证明执行准入没有随实际 worker 数量受限，也确认 #5 的回写保护仍有效。探针没有真的同时运行 8 次 scrypt，没有测量峰值内存，也没有在手机上触发 OOM；资源耗尽是结合生产同步 KDF 和上述执行路径得到的风险判断。

## 用户复现路径

在低内存/较慢设备上启动 AES 操作，在它实际完成前修改输入或清空，再重复启动。旧任务逻辑失效，但不可取消的计算仍可占用执行线程和内存，新任务继续被接收。

## 修复与验收

- 将“结果是否仍有效”和“物理 worker 是否已退出”分开管理；对同一应用实例的 KDF 执行设置明确并发/排队上限，不能仅依赖 cancel 或 generation。
- 清空/离开仍立即清除 UI，过期结果/错误/finally 继续不得回写；不要为了等待计算而阻塞主线程。
- 确定性暂停不可协作 worker，反复编辑、清空、重试，断言实际执行数不超过上限、队列有界、最后一次有效操作可以完成。
- 补实际 Android KDF 压力和峰值内存验收；保留 #5 的生命周期回归。

本地可复核材料：`docs/audits/2026-10-25/probes/TaskOverlapProbe.kt`、`task-overlap.txt`。这些新审计文件尚未提交，不能视作已发布测试。
