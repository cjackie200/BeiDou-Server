# Changelog

本文档记录 BeiDou Server 项目的所有重要版本变动，面向设计开发人员。

---

## [v2.1.1] — 2026-06-22

> **范围**: v1.0.0 → v2.1.1 | 108 次提交  
> **标签**: `v2.1.1`  
> **关联**: 客户端 `v2.1.1` | ijl15 DLL `v1.2.2`

### 🎯 新增功能

#### 生命之证 (LifeProof) 任务链系统
- 实现生命之证全系列任务链：5 分支 × 5 阶段 = 25 条任务线
- 任务 ID 范围: `5100`–`5974`，每块 25 个任务
- Q 窗口进度格式化：多行进度 + 颜色 + 物品图标
- NPC 对话格式化：任务引导与进度信息分离
- 附加试炼系统：完成主线后解锁隐藏挑战
- 访问任务（拜访）类型支持
- 相关文件：
  - `scripts-zh-CN/quest/lifeProof.js` — 任务入口脚本
  - `scripts-zh-CN/npc/2006.js` — NPC 对话格式化
  - `wz-zh-CN/Quest.wz/QuestInfo.img.xml` — 任务信息定义
  - `wz-zh-CN/Quest.wz/Check.img.xml` — 任务条件检查
  - `wz-zh-CN/Quest.wz/Act.img.xml` — 任务行为定义
  - `src/main/java/org/gms/server/quest/` — 任务引擎扩展

#### 怪物卡戒指 (Monster Card Ring) 升级系统
- 11 步任务链设计：1 个领取任务 + 10 级升级
- 任务 ID 范围: `29980`–`29990`
- Q 窗口进度可视化：`@@BD_IH_PROGRESS:<questId>@@` 标记格式
- 自动升级流程：NPC 一键操作
- 相关文件：
  - `scripts-zh-CN/quest/monsterCardRing.js`
  - `wz/Character.wz/Ring/01112415.img.xml` 等 11 个戒指定义
  - `wz-zh-CN/Quest.wz/` — 任务 WZ 数据
  - `src/main/java/org/gms/server/quest/` — 任务引擎支持

#### MAP 任务改造：水晶收集
- 原 MAP 任务改为水晶收集机制
- ReactorFactory 添加缺失数据保护，防止空指针异常
- `wz/Reactor.wz/` — 反应器配置更新
- `src/main/java/org/gms/server/quest/` — 任务逻辑调整

#### 挑战洗血 (Challenge HP Wash) 系统
- 新增加导师引导的洗血线性流程
- 配套验收命令与操作文档
- 修复挑战洗血导师入口逻辑
- 相关文件：
  - `scripts-zh-CN/npc/` — 导师 NPC 脚本
  - `docs/guides/` — 操作指南

#### 超级商店 (Super Shop)
- 集中式商店系统，分类展示商品
- 卷轴附带效果说明
- 数据文件：
  - `super_shop_scrolls_names.tsv`
  - `super_shop_scrolls_with_effects.csv`
  - `super_shop_scrolls_with_effects.tsv`

#### 交互 Hook 系统
- 服务端实现交互 Hook 规则引擎
- 客户端 ijl15.dll 配合拦截交互事件
- 多条件进度协议 v5：更灵活的任务进度追踪格式
- 相关文件：
  - `src/main/java/org/gms/client/processor/npc/` — NPC 交互处理
  - `src/main/java/org/gms/net/` — 协议扩展

#### 副本系统增强
- 关卡谜题支持单人跳过模式
- 相关文件：
  - `scripts-zh-CN/event/` — 副本事件脚本

#### 工具与脚本
- WSL 服务端启动脚本 (`scripts/start-wsl-server.sh`)
- 服务端打包规范文档

### 🔧 优化改进

#### 协议层
- 多条件进度协议：从单条件扩展到多条件并行
- `progressEntryText` 精简：只保留卡套 + 材料两行
- Q 窗口专注展示条件进度，去掉红色提示文字
- NPC 对话负责所有提示信息

#### 性能与稳定性
- `ReactorFactory` 添加缺失数据保护，防止 NPE
- Flyway 迁移版本冲突修复
- 数据库连接池优化

#### 代码质量
- 全量测试通过 46/46
- LifeProof 审计修复 BUG #1-#11
- 清理失败的 Reactor 试验残留代码
- 测试断言同步更新

#### 本地化 (i18n)
- 汉化阿里安特系列 NPC 对话（玛兹拉美发、拉尔拉护肤、高级整形券）
- 修正新手飞侠训练任务文本
- 修复上海 NPC 交互与相关汉化
- 优化妖精悲伤任务描述与 NPC 名称
- 修正休咪钞票任务父级显示
- 汉化阿多尼斯迷失的灵魂任务
- 补齐怪物嘉年华 2 英文勋章对白
- 本地化资深猎人勋章 zh-CN 资源
- 国际化资源更新：`i18n/message_zh_CN.properties`

