# 客户端静态更新规则

客户端更新采用“启动器先检查更新，再启动游戏”的方式。玩家运行
`BeiDouLauncher.exe`，启动器读取 `BeiDouLauncher.json` 中的 manifest 地址，发现新版本时先
弹窗提示，玩家确认后下载、校验、备份并覆盖客户端文件，最后启动 `BeiDou.exe`。

当前小规模服推荐使用静态网站分发客户端更新文件：服务端 8686 端口不对公网开放，只让玩家访问
HTTP 80 或 HTTPS 443 上的静态文件。Spring Boot 内置的 `/client-update/**` 接口保留作为内网
或临时调试方案，不作为首选公网分发方式。

## 推荐结构

仓库内维护源数据：

```text
client-update/
  manifest.json
  files/
    v1.0.1/
      Data/Skill/210.img
```

静态网站发布目录：

```text
wwwroot/
  web.config
  client-update/
    manifest.json
    files/
      v1.0.1/
        Data/Skill/210.img
```

客户端根目录：

```text
BeiDouLauncher.exe
BeiDouLauncher.json
BeiDou.exe
version.txt
Data/
```

`BeiDouLauncher.json` 示例：

```json
{
  "manifestUrl": "http://101.35.56.98/client-update/manifest.json",
  "gameExe": "BeiDou.exe"
}
```

不要把公网玩家配置成 `http://101.35.56.98:8686/client-update/manifest.json`，除非 8686 已经做了
指定 IP 白名单或反向代理保护。

## Manifest 规则

`manifest.json` 必须由 `tools/Generate-ClientUpdateManifest.ps1` 生成，不要手写。

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tools\Generate-ClientUpdateManifest.ps1 `
  -UpdateRoot .\client-update `
  -LatestVersion v1.0.1 `
  -BaseVersion v1.0.0
```

生成结果示例：

```json
{
  "latestVersion": "v1.0.1",
  "versions": [
    {
      "version": "v1.0.1",
      "requiredFrom": "v1.0.0",
      "files": [
        {
          "path": "Data/Skill/210.img",
          "size": 144535,
          "sha256": "763C37EFD5A29CC0D9D802FFD6581BB26709B144842154AF49D8CF295AA2F237",
          "url": "/client-update/files/v1.0.1/Data/Skill/210.img"
        }
      ]
    }
  ]
}
```

每个版本目录只放该版本新增或变更的文件。禁止发布：

- `config.ini`
- 日志、dump、crash-dumps
- `.wzpatch-backup`
- `backup/`
- 临时工具输出

发布前必须确认：

1. `latestVersion` 出现在 `versions[].version` 中。
2. 第一个增量版本的 `requiredFrom` 是线上客户端基线，例如 `v1.0.0`。
3. 后续增量版本的 `requiredFrom` 等于上一个版本号，形成连续升级链。
4. `versions` 中没有重复版本号。
5. `client-update/files/<版本>/` 包含 manifest 列出的每个文件。
6. 每个文件的大小和 SHA256 与 manifest 一致。

如果玩家本地版本无法沿 manifest 升级链升级到 `latestVersion`，启动器必须停止更新并提示错误，
不能直接启动游戏。

## 启动器提示规则

启动器必须有明确提示，避免玩家误以为“没更新，直接进游戏”：

1. 没有更新时：不弹更新确认框，直接启动 `BeiDou.exe`。
2. 发现更新时：先弹窗提示当前版本、目标版本和文件数量。
3. 玩家取消时：不下载、不覆盖、不启动游戏。
4. 更新成功后：弹窗提示已更新到目标版本，再启动游戏。
5. 校验失败、下载失败、备份失败或游戏正在运行时：弹窗说明原因，并停止启动游戏。

提示文案应面向玩家，不写内部实现细节。示例：

```text
发现客户端更新：v1.0.0 -> v1.0.1
需要更新 1 个文件。更新前请确认游戏已经关闭。
是否现在更新？
```

## 静态网站部署

Windows 服务器推荐使用 IIS 托管静态更新文件。

安装 IIS 时至少勾选：

- Web 服务器
- 静态内容
- 默认文档
- HTTP 错误

部署步骤：

1. 准备 `wwwroot` 目录，内部包含 `web.config` 和 `client-update/`。
2. 把 `wwwroot` 复制到服务器，例如 `C:\inetpub\wwwroot`。
3. IIS 网站根目录指向该 `wwwroot`。
4. Windows 防火墙放行 TCP 80；如果使用云服务器，也要在安全组放行 TCP 80。
5. 浏览器访问 `http://服务器IP/client-update/manifest.json`，确认能看到 JSON。
6. 浏览器访问 manifest 中任意 `url`，确认能下载文件。
7. 客户端 `BeiDouLauncher.json` 使用静态地址。

