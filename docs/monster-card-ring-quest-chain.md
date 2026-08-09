# 怪物卡戒指任务链实现说明

本文是怪物卡戒指 `29980..29990` 与满级复制任务 `29996` 的当前实现口径。后续修改服务端逻辑、任务脚本、WZ 或客户端
`Data/Quest` 时以本文为准。

## 总体规则

- 系列名称：怪物卡戒指。
- 任务列表归类：`QuestInfo.parent=怪物卡戒指`，`order=1..12`，`area=31`。分类 `31`
  仍显示为 `传奇之路`。
- 任务 ID：`29980..29990` 共 11 步，满级复制任务使用 `29996`。
- `29980` 只负责领取 `Lv0` 怪物卡戒指；`29981..29990` 分别负责升级到 `Lv1..Lv10`。
- 当前步骤以玩家实际持有或穿戴的最高怪物卡戒指为准，不以旧 quest 完成记录为权威。
- 玩家丢弃戒指后，任务步骤按剩余最高戒指等级回退；没有任何怪物卡戒指时回到可领取 `29980`。
- 成功重新领取 `Lv0` 是重置边界：服务端清理 `29981..29990` 的旧状态和进度，再开启
  `29981`。
- 达到 `Lv10` 后，可在 NPC 特安可处复制满级戒指。每次必须把至少一个 `Lv10` 戒指放在
  装备栏背包内，并消耗 100 个绝对音感（`4310000`）；每个角色永久最多复制 3 次。
- 复制次数记录在角色永久扩展字段 `monster_card_ring_max_copy_count` 中。丢弃戒指、重登、
  换频道或重新领取任务均不会重置次数。
- 满级戒指带唯一物品属性，复制功能仅在服务端该专用流程中绕过唯一物品拾取限制；其他发放、
  掉落和交易路径仍保持原限制。
- 持有满级戒指且复制次数不足 3 次时，服务端自动把 `29996` 设为进行中，特安可 NPC 头上显示
  未完成任务状态；第 3 次复制成功后自动把 `29996` 设为完成。
- 特安可 `2006` 同时固定加入服务端 `SET_NPC_SCRIPTABLE` 列表，客户端未命中
  Interaction Hook 时会回退到 `scripts-zh-CN/npc/2006.js`，两条入口共用同一套服务端校验。

## 状态机

`MonsterCardRingQuest.syncQuestState` 按当前戒指状态重算任务状态：

- 无戒指：`29980=NOT_STARTED`，`29981..29990=NOT_STARTED`，`29996=NOT_STARTED`。
- 持有 `Lv0`：`29980=COMPLETED`，`29981=STARTED`。
- 持有 `LvN`，`1 <= N < 10`：`29980..29980+N=COMPLETED`，
  `29980+N+1=STARTED`。
- 持有 `Lv10`：`29980..29990=COMPLETED`；复制不足 3 次时 `29996=STARTED`，达到 3 次时
  `29996=COMPLETED`。

当前升级任务使用自身 quest progress 的 `0=000/001` 作为虚拟 `infoex`：

- 条件不足、材料不足、怪物卡不足、戒指穿戴中或多戒指异常：写 `0=000`，任务保持进行中。
- `validateUpgrade` 通过：写 `0=001`，客户端显示完成图标，任务完成入口可提交升级。

戒指、材料和怪物卡变化后，服务端同步任务状态并刷新 `CHARACTER_QUEST_RULES` 与
`INTERACTION_HOOK_PROGRESS(0x1004)`，避免 Q 列表、任务 Hook 规则和 Q 详情进度缓存滞后。`0x1004`
是通用 Hook 进度包，每次发送都包含生命之证 entries 加当前怪物卡戒指 entry 的完整集合；不能只发送
怪物卡 entry，否则客户端完整替换缓存时会清掉生命之证 Q 详情进度。

## Q 详情进度

- `29980` 领取任务不使用动态进度 entry。
- `29981..29990` 和 `29996` 的 `QuestInfo.1` 必须包含且只包含一个 `@@BD_IH_PROGRESS:{questId}@@`，周围文案使用
  自然说明和 `升级进度：` 标签，不得回退到 `怪物卡戒指升级目标 / 当前进度 / 完成方式` 旧模板。
