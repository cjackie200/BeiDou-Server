# 洗血/洗蓝/AP重置 相关代码文档

## 一、核心文件：AP 分配/重置处理器

### `gms-server/src/main/java/org/gms/client/processor/stat/AssignAPProcessor.java`（约1019行）

**这是整个洗血系统的核心文件**，包含三个关键入口方法：

```text
方法: `APResetAction(Client, int APFrom, int APTo)`; 行号: 557; 功能: **洗血/洗蓝核心** — AP重置卷轴的处理逻辑
方法: `APAssignAction(Client, int num)`; 行号: 700; 功能: 手动分配 1 点 AP 到指定属性
方法: `APAutoAssignAction(InPacket, Client)`; 行号: 103; 功能: 自动分配全部剩余 AP
方法: `calcHpChange(Character, boolean usedAPReset)`; 行号: 779; 功能: 计算 HP 增加量（按职业不同）
方法: `calcMpChange(Character, boolean usedAPReset)`; 行号: 883; 功能: 计算 MP 增加量（按职业不同）
方法: `takeHp(Job)`; 行号: 973; 功能: AP 重置时 HP 扣除量（按职业）
方法: `takeMp(Job)`; 行号: 998; 功能: AP 重置时 MP 扣除量（按职业）
```

### APResetAction 洗血完整流程（第557-688行）：

**1. 来源属性扣点**（switch `APFrom`，值为 `Stat` 枚举编码）：

```text
APFrom: STR; 编码: 64; 属性: 力量; 验证规则: 最低保留 5 点
APFrom: DEX; 编码: 128; 属性: 敏捷; 验证规则: 最低保留 5 点
APFrom: INT; 编码: 256; 属性: 智力; 验证规则: 最低保留 5 点
APFrom: LUK; 编码: 512; 属性: 运气; 验证规则: 最低保留 5 点
APFrom: HP; 编码: 2048; 属性: 生命; 验证规则: `hpMpApUsed >= 1`，最低 HP 阈值 `hp >= level * 14 + 148`
APFrom: MP; 编码: 8192; 属性: 魔法; 验证规则: `hpMpApUsed >= 1`，按职业检查最低 MP 阈值
```

MP 最低阈值按职业细分：
- 枪战士(Spearman)：`mp >= 4 * level + 156`
- 剑士(Fighter)/战神(Aran1)：`mp >= 4 * level + 56`
- 一转后飞侠(Thief, id%100>0)：`mp >= level * 14 - 4`
- 其他职业：`mp >= level * 14 + 148`

**2. `useEnforceHpmpSwap` 配置**（默认 `false`）：开启时强制 HP↔MP 只能互换，不能转到四维属性。

**3. 目标属性加点**：调用 `addStat(player, APTo, true)`

### addStat 方法（第709-766行）：

- 对 STR/DEX/INT/LUK：调用 `assignStr/Dex/Int/Luk(1)`
- 对 HP（2048）：调用 `calcHpChange(chr, true)` → `assignHP(maxHp, 1)` → 显示 `[重置卷轴] 最大HP +X ↑`
- 对 MP（8192）：调用 `calcMpChange(chr, true)` → `assignMP(maxMp, 1)` → 显示 `[重置卷轴] 最大MP +X ↑`

### calcHpChange 职业 HP 增长表（第779-869行）：

```text
职业: 战士/黎明战士; 基础值: 20; 重置值: 20; 随机范围: 18-22; 技能加成: `IMPROVED_MAXHP` / `MAX_HP_INCREASE`
职业: 战神(Aran); 基础值: 28; 重置值: 20; 随机范围: 26-30; 技能加成: 无
职业: 法师/烈焰巫师; 基础值: 6; 重置值: 6; 随机范围: 5-9; 技能加成: 无
职业: 飞侠/暗夜行者; 基础值: 16; 重置值: 16; 随机范围: 14-18; 技能加成: 无
职业: 弓箭手/风灵使者; 基础值: 16; 重置值: 16; 随机范围: 14-18; 技能加成: 无
职业: 海盗/冲锋队长; 基础值: 18; 重置值: 18; 随机范围: 16-20; 技能加成: `IMPROVE_MAX_HP`
```

