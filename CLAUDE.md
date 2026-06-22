# CLAUDE.md

本文件为 Claude Code 在此仓库中工作提供指导。

## 项目概述

BeiDou Server 是一个冒险岛 v83 服务端模拟器，基于 [Cosmic](https://github.com/P0nk/Cosmic) 二次开发，进行中文本地化和功能增强。项目包含两个主模块：

- **gms-server** — Java 21 / Spring Boot 3.2.3 后端（Netty 游戏服务器 + REST API）
- **gms-ui** — Vue 3 / Vite / TypeScript 网页管理后台（Arco Design）

## 构建与运行命令

### 服务端 (gms-server)

```bash
# 构建
mvn clean package -pl gms-server

# 运行（在 gms-server 目录下执行，需要 JDK 21）
java -Dspring.config.location=application.yml -jar target/BeiDou.jar

# 直接用 Maven 运行
mvn spring-boot:run -pl gms-server

# 运行单个测试
mvn test -pl gms-server -Dtest=TestClassName

# 运行全部测试
mvn test -pl gms-server
```

服务端首次启动会自动创建 MySQL 数据库。需要本地运行 MySQL 8+。

**IDE 设置**：IntelliJ IDEA 中将运行配置的工作目录设为 `gms-server`。非 root 数据库用户需要 `performance_schema.user_variables_by_table` 的 `SELECT` 权限和 `mysql` 数据库的 `SHOW VIEW` 权限。

### Web 前端 (gms-ui)

```bash
cd gms-ui
yarn install           # 首次运行
yarn dev               # 启动开发服务器 (Vite)
yarn build             # 生产构建
yarn type:check        # TypeScript 类型检查
yarn lint-staged       # 运行 lint-staged (ESLint + Prettier + Stylelint)
```

## 架构

### 服务端：网络层

两个 Netty TCP 服务器处理冒险岛客户端协议：

- **`LoginServer`**（端口 8484，通过 `gms.service.login-port` 配置）— 处理登录认证、世界/频道选择、角色列表
- **`ChannelServer`** — 每个游戏频道一个实例，处理所有游戏内封包通信

封包流程: `ClientCyphers` (AES-OFB 解密) → `PacketDecoder` → `PacketProcessor` 分发到对应类型的 handler → `PacketEncoder` → `MapleAESOFB` (加密)。Handler 位于 `net/server/handlers/` 下，按 opcode 类别组织（login、channel 等）。

### 服务端：游戏核心 (`org.gms.server`)

游戏世界模型 — 地图、怪物/NPC、任务、商店、掉落表、玩家移动、远征队、组队任务、小游戏、定时事件。`Server.java` 是主单例，初始化所有子系统（频道、WZ 数据、脚本引擎、事件、定时器）。

### 服务端：玩家状态 (`org.gms.client`)

每个玩家的会话状态：背包管理、快捷键绑定、技能宏、好友列表、角色创建（新手/老手）、GM 命令处理、自动封号检测。

### 服务端：脚本引擎 (`org.gms.scripting`)

游戏内容脚本使用 **JavaScript**，通过 GraalVM 的 GraalJS 引擎（JSR 223 `ScriptEngine`）执行。脚本类型及管理器：

| 类型 | 管理器 | 脚本目录 |
|------|--------|----------|
| NPC | `NPCScriptManager` | `scripts/npc/` |
| Quest | `QuestScriptManager` | `scripts/quest/` |
| Portal | `PortalScriptManager` | `scripts/portal/` |
| Reactor | `ReactorScriptManager` | `scripts/reactor/` |
| Event | `EventScriptManager` | `scripts/event/` |
| Item | `ItemScriptManager` | `scripts/item/` |
| Map | `MapScriptManager` | `scripts/map/` |

**语言解析**：脚本优先查找 `scripts-<lang>/`（如 `scripts-zh-CN/`），回退到 `scripts/`。每种脚本类型有基础模板（如 `NPC Base.js`、`QUEST Base.js`），定义了游戏脚本可用的 API。

### 服务端：WZ 数据提供 (`org.gms.provider.wz`)

游戏数据（物品、地图、怪物、技能等）以 XML WZ 文件形式存储在 `wz/` 和 `wz-zh-CN/` 下。`DataProviderFactory` 负责加载和缓存。语言特定的 WZ 文件遵循与脚本相同的解析模式覆盖基础文件。

### 服务端：数据与 API 层

- **`dao`** — MyBatis-Flex mapper 和 entity，用于数据库访问
- **`service`** — 业务逻辑，每个 service 类封装一个领域（AccountService、CharacterService、InventoryService 等）
- **`controller`** — Spring MVC REST 控制器，暴露在端口 8686 上，路径带版本号（`/v1/...`、`/v2/...`）。API 版本管理：`ApiConstant.LATEST` 控制默认版本，个别 controller 可按需固定到特定版本
- **`config`** — Spring Security（JWT 认证）、CORS、i18n 配置（`I18nConfig`）、Swagger/OpenAPI 配置

### 服务端：数据库迁移

Flyway 迁移脚本位于 `src/main/resources/db/migration/` — 版本化 SQL 文件（`V1.0.0__...`、`V1.0.1__...`），启动时自动执行以创建/演进数据库结构。

### 服务端：国际化 (i18n)

资源文件位于 `src/main/resources/i18n/`，包含 `zh_CN` 和 `en_US` 两种语言：
- `exception_*.properties` — 异常信息
- `log_*.properties` — 日志信息
- `message_*.properties` — 通用 UI 信息

语言通过 `application.yml` 中的 `gms.service.language` 设置。

### Web 前端架构

Vue 3 项目，使用 Arco Design Pro 模板：
- `src/router/` — 路由定义
- `src/api/` — Axios 服务层，封装 REST API 调用
- `src/store/` — Pinia 状态管理
- `src/views/` — 页面组件（仪表盘、游戏管理、账号、商城、背包）
- `src/components/` — 共享组件（图表、导航栏、标签栏、页脚）
- `src/locale/` — vue-i18n 语言文件（`zh-CN`、`en-US`）
- `src/config/` — 应用配置
- `config/` — Vite 构建配置（开发/生产）

## 仓库与外部目录结构

| 路径 (WSL) | Windows 路径 | 说明 |
|-------------|-------------|------|
| `/home/jackie/code/beidou/BeiDou-Server/` | — | **服务端仓库**（本仓库） |
| `├─ gms-server/` | — | Java 21 / Spring Boot 服务端 |
| `├─ gms-ui/` | — | Vue 3 网页管理后台 |
| `/mnt/d/Game/BeiDou/BeiDou-ijl15/` | `D:\Game\BeiDou\BeiDou-ijl15\` | **ijl15 DLL 项目**（C++ / Detours） |
| `├─ ezorsia/` | — | DLL 源代码（Client.cpp、codecaves.h、AddyLocations.h 等） |
| `├─ out/Release/` | — | 构建输出 → `ijl15.dll` |
| `/mnt/d/Game/BeiDou/BeiDou-Client/` | `D:\Game\BeiDou\BeiDou-Client\` | **游戏客户端目录** |
| `├─ ijl15.dll` | — | 从 ijl15 构建输出复制过来 |
| `├─ config.ini` | — | 运行时配置（分辨率、服务器 IP 等） |

**部署流程**: 构建 DLL → 复制 `ijl15/out/Release/ijl15.dll` → `BeiDou-Client/ijl15.dll`

## 其他目录

- **`gms-server/handbook/`** — 游戏数据速查表（物品 ID、怪物 ID、地图 ID、任务 ID 等），开发时参考
- **`gms-server/wz/` 和 `gms-server/wz-zh-CN/`** — XML WZ 游戏数据文件，按类型组织（Character.wz、Item.wz、Map.wz、Mob.wz、Npc.wz、Quest.wz、Skill.wz 等）

## 关键配置

所有服务端配置在 `gms-server/src/main/resources/application.yml`：
- `server.port` — REST API 端口（默认 8686）
- `gms.service.login-port` — 游戏客户端登录端口（默认 8484）
- `gms.service.language` — `zh-CN` 或 `en-US`
- `gms.service.wan-host` / `lan-host` / `localhost` — 网络地址
- `mybatis-flex.datasource.mysql` — 数据库连接
- `jwt.secret` — JWT 签名密钥（生产环境需修改）

服务端版本定义在 `ServerConstants.BEI_DOU_VERSION`（当前 `1.11`），游戏协议版本 `ServerConstants.VERSION`（83）。
