# 服务端补丁和静态更新打包流程

本文是 BeiDou 服务端补丁的标准流程，供后续 agent 直接复用。补丁工具源码在
`tools/server-patch`，打包产物仍输出到 `deploy/`，不要提交产物。

本文和打包工具中的开发机路径全部以仓库根目录为基准。仓库可以位于任意磁盘和目录，
不依赖任何开发者电脑上的绝对工程路径。服务端安装目录及 IIS 静态目录属于部署配置，
不受这条开发机产物路径规则影响。

## 适用范围

服务端补丁用于同时更新这些内容：

- `gms-server/target/BeiDou.jar`
- 服务端工作目录运行时读取的 `scripts`、`scripts-zh-CN`、`wz`、`wz-zh-CN`
- 需要放到 IIS 静态站点的 `client-update/manifest.json`
- 需要随服务端补丁一起下发的 `client-update/files/<version>/...`

`deploy/**`、`target/**`、`client-update/files/**` 和安装器内嵌的
`tools/server-patch/Installer/Resources/patch-data.zip` 都是产物，不能提交。

## 打包前检查

1. 确认线上基线和目标版本，不要默认使用最早 tag。
2. 查看当前分支和工作区：

```powershell
git status -sb
git diff --name-status <FROM>..<TO>
```

3. 如果有 Web 后台改动，先执行 `gms-ui` 构建，并确认 jar 内包含最新
   `BOOT-INF/classes/static/**`。
4. 如果有客户端静态更新，先准备好 `client-update/manifest.json` 和对应版本目录。

## 生成服务端补丁

必须先构建 jar。默认脚本会执行：

```powershell
mvn -pl gms-server -am clean package -DskipTests
```

然后运行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/server-patch/Build-BeiDouServerPatch.ps1 `
  -From <FROM> `
  -To <TO> `
  -PatchVersion <VERSION> `
  -StaticVersions v1.0.4 `
  -Dotnet <dotnet.exe>
```

上面的相对命令从仓库根目录执行。脚本内部会根据自身所在的
`tools/server-patch/Build-BeiDouServerPatch.ps1` 自动定位仓库根目录；从其他目录调用时，
只需正确指向这个脚本文件，后续打包过程不依赖当前 PowerShell 工作目录。

示例：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/server-patch/Build-BeiDouServerPatch.ps1 `
  -From xjc `
  -To v1.0.4 `
  -PatchVersion v1.0.4 `
  -StaticVersions v1.0.4 `
  -Dotnet .codex-tools/dotnet-sdk/sdk-8.0.416/dotnet.exe
```

输出：

- `deploy/BeiDou-Server-<FROM>-to-<TO>-patch.exe`
- `deploy/BeiDou-Server-<FROM>-to-<TO>-patch.zip`

以上都是相对于仓库根目录的路径。`-OutputDir` 也只接受仓库内相对路径，例如
`deploy` 或 `artifacts/server-patch`；绝对路径及会通过 `..` 跳出仓库的路径会被拒绝。
工具最终打印的 `Exe` 和 `Zip` 字段同样使用仓库相对路径，便于其他电脑复用日志和命令。

zip 内只能有同名 exe，不能放备用脚本、payload 目录或散文件。

## 工具行为

构建脚本会：

- 执行 Maven clean package，确保 `BeiDou.jar` 是最新的。
- 从 `git diff --name-status <FROM>..<TO>` 收集运行时资源改动。
- 始终把 `gms-server/target/BeiDou.jar` 放入 payload。
- 把 `client-update/manifest.json` 和 `-StaticVersions` 指定的静态版本目录放入 payload。
- 生成 `copy-manifest.json`、`delete-manifest.json` 和 `patch-metadata.json`。
- 发布 Windows x64 单文件 GUI 安装器。
- 输出 exe、zip 和 SHA256。

安装器会：

- 让用户选择包含 `BeiDou.jar` 的服务端工作目录。
- 检查 `BeiDou.jar` 是否被占用；被占用则拒绝安装。
- 覆盖或删除前备份文件到 `backup/patch-<VERSION>-<时间戳>/`。
- 将 `client-update/**` 自动写入 `C:\inetpub\wwwroot\client-update\...`。
- 对所有复制后的文件做 SHA256 校验。
- 写入 `patch-<VERSION>.log`，失败时写入 `patch-<VERSION>.failed.log`。

## 验证清单

打包后至少确认：

```powershell
Get-ChildItem deploy/BeiDou-Server-<FROM>-to-<TO>-patch.zip
Get-FileHash deploy/BeiDou-Server-<FROM>-to-<TO>-patch.exe -Algorithm SHA256
Get-FileHash deploy/BeiDou-Server-<FROM>-to-<TO>-patch.zip -Algorithm SHA256
```

还要检查：

- `mvn -pl gms-server -am clean package -DskipTests` 成功。
- 如果有 Web 后台变更，jar 内有最新 `BOOT-INF/classes/static/index.html`。
- payload 有 `BeiDou.jar`、`copy-manifest.json`、`delete-manifest.json`、`patch-metadata.json`。
- 需要删除的运行时文件已经进入 `delete-manifest.json`。
- 需要部署的静态版本目录已经通过 `-StaticVersions` 放入 payload。
- `git status --short` 中没有 `deploy/**`、`target/**`、`client-update/files/**` 产物。

## 只更新一两个文件时

如果改动只有一两个静态文件或 WZ 文件，优先告诉服主精确替换路径，不必打 exe。
只有涉及 jar、多目录资源、删除清单或静态更新组合时，才使用本工具生成服务端补丁。
