# Repository Guidelines

## 项目结构与模块组织

本仓库包含 Java 游戏服务端和 Vue 管理后台。根目录 `pom.xml` 聚合
`gms-server`；Java 源码位于 `gms-server/src/main/java/org/gms`，资源文件位于
`gms-server/src/main/resources`，Flyway 迁移脚本位于
`gms-server/src/main/resources/db/migration`，命名示例为
`V1.0.60__create_example.sql`。运行时资源按语言区分，包括 `scripts-zh-CN`、
`scripts`、`wz-zh-CN` 和 `wz`。前端代码在 `gms-ui/src`，生产构建产物可复制到
`gms-server/src/main/resources/static`。

## 必读文档

接手非平凡任务时，先按顺序阅读：

1. `docs/README.md`：文档索引和新增文档规则。
2. `docs/guides/ai-rules.md`：AI / 自动编码统一规约。
3. `docs/guides/coding-style.md`：防御性编码、脚本、WZ、客户端 DLL 和测试规范。
4. 任务相关设计文档，例如 `docs/life-proof-quest-chain.md`、`docs/interaction-hook-v3.md`。

代码、协议、SQL、脚本、WZ、客户端资源、测试和文档必须同向落地；发现设计与实现不一致时，
先收敛不一致，再继续新增能力。

## 构建、测试与本地开发命令

- `mvn -pl gms-server -am test`：编译服务端模块并运行 JUnit 测试。
- `mvn -pl gms-server -am package`：构建服务端 jar，输出到
  `gms-server/target/BeiDou.jar`。
- `cd gms-server && mvn spring-boot:run`：使用 MySQL 8 本地启动服务端。
- `cd gms-ui && yarn install`：安装前端依赖。
- `cd gms-ui && yarn dev`：启动 Vite 开发服务。
- `cd gms-ui && yarn build`：执行 Vue 类型检查并构建前端。
- `cd gms-ui && yarn type:check`：仅运行 TypeScript 检查。

## 代码风格与命名约定

Java 目标版本为 OpenJDK 21。使用 4 空格缩进，包名保持在 `org.gms` 下，类名使用
`PascalCase`，方法和字段使用 `lowerCamelCase`。控制器、服务、DAO、模型、脚本和网络
相关代码应放入现有包族。前端使用 Vue 3、TypeScript、ESLint、Stylelint 和
Prettier；Prettier 配置为 2 空格、分号、单引号和 80 列。不要手工修改打包产物，除非
通过项目工具重新构建。

新增和重构代码遵守 `docs/guides/coding-style.md`：函数入口优先处理 `null`、空值、非法枚举、
越界参数、缺失配置和依赖错误；不要复制旧临时绕路逻辑；业务权威判断放在服务端。

## 测试指南

服务端测试使用 JUnit 5 和 Mockito。新增测试放在 `gms-server/src/test/java`，尽量
镜像生产代码包路径，并用清晰的行为和期望结果命名。仓库未配置覆盖率阈值，因此改动应
显式覆盖核心行为、边界条件和失败路径。前端提供类型检查和 lint-staged 格式化，但
`package.json` 中没有独立单元测试脚本。

## 提交与 Pull Request 指南

近期提交多为简洁中文标题，例如 `修复洗血bug`、`新增: ...`，也有少量
Conventional Commit，例如 `fix: ...`。提交应保持小而聚焦。PR 需要说明改动内容、
列出验证命令、关联 issue；涉及 UI 可见变化时附截图或录屏。数据库迁移、配置变更、
资源或脚本更新必须在 PR 描述中明确说明。

## 安全与配置提示

不要提交真实凭据、token、生产数据库密码或本地备份配置。示例配置应保持通用。涉及
MySQL 的变更需要按 MySQL 8 验证，因为项目不支持更低版本。

## 怪物卡戒指任务入口经验

特安可 NPC ID 是 `2006`，`1002006` 是吉夫；修改前必须用客户端和服务端 WZ 实际节点确认，
不要只按文件名或记忆判断。NPC 对话入口可能来自 `String/Npc.img` 的 `d0/d1`、`Npc.img`
的 `info/script`、服务端 `npcs_scriptable`、任务灯泡和任务完成书本；同一 NPC 不要混用多套
入口承载同一功能。

不要依赖 `SET_NPC_SCRIPTABLE` 动态移除客户端“其他”入口，客户端进程可能缓存旧入口。怪物卡
戒指使用任务链承载入口：`29980` 只负责领取 0 级戒指，`29981..29990` 负责下一档进度查看和
升级；升级任务的开始条件必须依赖上一档任务完成，而不是依赖自身完成。服务端由
`MonsterCardRingQuest.syncQuestState` 控制 `NOT_STARTED`、`STARTED`、`COMPLETED`，任务
`start` 脚本显示进度和 GM 测试补齐，任务 `end` 脚本执行升级。