### calcMpChange 职业 MP 增长表（第883-939行）：

```text
职业: 战士/黎明/战神; 基础值: 3; 重置值: 2; 随机范围: 2-4; 智力系数: 10%; 技能加成: 无
职业: 法师/烈焰巫师; 基础值: 18; 重置值: 18; 随机范围: 12-16; 智力系数: 5%; 技能加成: `IMPROVED_MAX_MP_INCREASE` / `INCREASING_MAX_MP`
职业: 弓箭手/风灵; 基础值: 10; 重置值: 10; 随机范围: 6-8; 智力系数: 无; 技能加成: 无
职业: 飞侠/暗夜; 基础值: 10; 重置值: 10; 随机范围: 6-8; 智力系数: 无; 技能加成: 无
职业: 海盗/冲锋队长; 基础值: 14; 重置值: 14; 随机范围: 7-9; 智力系数: 无; 技能加成: 无
```

### takeHp / takeMp 扣除量表（第973-1019行）：

```text
职业: 战士; takeHp（HP扣除）: -54; takeMp（MP扣除）: -4
职业: 法师; takeHp（HP扣除）: -10; takeMp（MP扣除）: -31
职业: 弓箭手/飞侠; takeHp（HP扣除）: -20; takeMp（MP扣除）: -12
职业: 海盗; takeHp（HP扣除）: -42; takeMp（MP扣除）: -16
职业: 其他; takeHp（HP扣除）: -12; takeMp（MP扣除）: -8
```

---

## 二、角色属性数据模型

### `gms-server/src/main/java/org/gms/client/AbstractCharacterObject.java`

核心字段：

- `remainingAp`（第55行）— 剩余可用 AP 点数
- `hpMpApUsed`（第53行）— **洗血核心计数器**，已分配到 HP/MP 的总 AP 次数
- `attrStr/Dex/Int/Luk` — 四维属性值
- `maxHp` / `maxMp` — 最大生命/魔法值（上限 30000）

关键方法：

```text
方法: `assignStr/Dex/Int/Luk(int x)`; 行号: 579-593; 功能: 单属性增减，委托给 `assignStrDexIntLuk`
方法: `assignStrDexIntLuk(...)`; 行号: 639-689; 功能: **核心校验**：检查 `apUsed <= remainingAp`、属性值在 `[4, max_ap]` 范围内
方法: `assignHP(int deltaHP, int deltaAp)`; 行号: 595-613; 功能: HP 分配：验证 `remainingAp >= deltaAp`、`hpMpApUsed + deltaAp >= 0`、`maxHp < 30000`
方法: `assignMP(int deltaMP, int deltaAp)`; 行号: 615-633; 功能: MP 分配：对称逻辑
方法: `gainAp(int deltaAp, boolean silent)`; 行号: 706-715; 功能: 增加剩余 AP
方法: `changeRemainingAp(int x, boolean silent)`; 行号: 695-704; 功能: 设置剩余 AP 绝对值
```

### `gms-server/src/main/java/org/gms/client/Character.java`

- `levelUp()`（第5786行）— 升级时的 AP/HP/MP 增益逻辑
- `changeJob()`（第1111行）— 转职时的 AP/HP/MP 奖励
- `resetStats()`（第7197行）— 重置角色属性到基础值
- `recalcLocalStats()`（第7053行）— 重新计算本地属性

---

## 三、网络入口与道具触发

### `gms-server/src/main/java/org/gms/net/server/channel/handlers/UseCashItemHandler.java`（第175-234行）

处理现金道具类型 `505`（AP/SP 重置卷轴）：

