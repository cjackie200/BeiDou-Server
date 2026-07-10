# BeiDou Server Docs

本文是本仓库文档索引。新增文档优先补充现有文件；确需新增文件时，必须放入合适目录并在本文登记。

## 任务与系统设计

- [挑战洗血设计](hp-challenge-design.md)
- [洗血资料整理](hpwashing.md)
- [生命之证任务链实现说明](life-proof-quest-chain.md)
- [生命之证 Q 列表、进度、入口与 Hook 串台修复计划](life-proof-q-runtime-fix-plan.md)
- [怪物卡戒指任务链实现说明](monster-card-ring-quest-chain.md)
- [InteractionHook 通用交互 Hook 方案](interaction-hook-v3.md)
- [InteractionHook v4 分组与分批规则下发方案](interaction-hook-v4-rule-batching.md)
- [背包栏位与物品堆叠策略](inventory-slot-policy.md)
- [客户端自动更新方案](client-update.md)
- [个人任务掉落机制](personal-quest-drops.md)
- [元素武器规则](elemental-weapon-rules.md)
- [任务完整性与资源一致性](quest-integrity.md)

## 规约

- [AI 执行规约](guides/ai-rules.md)
- [代码规范](guides/coding-style.md)

## 新增文档规则

1. 优先补充现有文档，不为小主题新建文件。
2. 新文件必须放入对应目录，并更新本索引。
3. 改代码影响设计、协议、配置、SQL、运维、测试或用户可见行为时，同批更新对应文档。
4. 文档描述最终方案时，必须同步检查代码是否落地；未落地内容必须明确标记为计划。
5. 历史方案、审查记录和阶段性计划不得覆盖正式实现说明；如保留历史内容，必须标明历史快照。
