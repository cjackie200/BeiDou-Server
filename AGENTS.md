# Repository Guidelines

## 项目结构与模块组织

本仓库包含 Java 游戏服务端和 Vue 管理后台。根目录 `pom.xml` 聚合
`gms-server`；Java 源码位于 `gms-server/src/main/java/org/gms`，资源文件位于
`gms-server/src/main/resources`，Flyway 迁移脚本位于
`gms-server/src/main/resources/db/migration`，命名示例为
`V1.0.60__create_example.sql`。运行时资源按语言区分，包括 `scripts-zh-CN`、
`scripts`、`wz-zh-CN` 和 `wz`。前端代码在 `gms-ui/src`，生产构建产物可复制到
`gms-server/src/main/resources/static`。

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
