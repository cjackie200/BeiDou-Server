# BeiDouLauncher

`BeiDouLauncher.exe` 放在客户端根目录后运行。它会先访问服务端
`/client-update/manifest.json`，按版本顺序下载和覆盖客户端补丁，然后启动
`BeiDou.exe`。

## 配置

复制 `launcher.example.json` 为 `BeiDouLauncher.json`：

```json
{
  "manifestUrl": "http://101.35.56.98:8686/client-update/manifest.json",
  "gameExe": "BeiDou.exe"
}
```

客户端根目录需要有 `version.txt`。如果文件不存在，启动器会从 manifest 的第一个版本开始应用。

## 构建

在 `G:\beidou\BeiDou-Server` 执行：

```powershell
& 'G:\beidou\BeiDou-Server\.codex-tools\dotnet-sdk\sdk-8.0.416\dotnet.exe' publish `
  tools\BeiDouLauncher\BeiDouLauncher.csproj `
  -c Release `
  -r win-x86 `
  --self-contained true `
  -p:PublishSingleFile=true `
  -p:IncludeNativeLibrariesForSelfExtract=true
```

产物目录：

```text
tools/BeiDouLauncher/bin/Release/net8.0-windows/win-x86/publish/
```
