param(
    [Parameter(Mandatory = $true)]
    [string] $From,

    [Parameter(Mandatory = $true)]
    [string] $To,

    [Parameter(Mandatory = $true)]
    [string] $PatchVersion,

    [string[]] $StaticVersions = @(),

    [string] $OutputDir = "deploy",

    [string] $StaticRoot = "C:\inetpub\wwwroot",

    [string] $Dotnet = "dotnet",

    [switch] $SkipMavenPackage
)

$ErrorActionPreference = "Stop"

function Get-RepoRoot {
    $root = git rev-parse --show-toplevel
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($root)) {
        throw "Not inside a git repository."
    }
    return $root.Trim()
}

function ConvertTo-RelativePath {
    param(
        [string] $Root,
        [string] $Path
    )

    $rootPath = [System.IO.Path]::GetFullPath($Root)
    if (-not $rootPath.EndsWith([System.IO.Path]::DirectorySeparatorChar)) {
        $rootPath += [System.IO.Path]::DirectorySeparatorChar
    }

    $pathFull = [System.IO.Path]::GetFullPath($Path)
    $rootUri = [System.Uri]::new($rootPath)
    $pathUri = [System.Uri]::new($pathFull)
    return [System.Uri]::UnescapeDataString($rootUri.MakeRelativeUri($pathUri).ToString()).Replace("\", "/")
}

function Add-PayloadFile {
    param(
        [string] $Source,
        [string] $RelativePath,
        [System.Collections.Generic.List[object]] $Manifest,
        [string] $PayloadRoot
    )

    if (!(Test-Path -LiteralPath $Source -PathType Leaf)) {
        throw "Missing payload source: $Source"
    }

    $normalized = $RelativePath.Replace("\", "/")
    $dest = Join-Path $PayloadRoot ($normalized.Replace("/", [IO.Path]::DirectorySeparatorChar))
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $dest) | Out-Null
    Copy-Item -LiteralPath $Source -Destination $dest -Force

    $item = Get-Item -LiteralPath $dest
    $Manifest.Add([ordered]@{
        path = $normalized
        size = $item.Length
        sha256 = (Get-FileHash -LiteralPath $dest -Algorithm SHA256).Hash
    }) | Out-Null
}

function Test-RuntimePath {
    param([string] $Path)

    $normalized = $Path.Replace("\", "/")
    return $normalized -eq "BeiDou.jar" `
        -or $normalized.StartsWith("scripts/", [StringComparison]::OrdinalIgnoreCase) `
        -or $normalized.StartsWith("scripts-zh-CN/", [StringComparison]::OrdinalIgnoreCase) `
        -or $normalized.StartsWith("wz/", [StringComparison]::OrdinalIgnoreCase) `
        -or $normalized.StartsWith("wz-zh-CN/", [StringComparison]::OrdinalIgnoreCase)
}

$repoRoot = Get-RepoRoot
Set-Location $repoRoot

if (-not $SkipMavenPackage) {
    mvn -pl gms-server -am clean package -DskipTests
}

$jarPath = Join-Path $repoRoot "gms-server/target/BeiDou.jar"
if (!(Test-Path -LiteralPath $jarPath -PathType Leaf)) {
    throw "Missing built jar: $jarPath"
}

$buildRoot = Join-Path $repoRoot "$OutputDir/BeiDou-Server-$From-to-$To-patch-build"
$payloadRoot = Join-Path $buildRoot "payload"
$installerRoot = Join-Path $repoRoot "tools/server-patch/Installer"
$resourceZip = Join-Path $installerRoot "Resources/patch-data.zip"

if (Test-Path -LiteralPath $buildRoot) {
    Remove-Item -LiteralPath $buildRoot -Recurse -Force
}
New-Item -ItemType Directory -Force -Path $payloadRoot | Out-Null
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $resourceZip) | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $repoRoot $OutputDir) | Out-Null

$copyManifest = [System.Collections.Generic.List[object]]::new()
$deleteManifest = [System.Collections.Generic.List[string]]::new()

Add-PayloadFile -Source $jarPath -RelativePath "BeiDou.jar" -Manifest $copyManifest -PayloadRoot $payloadRoot

$diffLines = git diff --name-status "$From..$To"
if ($LASTEXITCODE -ne 0) {
    throw "git diff failed for $From..$To"
}