- `itemId == 5050000` → AP 重置，读取 `APTo` 和 `APFrom`，调用 `AssignAPProcessor.APResetAction(c, APFrom, APTo)`
- `itemId > 5050000` → SP 重置

### `gms-server/src/main/java/org/gms/net/server/channel/handlers/DistributeAPHandler.java`

处理客户端 `DISTRIBUTE_AP (0x57)` 数据包 → 调用 `AssignAPProcessor.APAssignAction(c, num)`

### `gms-server/src/main/java/org/gms/net/server/channel/handlers/AutoAssignHandler.java`

处理客户端 `AUTO_DISTRIBUTE_AP (0x58)` 数据包 → 调用 `AssignAPProcessor.APAutoAssignAction(p, c)`

### `gms-server/src/main/java/org/gms/net/PacketProcessor.java`

- 第206行：注册 `DISTRIBUTE_AP` → `DistributeAPHandler`
- 第263行：注册 `AUTO_DISTRIBUTE_AP` → `AutoAssignHandler`

---

## 四、常量与枚举

### `gms-server/src/main/java/org/gms/constants/id/ItemId.java`（第302行）

```java
public static final int AP_RESET = 5050000;
```

### `gms-server/src/main/java/org/gms/client/Stat.java`

属性类型编码（用于网络传输）：

```text
属性: STR; 编码: 64; 十六进制: 0x40
属性: DEX; 编码: 128; 十六进制: 0x80
属性: INT; 编码: 256; 十六进制: 0x100
属性: LUK; 编码: 512; 十六进制: 0x200
属性: HP; 编码: 1024; 十六进制: 0x400
属性: MAXHP; 编码: 2048; 十六进制: 0x800
属性: MP; 编码: 4096; 十六进制: 0x1000
属性: MAXMP; 编码: 8192; 十六进制: 0x2000
属性: AVAILABLEAP; 编码: 16384; 十六进制: 0x4000
属性: AVAILABLESP; 编码: 32768; 十六进制: 0x8000
```

### `gms-server/src/main/java/org/gms/net/opcodes/RecvOpcode.java`

- `DISTRIBUTE_AP(0x57)` — 第101行
- `AUTO_DISTRIBUTE_AP(0x58)` — 第102行

---

## 五、数据库配置（game_config 表）

### `gms-server/src/main/resources/db/migration/V1.7.0__create_game_config.sql`

洗血相关配置项：

```text
配置键: `use_enforce_hpmp_swap`; 默认值: `false`; 含义: 强制 HP↔MP 只能互换，不能转到四维
配置键: `use_fixed_ratio_hpmp_update`; 默认值: `false`; 含义: 按比例更新 HP/MP（HeavenMS机制）
配置键: `use_randomize_hpmp_gain`; 默认值: `true`; 含义: HP/MP 增长随机化，且受智力加成
配置键: `max_ap`; 默认值: `32767`; 含义: 单属性 AP 上限
配置键: `level_up_ap_gain`; 默认值: `5`; 含义: 升级获得 AP 数
配置键: `use_auto_assign_starters_ap`; 默认值: `true`; 含义: 10级以下新手自动分配 AP
配置键: `use_starting_ap_4`; 默认值: `false`; 含义: 起始属性 4/4/4/4（早期GMS模式）
配置键: `use_server_auto_assigner`; 默认值: `false`; 含义: 使用 HeavenMS 内置自动分配器
配置键: `use_auto_assign_secondary_cap`; 默认值: `true`; 含义: 自动分配时副属性软上限
```

### characters 表关键字段（`V1.0.6__create_characters.sql`）：

```text
字段: `ap`; 类型: INT(11); 含义: 剩余 AP
字段: `hpMpUsed`; 类型: INT(11) UNSIGNED; 含义: 已分配到 HP/MP 的 AP 次数
字段: `str`; 类型: INT(11); 含义: 力量
字段: `dex`; 类型: INT(11); 含义: 敏捷
字段: `luk`; 类型: INT(11); 含义: 运气
字段: `int`; 类型: INT(11); 含义: 智力
字段: `hp`; 类型: INT(11); 含义: 当前 HP
字段: `mp`; 类型: INT(11); 含义: 当前 MP
字段: `maxhp`; 类型: INT(11); 含义: 最大 HP
字段: `maxmp`; 类型: INT(11); 含义: 最大 MP
```