#### Web 管理后台 (gms-ui)
- 账号管理 API 完善 (`gms-ui/src/api/account.ts`)
- 请求拦截器优化 (`gms-ui/src/api/interceptor.ts`)
- 导航栏组件重构 (`gms-ui/src/components/navbar/index.vue`)
- 文件管理页面更新 (`gms-ui/src/views/game/file/index.vue`)

### 🐛 BUG 修复

| 编号 | 问题 | 影响范围 |
|------|------|----------|
| #1 | 生命之证任务入口串台 | Q 进度与任务入口冲突 |
| #2 | 生命之证任务列表崩溃 | 任务分组与排序异常 |
| #3–#11 | LifeProof 审计修复 | 附加试炼进度、任务交付体验、Hook 规则下发等 |
| — | 黑悟空奖励失效 | BOSS 奖励发放逻辑 |
| — | Flyway 迁移版本冲突 | 数据库初始化 |
| — | 家族技能报错 | 家族系统 |
| — | 挑战洗血导师入口 | NPC 交互 |
| — | 怪物卡戒指任务入口 | NPC 交互 |
| — | 在线奖励更新 | 奖励发放 |
| — | 默认关闭宠吸 | 宠物系统 |
| — | 聚怪功能完善 | 怪物 AI |

### 🔗 关联项目变动

#### ijl15 DLL (v1.0.1 → v1.2.2)
- ZXString::Assign hook：修复 BSTR buffer 溢出
- 多 condition ApplyProgress 解析
- 生命之证进度替换与点击抑制
- 交互 Hook 客户端拦截
- 高刷新率 (>60Hz) 启动异常修复
- 无密码登录支持
- 角色选择 MAC 对齐修复（VMware 兼容）
- 技能描述中文换行乱码修复
- 一转技能检测优化（修复二段跳问题）
- 删除角色绕过 PIN 检测

#### 客户端 WZ (v1.0.0 → v2.1.1)
- 怪物卡戒指资源：11 个 `.img` 文件
- 生命之证任务数据同步
- MAP 任务水晶收集 WZ 补丁
- 数据文件更新：Quest、String、Etc、Item 等

### 📁 文件变动统计

| 类别 | 新增 | 修改 | 删除 | 合计 |
|------|------|------|------|------|
| Java 源代码 | 45 | 67 | 3 | 115 |
| 脚本 (JS) | 18 | 59 | 6 | 83 |
| WZ 数据 (XML) | 24 | 26 | 4 | 54 |
| Web 前端 (Vue/TS) | 5 | 8 | 0 | 13 |
| 配置与文档 | 20 | 12 | 1 | 33 |
| **总计** | **112** | **172** | **14** | **298** |

> 注：文件变动统计基于 git diff v1.0.0..v2.1.1，实际有效文件 248 个。

---

## [v1.2.2] — 2026-06-20

> **标签**: `v1.2.2`（被 v2.1.1 取代）  
> 与 v2.1.1 内容相同，版本号重新编排。

---

## [v1.2.1] — 2026-06-19

- 怪物卡戒指改为 11 步任务链
- 修复生命之证附加试炼进度
- 修复黑悟空奖励失效

---

## [v1.1.2] — 2026-06-18

- 实现生命之证交互 Hook
- 修复生命之证任务列表崩溃
- 修复 Flyway 迁移版本冲突

---

## [v1.1.1] — 2026-06-17

- 实现生命之证任务链（初版）
- WSL 服务端启动脚本
- 挑战洗血系统实现

---

## [v1.0.1] — 2026-06-16

- 超级商店上线
- 怪物卡戒指配置添加
- 默认关闭宠吸

---

## [v1.0.0] — 2026-06-15

- 🎉 初始版本发布
- 基于 Cosmic 的冒险岛 v83 服务端模拟器
- Spring Boot 3.2.3 + Netty 网络层
- MyBatis-Flex 数据访问层
- Flyway 数据库迁移
- GraalVM JavaScript 脚本引擎
- Vue 3 + Arco Design Web 管理后台
- 中文本地化 WZ 数据
- REST API (JWT 认证)
- 玩家数据管理（背包、技能、任务、好友等）
- GM 命令系统
- 副本与组队任务
- 商城系统

---

## 版本规范

本项目遵循以下版本命名规则：

- **主版本号**: 重大重构或架构变更
- **次版本号**: 新功能、新系统
- **修订号**: BUG 修复、小优化

标签格式: `v<major>.<minor>.<patch>`

---

> 📝 本文件由 BeiDou Server 开发团队维护  
> 每次版本发布时更新