涉及生命之证、怪物卡戒指等中文服自定义任务、NPC 或 WZ 的改动，只维护服务端
`scripts-zh-CN`、`wz-zh-CN` 和客户端 `Data/Quest`、`Data/String`、`Data/Etc`；基础
`scripts`、`wz` 保留原生内容，不承载中文服自定义任务节点。验证时用
`WzPatchTool ring-verify`、`inspect` 检查关键节点，例如
`Check.img/29981` 应依赖 `29980` 完成，`Check.img/29990` 应依赖 `29989` 完成。验收前必须
重启服务端、完全退出并重开客户端、重新登录角色；同时确认数据库 `game_config.npcs_scriptable`
没有残留测试 NPC。提交客户端补丁时排除 `config.ini`、`.wzpatch-backup` 和临时备份目录。

## 客户端补丁打包规范

客户端补丁以 `v旧版本 -> v新版本` 为边界，例如 `v1.0.0 -> v1.1.1`。打包前必须确认
客户端仓库分支、tag 和本地状态，记录实际 commit hash，并用
`git diff --name-only $FROM $TO` 锁定差异文件。补丁只能包含这批差异文件，不得包含
`config.ini`、备份、日志、临时工具输出或初始化大文件提交中的无关内容。

补丁载荷使用目标版本导出，而不是复制工作区文件，示例：
`git archive --format=zip -o patch.zip $TO -- $(git diff --name-only $FROM $TO)`。导出后用
`unzip -Z -1 patch.zip | grep -v '/$'` 校验文件清单，并确认没有
`config.ini`。

分发包命名为 `BeiDou-Client-Patch-$FROM-to-$TO.zip`。优先提供 Windows 自包含
`BeiDouPatchInstaller.exe`；同时保留 `Install-BeiDouPatch.bat`、
`Install-BeiDouPatch.ps1`、`patch.zip` 和 `README.txt` 作为备用。安装器必须让玩家选择
包含 `BeiDou.exe` 和 `Data` 的客户端根目录，覆盖前备份到
`backup\patch-$TO-时间戳`，安装完成后写入 `patch-$TO.log`。WSL/Linux 侧只能验证构建和
压缩包内容，最终必须在 Windows 客户端目录实际运行一次安装验证。

## 服务端补丁打包规范

服务端补丁必须以线上实际已部署的基线到目标版本为边界，例如
`xjc -> v1.1.1`，不要默认使用最早 release tag。打包前先确认当前分支、目标 tag、
线上基线 commit、`git status -sb` 和 `git diff --name-status $FROM..$TO`，并按差异判断
代码、SQL、脚本、WZ、Web 后台和删除文件范围。

Flyway SQL 以线上基线为准分类处理。已经属于线上基线的 migration 不得改名、不得复制成
更高版本重复执行；只有目标版本相对基线新增的 migration 才需要保证版本号排在基线已执行
版本之后。不得为了绕过版本顺序长期依赖 `spring.flyway.out-of-order=true`；只有明确的一次性
救急场景才允许使用，并必须在交付说明中写明。打包前必须列出新增 SQL 的来源 commit、用途和
是否属于当前补丁。

服务端 jar 必须包含最新 Web 后台。若 `gms-ui` 有改动，必须执行
`cd gms-ui && yarn build`，然后把 `gms-ui/dist` 产物打入
`BeiDou.jar` 的 `BOOT-INF/classes/static/`。可以在 Maven 打包前同步到
`gms-server/src/main/resources/static`，也可以在 Maven 打包后注入 jar，但最终必须用
`jar tf` 或 `unzip -p` 验证 `BOOT-INF/classes/static/index.html` 及其引用的 hash
资源确实来自本次 `gms-ui/dist`。只执行 `mvn -pl gms-server -am package` 不足以证明
Web 后台已更新。

服务端运行时会从工作目录读取 `scripts`、`scripts-zh-CN`、`wz`、`wz-zh-CN`，因此补丁
除 `BeiDou.jar` 外，还必须包含 `$FROM..$TO` 中这些目录下新增或修改的文件。若差异中存在
删除文件，补丁必须包含删除清单并由安装器执行删除，不能只覆盖文件导致旧脚本或旧 WZ 残留。

服务端补丁交付物命名为 `BeiDou-Server-$FROM-to-$TO-patch.exe`，外层传输包命名为
`BeiDou-Server-$FROM-to-$TO-patch.zip`。zip 内只放同名 exe，不放备用 `bat`、`ps1`、
payload 目录或散文件。exe 必须是 Windows GUI 安装器，内嵌 `BeiDou.jar`、复制清单、删除
清单和运行时资源；运行后让用户选择包含 `BeiDou.jar`、`scripts-zh-CN`、`wz`、`wz-zh-CN`
的服务端工作目录，更新前检测服务是否仍在运行，备份所有将被覆盖或删除的文件，再执行删除和
覆盖。

服务端补丁生成后必须验证：`mvn -pl gms-server -am clean package` 成功；若 Web 后台有改动，
`yarn build` 成功；安装器内嵌载荷包含 `payload/BeiDou.jar`、复制清单和删除清单；jar 内包含
本次新增 migration 和最新 `BOOT-INF/classes/static`；zip 只包含一个 exe；输出 exe 与 zip 的
SHA256。交付说明必须写明 SQL 版本结论、安装步骤、校验 hash 和是否存在删除文件。
