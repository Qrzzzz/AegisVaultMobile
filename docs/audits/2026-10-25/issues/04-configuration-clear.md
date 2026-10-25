审计基线：`ae4d6e41498c214e2bcea5940850d7b1ce3f33e3`，正式版 v1.5。优先级 P2：配置变化可能误删未保存的文本。证据类型：源码 + Android 生命周期合约；设备步骤尚未执行。与 #5 的过期任务回写问题不同。

## 问题与证据

设置文案说明在应用完全离开前台时清空敏感数据，但 [Route 把每一次 ON_STOP 都发送为 OnLeaveApp](https://github.com/Qrzzzz/AegisVaultMobile/blob/ae4d6e41498c214e2bcea5940850d7b1ce3f33e3/app/src/main/java/com/aegisvault/mobile/ui/AegisVaultRoute.kt#L67-L72)，没有区分 Activity 配置重建。开启 autoClearOnLeave 后，[该事件直接清空 input/password/result 并使任务失效](https://github.com/Qrzzzz/AegisVaultMobile/blob/ae4d6e41498c214e2bcea5940850d7b1ce3f33e3/app/src/main/java/com/aegisvault/mobile/ui/AegisVaultViewModel.kt#L51-L54)。Manifest 没有接管配置变化。

[Android 官方生命周期说明](https://developer.android.com/guide/components/activities/state-changes#configuration-change)指出，旋转等配置变化也会销毁/重建 Activity，并执行旧 Activity 的 onPause、onStop、onDestroy。由此，用户仍在应用内旋转、改变窗口大小等行为也会走到当前清空分支；保留同一个 ViewModel 不能阻止这次显式清空。

## 设备复现步骤（待执行）

1. 开启“离开应用时自动清空敏感数据”。
2. 输入合成文本、口令并生成结果，保持应用在前台。
3. 旋转屏幕触发 Activity 重建，检查输入/口令/结果及正在执行的任务。
4. 与真正按 Home、切换到其他应用的路径比较。

## 修复与验收

- 将配置重建与真正离开前台区分；明确配置变化的清空策略，使实现和设置文案一致。不要简单删除后台清理观察器。
- 按现有“完全离开前台”契约，旋转/窗口配置重建应保留当前会话及有效任务；真实离开仍立即清空并丢弃晚到结果。
- 覆盖 autoClear 开关两种状态、旋转/ActivityScenario.recreate、Home/返回以及进行中的任务；不可通过持久化明文来保留状态。
