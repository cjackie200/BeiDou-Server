# InteractionHook v3 通用交互 Hook 方案

本文记录当前最终方案。实现和后续维护必须以本文为准，不再使用旧 `QuestHook` 窄实现作为业务入口。

## 目标

`InteractionHook v3` 统一接管 NPC 点击、NPC 对话选项点击、任务状态点击。客户端先按服务端下发规则做本地判断，命中后发送结构化事件给服务端；服务端返回 `HANDLED` 或 `FALLBACK`。未命中或明确 fallback 时才执行客户端原始逻辑。

本方案不通过逐个修改 NPC 默认脚本修补问题，也不依赖 NPC 对话文本匹配。

## 协议

- 底层包：`CUSTOM_PACKET(0x3713)`
- C2S 子命令：`C2S_INTERACTION_HOOK_EVENT = 0x1003`
- S2C rules：`S2C_INTERACTION_HOOK_RULES = 0x1001`
- S2C result：`S2C_INTERACTION_HOOK_RESULT = 0x1002`
- 全部字段使用小端序 `int32`
- 协议不传字符串

`C2S 0x1003` 字段顺序：

```text
requestId
eventType
targetType
targetId
objectId
clientNpcId
questId
questState
rawAction
selection
dialogContextKind
dialogContextId
dialogState
```

`S2C 0x1001 rules` 字段顺序：

```text
version = 3
ruleCount
rules...
```

每条 rule 字段顺序：

```text
eventMask
targetType
targetId
questId
questStateMask
selectionId
actionMask
```

`S2C 0x1002 result` 字段顺序：

```text
requestId
resultCode
```

## 枚举

`eventType`：

- `1 NPC_CLICK`
- `2 NPC_DIALOG_SELECTION`
- `3 QUEST_ACTION`

`eventMask`：

- `1 NPC_CLICK`
- `2 NPC_DIALOG_SELECTION`
- `4 QUEST_ACTION`

`targetType`：

- `0 ANY`
- `1 NPC`
- `2 QUEST`
- `3 DIALOG_SELECTION`

`questState`：

- `0 NONE`
- `1 NOT_STARTED`
- `2 STARTED`
- `3 COMPLETED`

`questStateMask`：

- `0 ANY`
- `1 NOT_STARTED`
- `2 STARTED`
- `4 COMPLETED`

`actionMask`：

- `0 ANY`
- `1 QUERY_START`
- `2 CONFIRM_START`
- `4 QUERY_PROGRESS`
- `8 QUERY_COMPLETE`
- `16 CONFIRM_COMPLETE`

`dialogContextKind`：

- `0 NONE`
- `1 NPC`
- `2 QUEST`
- `3 INTERACTION_HOOK`

`dialogState`：

- `0 NONE`
- `1 OPEN`
- `2 WAIT_SELECTION`
- `3 WAIT_CONFIRM`

`resultCode`：

- `0 HANDLED_DIALOG`
- `1 HANDLED_UPDATE`
- `2 FALLBACK_ORIGINAL`
- `3 REJECTED`
- `4 ERROR`

通配值：

- `ANY_ID = -1`
- `questStateMask = 0` 表示不限任务状态
- `actionMask = 0` 表示不限 Hook 动作

## 规则匹配

客户端只做规则命中判断和事件转发，不内置业务逻辑。匹配顺序固定：

1. `eventMask` 必须包含当前 `eventType`。
2. `targetType=ANY` 跳过目标类型判断，否则必须等于事件目标类型。
3. `targetId=ANY_ID` 跳过目标 ID 判断，否则必须等于事件目标 ID。
4. `questId=ANY_ID` 跳过任务 ID 判断，否则必须等于事件 `questId`。
5. `questStateMask=0` 跳过任务状态判断，否则必须包含当前 `questState`。
6. `selectionId=ANY_ID` 跳过选择项判断，否则必须等于事件 `selection`。
7. `actionMask=0` 跳过动作判断，否则必须包含当前 Hook 动作。

命中任意一条 rule 即拦截原始点击并发送 `C2S_INTERACTION_HOOK_EVENT`。

## 客户端运行态

发送拦截覆盖：

- `NPC_TALK(0x003A)`：解析 `objectId`，用本地映射取得 `clientNpcId`，作为 `NPC_CLICK` 判断。
- `NPC_TALK_MORE(0x003C)`：解析 `lastMsg/action/selection`，作为 `NPC_DIALOG_SELECTION` 判断。
- `QUEST_ACTION(0x006B)`：解析 `rawAction/questId/npcId`，作为 `QUEST_ACTION` 判断。