---

## 六、GM 命令

```text
文件: `client/command/commands/gm2/ApCommand.java`; 命令: `!ap <值>` 或 `!ap <玩家> <值>`; 功能: 设置剩余 AP（受 max_ap 限制）
文件: `client/command/commands/gm0/StatStrCommand.java`; 命令: 玩家命令; 功能: 分配力量
文件: `client/command/commands/gm0/StatDexCommand.java`; 命令: 玩家命令; 功能: 分配敏捷
文件: `client/command/commands/gm0/StatIntCommand.java`; 命令: 玩家命令; 功能: 分配智力
文件: `client/command/commands/gm0/StatLukCommand.java`; 命令: 玩家命令; 功能: 分配运气
```

---

## 七、NPC 脚本

- `scripts-zh-CN/npc/2003.js` — 新手引导 NPC Robin，解释 AP 系统
- `scripts-zh-CN/npc/1012005.js` — 宠物大师（宠物 AP 重置卷轴 4160011，与玩家洗血无关）

**没有 NPC 脚本直接触发 AP 重置操作** — AP 重置完全通过现金道具 `5050000` + `UseCashItemHandler` 来处理。

---

## 八、调用链路总结

```
玩家使用 AP 重置卷轴(5050000)
  │
  ▼
UseCashItemHandler (itemType=505, itemId=5050000)
  │ 读取 APFrom, APTo (属性编码: 64=STR, 128=DEX, 256=INT, 512=LUK, 2048=HP, 8192=MP)
  ▼
AssignAPProcessor.APResetAction(c, APFrom, APTo)
  ├── 从 APFrom 扣除 1 点
  │   ├── STR/DEX/INT/LUK → assignStr/Dex/Int/Luk(-1)
  │   ├── HP → 验证 hpMpApUsed≥1, hp≥level*14+148
  │   │      → takeHp(job) → assignHP(hplose, -1)
  │   └── MP → 验证 hpMpApUsed≥1, 职业最低MP
  │          → takeMp(job) → assignMP(mplose, -1)
  │
  └── 向 APTo 增加 1 点 (addStat)
      ├── STR/DEX/INT/LUK → assignStr/Dex/Int/Luk(1)
      ├── HP → calcHpChange(chr, true) → assignHP(maxHp, 1)
      │        → dropMessage "[重置卷轴] 最大HP +X ↑"
      └── MP → calcMpChange(chr, true) → assignMP(maxMp, 1)
               → dropMessage "[重置卷轴] 最大MP +X ↑"

玩家手动分配 AP
  │
  ▼
DistributeAPHandler (DISTRIBUTE_AP 0x57)
  ▼
AssignAPProcessor.APAssignAction(c, num)
  ▼
addStat(chr, num, false)  // 同上但不显示 [重置卷轴] 消息

玩家自动分配 AP
  │
  ▼
AutoAssignHandler (AUTO_DISTRIBUTE_AP 0x58)
  ▼
AssignAPProcessor.APAutoAssignAction(p, c)
  // 按职业智能分配全部剩余AP到主属性/副属性
```

---

## 九、"洗血"玩法原理

在冒险岛 v83 中，洗血（HP Washing）是一种通过 AP 重置卷轴将 MP 点数转为 HP 的高级玩法：

1. 升级时将 AP 分配到 **INT（智力）**，因为智力影响升级时的 MP 增长量（`use_randomize_hpmp_gain` 开启时）
2. 使用 **AP 重置卷轴（5050000）**，将 MP 中的点数洗到 HP
3. 因为 MP 扣除量远小于 HP 增加量（如战士：扣 4 MP，加 20 HP），反复操作可显著提升最大 HP

