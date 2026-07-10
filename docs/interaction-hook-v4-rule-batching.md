# InteractionHook v4 分组与分批规则下发方案

本文记录 `InteractionHook v4` 的规则分组和分批下发方案。实现和后续维护必须以本文为准。

## 目标

- 规则按作用域拆成全部清理、角色任务、当前地图 NPC、临时对话四类。
- 每个业务作用域独立分批下发，客户端收齐完整批次后原子替换对应作用域。
- `ALL_RULES` 只用于清空，防止切频道、重登、断线后的旧规则残留。
- 生命之证只走当前唯一任务条目 `QUEST_ACTION`，普通 NPC 点击保持原逻辑。
- 地图 NPC Hook 只按当前地图存在的 NPC 下发。

## 协议

本文的 v4 指分组、分批和 scope 语义；当前线上 wire format 已随统一协议升级为 `version=5`：

```text
subCommand = 0x1003
version = 5
scope
batchId
batchIndex
batchCount
replaceMode
ruleCount
rules...
```

`scope`：

```text
0 ALL_RULES
1 CHARACTER_QUEST_RULES
2 MAP_NPC_RULES
3 DIALOG_TEMP_RULES
```

`replaceMode`：

```text
1 REPLACE_SCOPE
2 CLEAR_SCOPE
```

每条 rule 字段保持不变：

```text
eventMask
targetType
targetId
questId
questStateMask
actionMask
selectionId
```

固定约束：

- 每包最多 `100` 条 rule。
- `batchIndex` 从 `0` 开始。
- `CLEAR_SCOPE` 固定为 `batchCount=1`、`batchIndex=0`、`ruleCount=0`。
- `ALL_RULES` 只允许 `CLEAR_SCOPE`，不允许携带 rules。
- `batchId` 存在 `Client` 上，使用单个单调递增计数；每次发送任意 scope 时递增。
- 普通 scope 客户端只接受 `batchId > lastAppliedBatchId[scope]`。
- `CLEAR_SCOPE ALL_RULES` 代表登录、进频道或重建会话后的全量重置，客户端必须无条件接受，并把全部普通 scope 的
  `lastAppliedBatchId` 重置为 `0`，避免同一客户端进程重登后误拒服务端新 `Client` 从 `1` 开始的批次号。
- 客户端保留 v3 兼容解析：收到 `version=3` 时按旧格式全量替换全部 active rules。

## 服务端行为

废弃原 `sendRules(Client)` 对外入口，使用明确入口：

- `sendInitialRules(Client)`：先 `CLEAR_SCOPE ALL_RULES`，再发送角色任务规则和当前地图 NPC 规则。
- `sendCharacterQuestRules(Client)`：只刷新 `CHARACTER_QUEST_RULES`。
- `sendMapNpcRules(Client)`：只刷新 `MAP_NPC_RULES`。
- `clearDialogTempRules(Client)`：清空 `DIALOG_TEMP_RULES`。
- `clearAllRules(Client)`：清空全部客户端规则和 pending 状态。

规则生成：

- `CHARACTER_QUEST_RULES` 中生命之证只包含当前角色唯一当前任务；已完成、未来和非当前阶段生命之证任务不下发。
  其他任务系列仍按各自 provider 生成当前需要的 `QUEST_ACTION` 规则。
- `MAP_NPC_RULES` 只遍历当前 `chr.getMap().getMapObjects()` 中的 `MapObjectType.NPC`，provider 只能为这些 NPC 生成 `NPC_CLICK` rule。
- `DIALOG_TEMP_RULES` 本轮只实现协议能力和清理能力，不接入任何业务临时规则。

发送时机：

- 登录/进频道后调用 `sendInitialRules(Client)`，并保证它早于 `addPlayer()` 发送当前地图 NPC spawn。
- `PlayerMapTransitionHandler` 中 `chr.setMapTransitionComplete()` 后调用 `sendMapNpcRules(Client)`。
- Hook 对话关闭、任务状态变化、怪物卡戒指状态同步后调用 `sendCharacterQuestRules(Client)`。
- Hook 对话关闭或 fallback 时调用 `clearDialogTempRules(Client)`。
- 如果服务端存在可靠断线或切频道清理入口，则调用 `clearAllRules(Client)`；否则依赖下次 `sendInitialRules(Client)` 先清空。
- `InteractionHookManager.dispose()` 只刷新 `CHARACTER_QUEST_RULES` 并清 `DIALOG_TEMP_RULES`，不刷新地图规则。

