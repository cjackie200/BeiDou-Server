# 怪物卡戒指任务链实现说明

本文是怪物卡戒指 `29980..29990` 的当前实现口径。后续修改服务端逻辑、任务脚本、WZ 或客户端
`Data/Quest` 时以本文为准。

## 总体规则

- 系列名称：怪物卡戒指。
- 任务列表归类：`QuestInfo.parent=怪物卡戒指`，`order=1..11`，`area=31`。分类 `31`
  仍显示为 `传奇之路`。
- 任务 ID：`29980..29990` 共 11 步。
- `29980` 只负责领取 `Lv0` 怪物卡戒指；`29981..29990` 分别负责升级到 `Lv1..Lv10`。
- 当前步骤以玩家实际持有或穿戴的最高怪物卡戒指为准，不以旧 quest 完成记录为权威。
- 玩家丢弃戒指后，任务步骤按剩余最高戒指等级回退；没有任何怪物卡戒指时回到可领取 `29980`。
- 成功重新领取 `Lv0` 是重置边界：服务端清理 `29981..29990` 的旧状态和进度，再开启
  `29981`。

## 状态机

`MonsterCardRingQuest.syncQuestState` 按当前戒指状态重算任务状态：

- 无戒指：`29980=NOT_STARTED`，`29981..29990=NOT_STARTED`。
- 持有 `Lv0`：`29980=COMPLETED`，`29981=STARTED`。
- 持有 `LvN`，`1 <= N < 10`：`29980..29980+N=COMPLETED`，
  `29980+N+1=STARTED`。
- 持有 `Lv10`：`29980..29990=COMPLETED`。

当前升级任务使用自身 quest progress 的 `0=000/001` 作为虚拟 `infoex`：

- 条件不足、材料不足、怪物卡不足、戒指穿戴中或多戒指异常：写 `0=000`，任务保持进行中。
- `validateUpgrade` 通过：写 `0=001`，客户端显示完成图标，任务完成入口可提交升级。

戒指、材料和怪物卡变化后，服务端同步任务状态并刷新 `CHARACTER_QUEST_RULES`，避免 Q 列表和
任务 Hook 规则滞后。

## 资源落点

- 服务端逻辑：`gms-server/src/main/java/org/gms/server/quest/MonsterCardRingQuest.java`
- Hook Provider：`gms-server/src/main/java/org/gms/server/quest/hook/MonsterCardRingInteractionHookProvider.java`
- 任务脚本：`gms-server/scripts-zh-CN/quest/monsterCardRing.js`
- 服务端 WZ：`gms-server/wz-zh-CN/Quest.wz/QuestInfo.img.xml`、`Check.img.xml`、`Act.img.xml`
- 客户端 WZ：`/mnt/d/Game/BeiDou/BeiDou-Client/Data/Quest/QuestInfo.img`、`Check.img`、`Act.img`
- WZ 工具：`/mnt/d/Game/BeiDou/WzPatchTool`

基础 `gms-server/wz` 和 `gms-server/scripts` 不承载怪物卡戒指中文服自定义节点。

## WZ 规则

- `QuestInfo.img/29980..29990` 必须包含 `parent=怪物卡戒指`、`order=1..11`、`area=31`。
- `Check.img/29981..29990/1` 必须包含 `infoex/0/value=001`。
- `Act.img/29980..29989/1` 必须包含 `nextQuest=下一步任务 ID`。
- `Act.img/29990/1` 不写 `nextQuest`。
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
