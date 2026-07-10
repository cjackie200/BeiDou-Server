# 代码规范

本文是 BeiDou Server 仓库的代码规范。所有新增和重构代码都必须遵守；已有代码在本次改动触达时，应尽量按本规范收敛。

## Java

- Java 目标版本为 OpenJDK 21。
- 使用 4 空格缩进。
- 包名保持在 `org.gms` 下。
- 类名使用 `PascalCase`，方法和字段使用 `lowerCamelCase`。
- 优先使用 guard clause 处理错误和非法状态。
- 网络协议、任务、WZ、脚本、数据库迁移相关代码必须同步补测试或文档。
- 不手工修改生成文件或打包产物。

## JavaScript 脚本

- 自定义中文脚本放在 `gms-server/scripts-zh-CN`。
- 基础 `gms-server/scripts` 保留原生内容。
- 任务脚本中的玩家可见文案必须与对应设计文档保持一致。
- 复杂任务逻辑优先放在 Java 服务端权威实现，脚本只负责入口和确认流程。
- 脚本调用 `Quest.start/complete` 时，任务物品和奖励以 `Act.img` 为唯一执行源；不得再用
  `gainItem/removeItem` 重复发放或扣除同一物品。

## WZ 和客户端资源

- 服务端中文任务资源放在 `gms-server/wz-zh-CN`。
- 客户端资源同步到 `Data/Quest`、`Data/Etc`、`Data/String`。
- 基础 `gms-server/wz` 不承载中文服自定义任务节点。
- 改动任务节点时必须确认 `Check.img`、`QuestInfo.img`、`Act.img` 三者完整，避免客户端按 `Q` 打开任务列表崩溃。
- 中文客户端存在的可见 `Check/Act` 顶层任务必须在服务端 `wz-zh-CN` 有同名节点；服务端按语言目录
  整体选择 Quest WZ，不会用基础 `wz` 自动补齐缺失节点。只在服务端存在的内部状态任务必须明确
  标记并保持客户端不可见。
- `@@BD_LP_PROGRESS`、`@@BD_IH_PROGRESS` 等自定义进度 marker 只能写入对应自定义任务；同步或合并
  `QuestInfo.img` 时必须校验普通原版任务没有被批量替换或注入 marker。
- 新增 Act 字段时必须同步校验 `QuestActionType` 映射和执行类；WZ 中存在但 loader 返回
  `UNDEFINED` 的状态动作会静默跳过，造成后续任务和图标滞留。
- 原生任务缺少服务端 `Check.img` 节点时必须拒绝 `start/complete`；客户端 `autoStart`、
  `autoPreComplete` 等展示元数据不得跳过服务端条件。需要无条件迁移状态时只能由明确的内部流程调用
  `forceStart/forceComplete`。
- 无法实现的任务条件不得按 `true` 放行。选择 fail-closed 或禁用时，必须同步处理服务端四个 Quest
  XML、客户端四个 Quest IMG，并保留旧客户端请求的服务端拒绝。

## C++ DLL

- `ijl15` 修改必须保持协议常量与服务端文档一致。
- 客户端 Hook 只做本地规则匹配、状态缓存和事件转发，不内置生命之证、怪物卡戒指业务逻辑。
- 所有跨包解析都要做长度检查。
- fallback 重放必须有防重入标记。
- 换图、切频道、断线时清理旧运行态，并从服务端新下发的包重建状态。

## 防御性编码

- 函数入口先处理空对象、非法枚举、越界参数和缺失上下文。
- 依赖调用失败先返回或降级，不继续进入主逻辑。
- 不复制旧的临时绕路逻辑；旧逻辑能被最终方案替换时直接移除。
- 业务权威判断放在服务端，客户端传入字段只作为辅助校验。

## 测试

- 服务端新增测试放在 `gms-server/src/test/java`，尽量镜像生产包路径。
- 协议测试必须覆盖字段顺序、枚举值、通配值、fallback 和错误路径。
- WZ 任务测试必须覆盖任务节点完整性。
- 客户端 DLL 修改后必须编译并覆盖客户端，再完全重启客户端联调。

## 提交说明

提交标题使用简洁中文，优先匹配仓库历史风格。建议格式：

```text
<feat> 模块 新增/修改 简洁描述
<fix> 模块 修复 简洁描述
```

示例：

```text
<feat> InteractionHook 新增通用交互 Hook
<fix> 生命之证 修复任务进行中对话
```