- 怪物卡戒指 Q 详情不使用 `#a2998x1#` 或其他 `#a` 宏；升级任务的完成门仍由服务端写入
  `QuestStatus` 的 `0=000/001` 控制。
- 服务端生成 progress entries 前先执行 `syncQuestStateSilently` 归一旧状态；随后为 `29981..29990`
  中所有 `STARTED` 升级任务生成 entry，并保证按当前戒指等级推导出的升级任务一定有 entry。文本包含
  满套怪物卡 `current/required` 套、升级材料 `材料名 count/10`、上一级戒指位置，以及当前状态或
  未满足原因。
- `29996` 进行中时生成复制进度 entry，显示永久复制次数 `current/3` 和绝对音感 `current/100`。
- progress entry 必须是已经解析好的纯文本，不得包含 `#i`、`#t`、`#b`、`#k`、`#r`、`#n`
  等 NPC/WZ 宏。材料名和戒指名由 `ItemInformationProvider.getName(itemId)` 获取；名称缺失时显示
  `道具 {itemId}`，不得回退成 `#t{itemId}#`。
- `syncQuestState(... announce=true)` 即使任务状态和 `0=000` 没变化，也会刷新通用 progress 包，确保
  材料数量或怪物卡数量变化能进入 Q 详情缓存。

## 资源落点

- 服务端逻辑：`gms-server/src/main/java/org/gms/server/quest/MonsterCardRingQuest.java`
- Hook Provider：`gms-server/src/main/java/org/gms/server/quest/hook/MonsterCardRingInteractionHookProvider.java`
- 任务脚本：`gms-server/scripts-zh-CN/quest/monsterCardRing.js`
- 服务端 WZ：`gms-server/wz-zh-CN/Quest.wz/QuestInfo.img.xml`、`Check.img.xml`、`Act.img.xml`
- 客户端 WZ：`/mnt/d/Game/BeiDou/BeiDou-Client/Data/Quest/QuestInfo.img`、`Check.img`、`Act.img`
- WZ 工具：`/mnt/d/Game/BeiDou/WzPatchTool`

基础 `gms-server/wz` 和 `gms-server/scripts` 不承载怪物卡戒指中文服自定义节点。

## WZ 规则

- `QuestInfo.img/29980..29990` 与 `29996` 必须包含 `parent=怪物卡戒指`、连续的 `order=1..12`、`area=31`。
- `QuestInfo.img/29981..29990` 的 `1` 文案必须包含一个 `@@BD_IH_PROGRESS:{questId}@@`，不得包含
  `@@DB_IH_PROGRESS`、`@@BD_LP_PROGRESS`、`#a` 或旧模板字段。
- `Check.img/29981..29990/1` 必须包含 `infoex/0/value=001`。
- `Act.img/29980..29989/1` 必须包含 `nextQuest=下一步任务 ID`。
- `Act.img/29990/1` 不写 `nextQuest`。
- `Check.img/29996/0` 依赖 `29990` 完成；`Act.img/29996` 不发放物品，也不设置 `nextQuest`。
- 客户端 `Data/Quest/QuestInfo.img`、`Check.img`、`Act.img` 的顶层节点顺序是兼容要求：
  `29980..29990` 必须连续，且 `29990` 后一个顶层节点必须是 `5100`。`WzPatchTool ring-patch`
  删除旧节点后必须按此顺序插入，找不到 `5100` 时不得尾部追加。

## 验证

服务端聚焦验证：

```bash
mvn -pl gms-server -Dtest=MonsterCardRingQuestTest,LifeProofQuestTest,InteractionHookRegistryTest,QuestActionHandlerTest test
```

WZ 工具和客户端资源验证：

```bash
dotnet build /mnt/d/Game/BeiDou/WzPatchTool/WzPatchTool.csproj -c Release
dotnet run --project /mnt/d/Game/BeiDou/WzPatchTool/WzPatchTool.csproj -- ring-patch --dry-run
dotnet run --project /mnt/d/Game/BeiDou/WzPatchTool/WzPatchTool.csproj -- ring-patch
dotnet run --project /mnt/d/Game/BeiDou/WzPatchTool/WzPatchTool.csproj -- ring-verify
```