**本项目的关键控制开关**：

- `use_randomize_hpmp_gain`（默认 `true`）— 控制智力是否影响 MP 增长，关闭则洗血失效
- `use_enforce_hpmp_swap`（默认 `false`）— 控制是否强制 HP/MP 只能互换
- `hpMpApUsed` 计数器 — 追踪有多少点数被分配到 HP/MP，防止无限洗血

---

## 十、完整文件索引

**核心业务逻辑：**
- `gms-server/src/main/java/org/gms/client/processor/stat/AssignAPProcessor.java` — AP分配/重置处理器
- `gms-server/src/main/java/org/gms/client/AbstractCharacterObject.java` — 角色属性数据模型
- `gms-server/src/main/java/org/gms/client/Character.java` — 角色类（升级/转职/属性重算）

**网络处理：**
- `gms-server/src/main/java/org/gms/net/server/channel/handlers/UseCashItemHandler.java` — AP重置卷轴触发
- `gms-server/src/main/java/org/gms/net/server/channel/handlers/DistributeAPHandler.java` — 手动分配AP
- `gms-server/src/main/java/org/gms/net/server/channel/handlers/AutoAssignHandler.java` — 自动分配AP
- `gms-server/src/main/java/org/gms/net/PacketProcessor.java` — 数据包处理器注册

**常量/枚举：**
- `gms-server/src/main/java/org/gms/constants/id/ItemId.java` — `AP_RESET = 5050000`
- `gms-server/src/main/java/org/gms/client/Stat.java` — 属性类型编码枚举
- `gms-server/src/main/java/org/gms/net/opcodes/RecvOpcode.java` — 网络协议码

**技能常量（影响 HP/MP 增长）：**
- `gms-server/src/main/java/org/gms/constants/skills/Warrior.java` — `IMPROVED_MAXHP`
- `gms-server/src/main/java/org/gms/constants/skills/Magician.java` — `IMPROVED_MAX_MP_INCREASE`
- `gms-server/src/main/java/org/gms/constants/skills/DawnWarrior.java` — `MAX_HP_INCREASE`
- `gms-server/src/main/java/org/gms/constants/skills/Brawler.java` — `IMPROVE_MAX_HP`
- `gms-server/src/main/java/org/gms/constants/skills/ThunderBreaker.java` — `IMPROVE_MAX_HP`
- `gms-server/src/main/java/org/gms/constants/skills/BlazeWizard.java` — `INCREASING_MAX_MP`
- `gms-server/src/main/java/org/gms/constants/skills/Legend.java` — 传说职业技能

**配置：**
- `gms-server/src/main/java/org/gms/config/GameConfig.java` — 动态配置读取
- `gms-server/src/main/resources/db/migration/V1.7.0__create_game_config.sql` — 配置默认值
- `gms-server/src/main/resources/db/migration/V1.0.6__create_characters.sql` — 角色表结构

**角色创建：**
- `gms-server/src/main/java/org/gms/client/creator/CharacterFactoryRecipe.java` — 角色创建配方
- `gms-server/src/main/java/org/gms/client/creator/CharacterFactory.java` — 角色工厂
- `gms-server/src/main/java/org/gms/client/creator/veteran/*.java` — 各职业 veteran 创建器

**GM命令：**
- `gms-server/src/main/java/org/gms/client/command/commands/gm2/ApCommand.java` — `!ap`
- `gms-server/src/main/java/org/gms/client/command/commands/gm0/StatStrCommand.java`
- `gms-server/src/main/java/org/gms/client/command/commands/gm0/StatDexCommand.java`
- `gms-server/src/main/java/org/gms/client/command/commands/gm0/StatIntCommand.java`
- `gms-server/src/main/java/org/gms/client/command/commands/gm0/StatLukCommand.java`
- `gms-server/src/main/java/org/gms/client/command/commands/gm2/LevelCommand.java`
