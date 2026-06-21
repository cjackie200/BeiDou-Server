# 生命之证 Q 列表、进度、入口与 Hook 串台修复计划

本文保存本轮修复计划。正式实现完成后，应把最终行为同步回
`docs/life-proof-quest-chain.md` 和 `docs/interaction-hook-v3.md`。

## Summary

- 本轮修复必须逐条关闭 4 个现象：旧阶段计数错误、Q 当前进度缺失、汉斯旧任务入口、接受任务后默认 NPC 对话串台。
- 数据修复采用“登录归一主修 + SQL 审计辅助”，不做 SQL-only。
- 三个硬约束：`LifeProofQuest.normalizeForLogin(player)` 必须在 `PacketCreator.getCharInfo(player)` 前执行；完成阶段判断必须包含 `hp_challenge_state`；`RETIRED_OPTION`、`BRIDGE`、`RESERVED` 客户端不可见且不可开始。
- 不允许用静态文案、隐藏任务、非安全 `#a` 宏绕过需求。

## Implementation Changes

- 登录状态归一：
  - 登录流程在 `MonsterCardRingQuest.syncQuestStateSilently(player)` 后、`PacketCreator.getCharInfo(player)` 前执行 `LifeProofQuest.normalizeForLogin(player)`。
  - 阶段完成依据合并：reward quest completed、有效 `hp_challenge_reward_log`、`hp_challenge_state.highest_rewarded_stage >= stage`、`hp_challenge_state.current_stage > stage`。
  - 将进行中阶段的旧 `附加试炼4..8` 迁移到固定 `附加试炼1..3` 槽位和 `hp_challenge_progress`，再清旧 quest 状态。
  - 已完成阶段补齐本职业本阶段所有可见任务为 `COMPLETED`，清理 retired option、bridge、reserved 的 started/completed 状态，保证 T1 `16/16`、T2-T7 `13/13`。

- Q 动态进度：
  - `QuestInfo` LifeProof 详情使用安全占位符，禁止非 mob `#a`。
  - 服务端新增 `INTERACTION_HOOK_PROGRESS(0x1004)`，发送当前职业分支 LifeProof visible quest 的状态和进度文本。
  - `ijl15` 缓存进度包并替换 Q 详情占位符；验收必须看到真实 `current/required`，不能是 marker 原文或 `...`。
  - LifeProof 任务开始、完成、归一、进度变化后刷新 Hook rules + progress。

- WZ 与客户端可见性：
  - 只有可见 LifeProof 任务允许有完整 `QuestInfo.name/0/1/2/area/parent/order` 和 `Check/0/startscript=lifeProof`。
  - `RETIRED_OPTION`、`BRIDGE`、`RESERVED` 如保留占位，不得有 `name`、`area`、`parent/order`、`startscript`、`endscript`。
  - 链式条件只依赖可见任务：第 2/3 个选择任务依赖前一个固定附加试炼完成，阶段奖励依赖第 3 个固定附加试炼完成，不再依赖 hidden bridge。
  - `WzPatchTool life-proof-sync/life-proof-verify` 校验 retired/hidden 节点不可见不可开始，且 T1 parent 计数 `16/16`、T2-T7 parent 计数 `13/13`。

- 汉斯错误任务入口：
  - 汉斯“可以开始”列表的根修复靠 WZ 不暴露 retired 任务；服务端非当前 LifeProof quest action 静默拒绝只作为兜底。
  - 非当前 LifeProof 任务动作静默拒绝并 `enableActions`，只记录服务端日志。
  - 当前任务必须匹配当前 quest、正确 NPC、正确状态才进入 Hook。
  - 状态归一后，旧的 I/II 附加试炼不应再出现在汉斯“可以开始”列表。

- Hook 默认对话串台：
  - LifeProof intercepted request 必须明确发送 handled/rejected result，不能依赖 NPC_TALK 隐式 ack。
  - `ijl15` 只有收到 `FALLBACK_ORIGINAL` 才 replay 原动作；`HANDLED_*`、`REJECTED`、`ERROR` 都 drop pending。
  - 接受附加试炼后增加短窗口 suppress，防止同一 NPC/quest 原生对话续跑。

- SQL 与文档：
  - 新增只读审计 SQL 或 GM 诊断：统计 retired option 脏状态、阶段缺步、stray started。
  - 不把玩家状态修复写成 Flyway migration。
  - 更新 `docs/life-proof-quest-chain.md` 和 `AGENTS.md`，记录 LifeProof 状态归一、Q 进度 Hook、ijl15 Windows 编译环境要求。
  - 更新 `WzPatchTool life-proof-sync/life-proof-verify`，校验安全占位符、visible count、parent/order、禁止非安全 `#a`。

## Test Plan

- `ijl15` gate：
  - Windows 侧编译通过。
  - 客户端 Q 详情占位符能被替换。
  - 日志能证明替换命中且无异常。

- 服务端单测：
  - mage 旧数据：`5142`、`5268` completed/started 能迁移并清理，归一后 I 为 `16/16`、II 为 `13/13`，旧附加试炼不在 started/completed 可见列表。
  - 构造 `hp_challenge_state.current_stage=3` 或 `highest_rewarded_stage=2`，即使 reward quest/log 缺失，也能补齐 I/II。
  - WZ 测试断言 retired/bridge/reserved 不含客户端可见字段，不含 `startscript/endscript`。
  - 非当前 LifeProof native action 无 dropMessage、无脚本启动。
  - LifeProof selection/confirm 分支均发送 handled result。

- 命令验证：
  - `mvn -pl gms-server -am test`
  - `dotnet build /mnt/d/Game/BeiDou/WzPatchTool/WzPatchTool.csproj -c Release`
  - `dotnet run --project /mnt/d/Game/BeiDou/WzPatchTool/WzPatchTool.csproj -- life-proof-sync`
  - `dotnet run --project /mnt/d/Game/BeiDou/WzPatchTool/WzPatchTool.csproj -- life-proof-verify`
  - `dotnet run --project /mnt/d/Game/BeiDou/WzPatchTool/WzPatchTool.csproj -- ring-verify`

- 手工验收：
  - 重启服务端，完全退出并重开客户端，重新登录同一法师。
  - Q 中生命之证 I/II 不在“正在进行”，完成进度正确，无 retired 附加试炼残留。
  - 当前生命之证 III 在 Q 详情显示真实进度，不崩溃、不显示占位符。
  - 汉斯列表不出现错误 I/II 附加试炼；漏网点击也无聊天提示。
  - 接受 `生命之证III:附加试炼3` 后不再弹默认 NPC 对话。
  - `interaction-hook.log` 无 `ReplayLocalQuestAction`、无错误 `FALLBACK_ORIGINAL`。

## Git Gate

- 提交前分别检查服务端、WzPatchTool、ijl15、客户端仓库：
  - `git status -sb`
  - `git diff --name-status`
  - `git diff --check`
- 客户端提交只包含必要 `Data/Quest` 和 DLL 产物，排除 `config.ini`、日志、PDB、备份目录。
- 不 amend 既有提交，不 push。

## Assumptions

- SQL 只做审计或停服预清理，不作为唯一修复手段。
- 登录归一必须保留，用于修复未预清理角色和运行时客户端同步。
- 如果 Q 文本 Hook 找不到稳定点，停止并回报，不提交静态文案替代方案。
