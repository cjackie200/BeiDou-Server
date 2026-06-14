# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

## Project Overview

BeiDou Server is a MapleStory v83 server emulator, forked from [Cosmic](https://github.com/P0nk/Cosmic), with Chinese localization and enhancements. The project has two main modules:

- **gms-server** — Java 21 / Spring Boot 3.2.3 backend (Netty game server + REST API)
- **gms-ui** — Vue 3 / Vite / TypeScript web admin panel (Arco Design)

## Build & Run Commands

### Server (gms-server)

```bash
# Build the server
mvn clean package -pl gms-server

# Run (from gms-server directory, needs JDK 21)
java -Dspring.config.location=application.yml -jar target/BeiDou.jar

# Or run directly with Maven
mvn spring-boot:run -pl gms-server

# Run a single test
mvn test -pl gms-server -Dtest=TestClassName

# Run all tests
mvn test -pl gms-server
```

The server auto-creates the MySQL database on first startup if it doesn't exist. Requires MySQL 8+ running locally.

**IDE setup**: In IntelliJ IDEA, set the run configuration's working directory to `gms-server`. Non-root database users need `SELECT` on `performance_schema.user_variables_by_table` and `SHOW VIEW` on the `mysql` database.

### Web Frontend (gms-ui)

```bash
cd gms-ui
yarn install           # first time only
yarn dev               # start dev server (Vite)
yarn build             # production build
yarn type:check        # TypeScript type checking only
yarn lint-staged       # run lint-staged (ESLint + Prettier + Stylelint)
```

## Architecture

### Server: Networking Layer

Two Netty TCP servers for the MapleStory client protocol:

- **`LoginServer`** (port 8484, configurable via `gms.service.login-port`) — handles authentication, world/channel selection, character list
- **`ChannelServer`** — one instance per game channel; handles all in-game packet communication

Packet flow: `ClientCyphers` (AES-OFB decryption) → `PacketDecoder` → `PacketProcessor` dispatches to type-specific handlers → `PacketEncoder` → `MapleAESOFB` (encryption). Handlers live under `net/server/handlers/` organized by opcode category (login, channel, etc.).

### Server: Game Core (`org.gms.server`)

The game world model — maps, life (mobs/NPCs), quests, shops, loot tables, player movement, expeditions, party quests, minigames, and timed events. `Server.java` is the main singleton that initializes all subsystems (channels, WZ data, script engine, events, timers).

### Server: Client State (`org.gms.client`)

Per-player session state: inventory management, key bindings, skill macros, buddy lists, character creation (novice/veteran paths), GM command processing, auto-ban detection.

### Server: Script Engine (`org.gms.scripting`)

Game content scripts are **JavaScript** executed via GraalVM's GraalJS engine (JSR 223 `ScriptEngine`). Script types and their managers:

| Type | Manager | Script Directory |
|------|---------|------------------|
| NPC | `NPCScriptManager` | `scripts/npc/` |
| Quest | `QuestScriptManager` | `scripts/quest/` |
| Portal | `PortalScriptManager` | `scripts/portal/` |
| Reactor | `ReactorScriptManager` | `scripts/reactor/` |
| Event | `EventScriptManager` | `scripts/event/` |
| Item | `ItemScriptManager` | `scripts/item/` |
| Map | `MapScriptManager` | `scripts/map/` |

**Language resolution**: Scripts first check `scripts-<lang>/` (e.g., `scripts-zh-CN/`), falling back to `scripts/`. Each script type has base templates (e.g., `NPC Base.js`, `QUEST Base.js`) that define the API surface available to game scripts.

### Server: WZ Data Provider (`org.gms.provider.wz`)

Game data (items, maps, mobs, skills, etc.) is stored as XML WZ files under `wz/` and `wz-zh-CN/`. `DataProviderFactory` loads and caches these. Language-specific WZ files override the base ones following the same resolution pattern as scripts.

### Server: Data & API Layers

- **`dao`** — MyBatis-Flex mappers and entities for database access
- **`service`** — Business logic; each service class wraps a domain (AccountService, CharacterService, InventoryService, etc.)
- **`controller`** — Spring MVC REST controllers exposed on port 8686 under versioned paths (`/v1/...`, `/v2/...`). API versioning: `ApiConstant.LATEST` controls the default version; individual controllers pin to specific versions when needed
- **`config`** — Spring Security (JWT auth), CORS, i18n config (`I18nConfig`), Swagger/OpenAPI config

### Server: Database Migrations

Flyway migrations in `src/main/resources/db/migration/` — versioned SQL files (`V1.0.0__...`, `V1.0.1__...`) that auto-run on startup to create/evolve the schema.

### Server: i18n

Resource bundles in `src/main/resources/i18n/` with `zh_CN` and `en_US` variants for:
- `exception_*.properties` — exception messages
- `log_*.properties` — log messages
- `message_*.properties` — general UI messages

Language is set via `gms.service.language` in `application.yml`.

### Web Frontend Architecture

Vue 3 project using Arco Design Pro template:
- `src/router/` — route definitions
- `src/api/` — Axios service layer for REST API calls
- `src/store/` — Pinia stores
- `src/views/` — page components (dashboard, game management, account, shop, inventory)
- `src/components/` — shared components (charts, navbar, tab-bar, footer)
- `src/locale/` — vue-i18n locale files (`zh-CN`, `en-US`)
- `src/config/` — app configuration
- `config/` — Vite build configs (dev/prod)

## Additional Directories

- **`gms-server/handbook/`** — Game data lookup tables (item IDs, mob IDs, map IDs, quest IDs, etc.) for reference during development
- **`gms-server/wz/` and `gms-server/wz-zh-CN/`** — XML WZ game data files organized by type (Character.wz, Item.wz, Map.wz, Mob.wz, Npc.wz, Quest.wz, Skill.wz, etc.)

## Key Configuration

All server settings in `gms-server/src/main/resources/application.yml`:
- `server.port` — REST API port (default 8686)
- `gms.service.login-port` — game client login port (default 8484)
- `gms.service.language` — `zh-CN` or `en-US`
- `gms.service.wan-host` / `lan-host` / `localhost` — network addresses
- `mybatis-flex.datasource.mysql` — database connection
- `jwt.secret` — JWT signing key (change in production)

Server version is defined in `ServerConstants.BEI_DOU_VERSION` (currently `1.11`) and game protocol version is `ServerConstants.VERSION` (83).