业务接入：

- 生命之证不生成 `NPC_CLICK` 或旧菜单 selection rule，只生成当前唯一任务的
  `CHARACTER_QUEST_RULES` `QUEST_ACTION` rule。生命之证自定义事件必须携带
  `dialogContext=DIALOG_CONTEXT_QUEST`；普通 NPC 对话、旧菜单、普通 NPC 对话关闭后的尾随
  `QUEST_ACTION` 和原生生命之证任务包都不能推进任务。
- 怪物卡戒指任务规则进入 `CHARACTER_QUEST_RULES`。
- 特安可 NPC 点击规则进入 `MAP_NPC_RULES`，且仅在当前地图存在特安可时下发。
- 怪物卡戒指旧长期 `selectionRule(MENU_SELECTION_ID)` 本轮移除，不再下发。

## 客户端行为

- 用 `characterQuestRules`、`mapNpcRules`、`dialogTempRules` 替代单一 `g_rules`。
- `ShouldIntercept` 按三个 active scope 依次扫描。
- 收到 `REPLACE_SCOPE` 时暂存 `scope + batchId` 批次，全部收齐后按 `batchIndex` 拼接并原子替换该 scope。
- 收到普通 scope 的 `CLEAR_SCOPE` 时立即清空对应 scope，不进入 pending batch。
- 收到 `CLEAR_SCOPE ALL_RULES` 时立即清空全部 active rules、pending batches、active pending 和当前
  Hook 对话上下文；不清 NPC 映射。NPC 映射只由 `SET_FIELD`、spawn 和 remove 包维护。
- 5 秒未收齐的 pending batch 丢弃，继续使用旧 active rules。
- v4 普通 scope 替换不清 active pending。
- `SET_FIELD` 只清空旧地图 `objectId -> npcId` 映射，不清角色任务规则。
- NPC 点击的 `objectId -> npcId` 无法解析时直接放行原生包，不允许用“存在任意 NPC rule”作为全图
  catch-all。
- 客户端普通 NPC 对话关闭后进入一次性抑制状态；抑制状态不因 `STAT_CHANGED enableActions` 清除，
  只在放行一条匹配的尾随 `QUEST_ACTION` 或匹配忽略超时后清除。
- 本地任务条目点击必须先按 active rules 判断 `QUEST_ACTION` Hook。命中
  `CHARACTER_QUEST_RULES` 时优先发送 Hook 事件，并清理普通 NPC 对话留下的抑制状态；未命中时才
  继续使用抑制状态保护尾随原生包。
- Outgoing `QUEST_ACTION` 包路径仍然先执行抑制状态保护，不使用本地任务条目点击的优先级，避免
  普通 NPC 对话关闭后的尾随原生包绕过保护。

## 验收

- 服务端测试覆盖 v4 包头字段、分批数量、`CLEAR_SCOPE` 包格式、`ALL_RULES` 约束和 `batchId` 单调递增。
- 服务端测试覆盖 `sendInitialRules` 顺序、地图切换只刷新地图规则、dispose 只刷新角色规则并清临时规则。
- 服务端测试覆盖生命之证只在 `CHARACTER_QUEST_RULES`，怪物卡戒指 NPC rule 只在当前地图存在特安可时进入 `MAP_NPC_RULES`。
- 客户端验证 v4 单包、多包、缺失 batch 超时、`CLEAR_SCOPE DIALOG_TEMP_RULES`、`CLEAR_SCOPE ALL_RULES` 和 v3 兼容解析。
- 联调验证生命之证任务对话、普通一转教官原对话、怪物卡戒指地图 NPC Hook、换图后地图规则更新、重登后旧规则清空。