foreach ($line in $diffLines) {
    if ([string]::IsNullOrWhiteSpace($line)) {
        continue
    }

    $parts = $line -split "`t"
    $status = $parts[0]
    $path = $parts[-1].Replace("\", "/")
    if (-not (Test-RuntimePath $path)) {
        continue
    }

    if ($path -eq "BeiDou.jar") {
        continue
    }

    if ($status.StartsWith("D", [StringComparison]::OrdinalIgnoreCase)) {
        $deleteManifest.Add($path) | Out-Null
        continue
    }

    $source = Join-Path $repoRoot ($path.Replace("/", [IO.Path]::DirectorySeparatorChar))
    Add-PayloadFile -Source $source -RelativePath $path -Manifest $copyManifest -PayloadRoot $payloadRoot
}

if (Test-Path -LiteralPath "client-update/manifest.json" -PathType Leaf) {
    Add-PayloadFile -Source "client-update/manifest.json" -RelativePath "client-update/manifest.json" -Manifest $copyManifest -PayloadRoot $payloadRoot
}

foreach ($version in $StaticVersions) {
    $versionRoot = Join-Path $repoRoot "client-update/files/$version"
    if (!(Test-Path -LiteralPath $versionRoot -PathType Container)) {
        throw "Missing static update version directory: $versionRoot"
    }

    Get-ChildItem -LiteralPath $versionRoot -File -Recurse | Sort-Object FullName | ForEach-Object {
        $relative = ConvertTo-RelativePath -Root $repoRoot -Path $_.FullName
        Add-PayloadFile -Source $_.FullName -RelativePath $relative -Manifest $copyManifest -PayloadRoot $payloadRoot
    }
}

$metadata = [ordered]@{
    version = $PatchVersion
    title = "BeiDou Server $PatchVersion Patch"
    staticRoot = $StaticRoot
    logName = "patch-$PatchVersion.log"
    failedLogName = "patch-$PatchVersion.failed.log"
}

ConvertTo-Json -InputObject @($copyManifest.ToArray()) -Depth 6 | Set-Content -LiteralPath (Join-Path $payloadRoot "copy-manifest.json") -Encoding UTF8
ConvertTo-Json -InputObject @($deleteManifest.ToArray()) -Depth 4 | Set-Content -LiteralPath (Join-Path $payloadRoot "delete-manifest.json") -Encoding UTF8
$metadata | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $payloadRoot "patch-metadata.json") -Encoding UTF8

if (Test-Path -LiteralPath $resourceZip) {
    Remove-Item -LiteralPath $resourceZip -Force
}
Compress-Archive -Path (Join-Path $payloadRoot "*") -DestinationPath $resourceZip -Force

$assemblyName = "BeiDou-Server-$From-to-$To-patch"
& $Dotnet publish $installerRoot -c Release -r win-x64 --self-contained true `
    -p:PublishSingleFile=true `
    -p:AssemblyName=$assemblyName `
    -p:ApplicationTitle="BeiDou Server $PatchVersion Patch" `
    -o (Join-Path $buildRoot "publish")
if ($LASTEXITCODE -ne 0) {
    throw "dotnet publish failed."
}

$exeSource = Join-Path $buildRoot "publish/$assemblyName.exe"
$exeTarget = Join-Path $repoRoot "$OutputDir/$assemblyName.exe"
$zipTarget = Join-Path $repoRoot "$OutputDir/$assemblyName.zip"
Copy-Item -LiteralPath $exeSource -Destination $exeTarget -Force
if (Test-Path -LiteralPath $zipTarget) {
    Remove-Item -LiteralPath $zipTarget -Force
}
Compress-Archive -LiteralPath $exeTarget -DestinationPath $zipTarget -Force

$exeHash = (Get-FileHash -LiteralPath $exeTarget -Algorithm SHA256).Hash
$zipHash = (Get-FileHash -LiteralPath $zipTarget -Algorithm SHA256).Hash

[PSCustomObject]@{
    Exe = $exeTarget
    ExeSha256 = $exeHash
    Zip = $zipTarget
    ZipSha256 = $zipHash
    PayloadFileCount = $copyManifest.Count
    DeleteFileCount = $deleteManifest.Count
}