`NPC_TALK_MORE lastMsg=2` 的文本输入不进入 v3 Hook，默认走原逻辑。

接收拦截维护：

- `SET_FIELD(0x007D)`：清空旧地图 `objectId -> npcId` 映射。
- `SPAWN_NPC(0x0101)`：写入 `objectId -> npcId`。
- `REMOVE_NPC(0x0102)`：删除 `objectId`。
- `SPAWN_NPC_REQUEST_CONTROLLER(0x0103)`：`mode=1` 写入，`mode=0` 删除。
- `S2C_INTERACTION_HOOK_RULES(0x1001)`：原子替换规则，并清 pending 和 Hook 对话上下文。
- `S2C_INTERACTION_HOOK_RESULT(0x1002)`：处理 pending 请求。

pending 行为：

- 命中规则后保存原始 `COutPacket` 字节、opcode、requestId、时间戳。
- `FALLBACK_ORIGINAL` 时带重放标记重新发送原始包，避免客户端再次 Hook。
- `HANDLED_DIALOG/HANDLED_UPDATE` 时丢弃 pending。
- `REJECTED/ERROR` 或 5 秒超时时丢弃 pending，不自动 fallback，只恢复客户端操作状态。

生命周期：

- 换图清空旧 `objectId -> npcId`，再根据新地图 spawn 包重建。
- 切频道、断线、重登清空全部 Hook 运行态。
- 登录或进频道后重新接收 rules，再根据 NPC spawn 包重建映射。

## 服务端行为

- 使用 `InteractionHookManager`、`InteractionHookPackets`、`InteractionHookRuleRegistry` 替代旧 `QuestHook*` 对外入口。
- `CustomPacketHandler` 将 `0x1003` 分发给 `InteractionHookManager.handleEvent()`。
- 登录和进频道后下发 `InteractionHook v3 rules`。
- `NPC_CLICK` 必须使用当前地图 `objectId` 解析真实 `serverNpcId`；业务判断只信 `serverNpcId`，`clientNpcId` 只用于校验日志。
- 返回 `FALLBACK_ORIGINAL` 时，在 `Client` 上设置一次性 `skipNextNativeInteractionHook`，下一次对应 native handler 只走原逻辑，消费后立即清除。
- `NPCMoreTalkHandler` 的 Hook 判断放在 `QM/CM` 原始分发之前；`HANDLED_DIALOG` 时关闭或替换当前 `CM/QM`，建立 `InteractionHookContext`；fallback 时不清理原上下文。
- `NPCTalkHandler` 在进入 NPC 默认脚本、商店、职业导师对话前允许 Hook 预处理；fallback 时保持原逻辑。
- `QuestActionHandler` 任务状态入口统一走 `InteractionHook`。

`QUEST_ACTION` 映射：

- `rawAction=1`：`QUERY_START`
- `rawAction=4`：`QUERY_START`
- `rawAction=2`：`QUERY_COMPLETE`
- `rawAction=5`：`QUERY_PROGRESS`

确认领取或领奖只在 Hook 对话确认后执行 `CONFIRM_START` 或 `CONFIRM_COMPLETE`。

## 业务接入

生命之证：

- 一转教官 NPC 点击、任务可领取、进行中、可完成入口都接入 Hook。
- 进行中对话显示当前阶段、目标、进度、下一步。
- 不允许落到职业导师默认文本。
- 不再使用 `4033011` 作为任务完成绕路道具。

怪物卡戒指：

- 领取、查看进度、升级入口接入 Hook。
- 任务主题归入 `传奇之路`。

## 验收

- 服务端测试覆盖 rules 下发、通配匹配、结果码、fallback、`skipNextNativeInteractionHook`、NPC `objectId` 校验、`QM/CM` 接管。
- 生命之证测试覆盖进行中对话、领取、完成、奖励和 WZ 节点完整性。
- 怪物卡戒指测试覆盖领取、进度、升级。
- 客户端验证覆盖 `ijl15.dll` 编译、登录收到 v3 rules、换图重建 NPC 映射、普通职业导师 fallback、生命之证进行中对话。
- 联调用 `scripts/start-wsl-server.sh` 启动服务端，完全退出并重开客户端后验收。
