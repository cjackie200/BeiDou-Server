# 个人任务掉落机制

本文记录怪物任务掉落的服务端规则，避免任务道具在组队击杀、退组和重新入队时出现共享、串看或串拾取。

## 适用范围

怪物掉落中同时满足以下条件的道具走个人任务掉落：

- `drop_data.questid > 0` 或服务端动态生成的 `MonsterDropEntry.questid > 0`。
- 道具 WZ 标记为任务道具，即 `ItemInformationProvider.isQuestItem(itemId) == true`。
- 当前角色按任务状态和背包持有量仍需要该道具，具体由 `Character.needQuestItem(questid, itemId)` 判断。

普通装备、金币、消耗品、无明确 `questid` 的老式任务物品仍按原有掉落规则处理。`questid = 0` 的任务物品没有明确角色任务上下文，不会被自动改成个人掉落。

## 组队规则

怪物死亡时，服务端按当前同地图队员逐个判定个人任务掉落：

- 每个符合条件的队员独立生成自己的掉落物。
- 掉落物只对拥有者可见，只能由拥有者本人或其宠物拾取。
- 个人任务掉落不会因为组队共享、15 秒所有权过期、退组或重新入队变成队友可见或可拾取。
- 不符合任务步骤、任务未开始、任务已完成、背包已达到需求数量的角色不会生成该个人掉落。

元素共鸣 Boss 凭证在通用个人任务掉落基础上额外校验当前 Boss 步骤：只有当前步骤匹配的 `mobId + itemId + questId` 会为角色生成凭证。

生命之证等服务端动态任务掉落也走同一套个人掉落队列，不再作为普通组队共享任务掉落生成。

## 文件落点

- 可见性和拾取权：`MapItem.isPersonalQuestDrop`、`MapItem.isVisibleTo`、`MapItem.canBePickedBy`。
- 怪物掉落拆分和生成：`MapleMap.sortDropEntries`、`MapleMap.dropPersonalQuestItemsFromMonsterOnMap`。
- 预筛选掉落表：`LootManager.retrieveRelevantDrops`。
