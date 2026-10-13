# 1.4 settings recovery / 设置恢复

Issue [#10](https://github.com/Qrzzzz/AegisVaultMobile/issues/10). Product 1.4, versionCode 6.

`SettingsCodec` is shared by the repository read flow and DataStore atomic edit. It inspects raw key names/types, avoiding unchecked casts and Enum.valueOf. Legitimate preferences, including explicit false security choices, remain unchanged. No alias inference or enum normalization is performed.

| Stored value | Recovery |
| --- | --- |
| Missing keys on first installation | Existing defaults: SYSTEM language/theme, false screen/leave protection, clipboard OFF; no recovery notice |
| Unknown/wrong-type language or theme | SYSTEM; notice |
| Unknown/wrong-type clipboard setting | S30; notice |
| Wrong-type screen/leave setting | true; notice |
| Lost entire preference record after an earlier successful load | Last known settings retained; notice; next edit can rebuild the five keys |
| Corrupt protobuf | Actual ReplaceFileCorruptionHandler writes SYSTEM language/theme, true screen/leave protection, S30 clipboard; notice |
| Read IOException | Last known settings retained, or protective defaults before first load; automatic 1/2/4/8/16/30-second retries, capped at 30 seconds |
| Write IOException | Failure reported; caller receives error, ViewModel displays notice; later explicit edit can retry |

Whole corrupt bytes cannot reveal previous settings; the replacement enables safety options rather than silently disabling them. Valid independent values survive partial decode recovery. No read-I/O replacement is written over unavailable storage. Corruption is handled separately from ordinary I/O; cancellation and arbitrary programming failures are not caught as recoverable settings failures.

The repository owns the recoverable read boundary, so ViewModel collection remains subscribed while reads retry. The ViewModel catches write I/O only. Recovery status has its own state rather than using the transient operation snackbar; text actions, their completion and sensitive clearing cannot overwrite it. The visible bilingual notice remains until acknowledged within the current process. This notice is not persisted across process death; repaired protective settings are persisted. Damaged values left on disk are reported again when read; explicit edits repair them. Pending initial settings loading keeps FLAG_SECURE enabled until a successful/fallback settings emission, and leaving before first loading clears sensitive inputs. Once loaded, an explicit false auto-clear preference is honored.

Only the existing five non-sensitive preference keys are written: language, theme, protect_screen, auto_clear_on_leave, clipboard_auto_clear. Input/password/plaintext/result/Base64 options remain unpersisted. Existing unknown unrelated keys are not interpreted or added by this change. There is one Context DataStore delegate per file; tests close prior owners before reopening a file. Android's DataStore instance and corruption-handler guidance is documented in the [official DataStore reference](https://developer.android.com/topic/libraries/architecture/datastore).

Tests use actual Preferences DataStore 1.1.1 and temporary protobuf files for unknown/old strings, every legal enum combination, absent keys, type anomalies, corruption, real file read/write faults, recovery/subscription and subsequent edit. A separate interruption wrapper around a real DataStore checks last-known settings across a stream I/O interruption; it is not claimed as a physical-device fault. Further tests prove cancellation/programming errors propagate and ViewModel recovery notices survive operations until acknowledgement.

中文：读取与编辑共用安全解码，合法安全设置不会因其他字段异常被重置。未知语言/主题回退 SYSTEM，异常安全选项采用保护默认值。普通读 I/O 保留最后有效值并重试；损坏 protobuf 明确重置为保护设置。恢复提示独立于操作消息，在当前进程内保留至确认。输入、口令和明文不会持久化。真实手机 DataStore/窗口/剪贴板验收仍需设备证据。