IIS 必须允许 `.json` 和 `.img` 文件下载。`web.config` 示例：

```xml
<?xml version="1.0" encoding="utf-8"?>
<configuration>
  <system.webServer>
    <staticContent>
      <remove fileExtension=".json" />
      <mimeMap fileExtension=".json" mimeType="application/json" />
      <remove fileExtension=".img" />
      <mimeMap fileExtension=".img" mimeType="application/octet-stream" />
    </staticContent>
  </system.webServer>
</configuration>
```

如果访问 manifest 返回 404，优先检查 IIS 网站根目录是否直接指向 `wwwroot`，不要多套一层目录。
如果 manifest 能访问但 `.img` 下载 404 或 403，优先检查 MIME 映射。

## 8686 内置接口

服务端仍提供以下接口：

- `GET /client-update/manifest.json`
- `GET /client-update/files/{version}/**`

接口从服务端进程工作目录读取 `client-update`，不是从 jar 内读取。只有在以下场景建议使用：

- 本机或内网测试。
- 8686 已经只对指定 IP 开放。
- 由 IIS、Nginx 或其他反向代理转发 `/client-update/**`，公网不直接暴露 8686。

公网直开 8686 不推荐。游戏登录端口按服务端原有配置开放即可，客户端更新文件不需要和游戏登录共用
端口。

## 打包要求

客户端更新相关提交和补丁必须包含：

- `client-update/manifest.json`
- `client-update/files/**`
- `tools/Generate-ClientUpdateManifest.ps1`
- `tools/BeiDouLauncher/**` 源码和配置模板
- `docs/client-update.md`

静态网站交付包必须包含：

```text
BeiDou-static-update-site-<版本>/
  README.txt
  client/
    BeiDouLauncher.exe
    BeiDouLauncher.json
  wwwroot/
    web.config
    client-update/
      manifest.json
      files/**
```

服务端补丁如果同时交付客户端更新能力，安装器必须把以下内容写入服务端工作目录或静态网站发布目录：

- `client-update/manifest.json`
- `client-update/files/**`
- 如使用 Spring Boot 内置接口，还要包含最新 `BeiDou.jar`
- 如使用 IIS 静态网站，还要包含 `wwwroot/web.config`

不要把 `deploy/`、启动器 `bin/obj`、临时 zip、exe 打包输出提交进 git。它们是生成物，可以重新构建。

## 打包流程

每次制作客户端静态更新包时按顺序执行：

1. 把本次要更新的客户端文件放入 `client-update/files/<新版本>/`。
2. 运行 `tools/Generate-ClientUpdateManifest.ps1` 重新生成 manifest。
3. 构建启动器：

```powershell
dotnet publish tools\BeiDouLauncher\BeiDouLauncher.csproj `
  -c Release `
  -r win-x86 `
  --self-contained true `
  -p:PublishSingleFile=true `
  -p:IncludeNativeLibrariesForSelfExtract=true
```

4. 生成静态网站目录 `wwwroot/client-update/**`，并放入 `web.config`。
5. 生成玩家客户端用的 `BeiDouLauncher.json`，manifest 地址使用静态网站地址。
6. 压缩为 `BeiDou-static-update-site-<版本>.zip`。
7. 计算 zip、`BeiDouLauncher.exe`、manifest 中每个文件的 SHA256。

验收必须确认：

1. zip 内有 `client/BeiDouLauncher.exe` 和 `client/BeiDouLauncher.json`。
2. zip 内有 `wwwroot/web.config`。
3. zip 内有 `wwwroot/client-update/manifest.json`。
4. zip 内有 manifest 列出的每个 `wwwroot/client-update/files/**` 文件。
5. 浏览器能打开公网静态 manifest 地址。
6. 浏览器能下载 manifest 中任意一个文件地址。
7. 旧客户端运行启动器会先弹更新提示，确认后能更新到 `latestVersion`。
8. 更新完成后 `version.txt` 变成 `latestVersion`，游戏能正常启动。

## 玩家更新方法

首次启用启动器时，发给玩家：

- `BeiDouLauncher.exe`
- `BeiDouLauncher.json`

玩家把这两个文件放到包含 `BeiDou.exe` 的客户端根目录，以后从 `BeiDouLauncher.exe` 启动游戏。

如果玩家已经有旧版启动器，只改了更新内容，不需要重新发启动器；只需要更新静态网站上的
`client-update/manifest.json` 和 `client-update/files/**`。如果启动器本身有改动，必须重新发
`BeiDouLauncher.exe`。
