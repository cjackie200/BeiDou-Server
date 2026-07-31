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

    [switch] $SkipMavenPackage,

    [switch] $IncludeWorkingTree,

    [switch] $CreateZip
)

$ErrorActionPreference = "Stop"

function Get-RepoRoot {
    $root = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot "../.."))
    if (!(Test-Path -LiteralPath (Join-Path $root ".git") -PathType Container)) {
        throw "Cannot locate repository root from script directory: $PSScriptRoot"
    }
    return $root
}

function Resolve-RepoRelativeDirectory {
    param(
        [string] $RepoRoot,
        [string] $RelativePath,
        [string] $ParameterName
    )

    if ([string]::IsNullOrWhiteSpace($RelativePath)) {
        throw "$ParameterName cannot be empty."
    }
    if ([System.IO.Path]::IsPathRooted($RelativePath)) {
        throw "$ParameterName must be relative to the repository root: $RelativePath"
    }

    $repoFull = [System.IO.Path]::GetFullPath($RepoRoot).TrimEnd('\', '/') + [System.IO.Path]::DirectorySeparatorChar
    $resolved = [System.IO.Path]::GetFullPath((Join-Path $repoFull $RelativePath))
    if (-not $resolved.StartsWith($repoFull, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "$ParameterName must stay inside the repository root: $RelativePath"
    }
    return $resolved
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

    $normalized = ConvertTo-RuntimePayloadPath -Path $Path
    return $normalized -eq "BeiDou.jar" `
        -or $normalized.StartsWith("scripts/", [StringComparison]::OrdinalIgnoreCase) `
        -or $normalized.StartsWith("scripts-zh-CN/", [StringComparison]::OrdinalIgnoreCase) `
        -or $normalized.StartsWith("wz/", [StringComparison]::OrdinalIgnoreCase) `
        -or $normalized.StartsWith("wz-zh-CN/", [StringComparison]::OrdinalIgnoreCase)
}

function ConvertTo-RuntimePayloadPath {
    param([string] $Path)

    $normalized = $Path.Replace("\", "/")
    if ($normalized.StartsWith("gms-server/", [StringComparison]::OrdinalIgnoreCase)) {
        $candidate = $normalized.Substring("gms-server/".Length)
        if ($candidate.StartsWith("scripts/", [StringComparison]::OrdinalIgnoreCase) `
            -or $candidate.StartsWith("scripts-zh-CN/", [StringComparison]::OrdinalIgnoreCase) `
            -or $candidate.StartsWith("wz/", [StringComparison]::OrdinalIgnoreCase) `
            -or $candidate.StartsWith("wz-zh-CN/", [StringComparison]::OrdinalIgnoreCase)) {
            return $candidate
        }
    }
    return $normalized
}

$repoRoot = Get-RepoRoot
Set-Location $repoRoot
$outputRoot = Resolve-RepoRelativeDirectory -RepoRoot $repoRoot -RelativePath $OutputDir -ParameterName "OutputDir"

if (-not $SkipMavenPackage) {
    mvn -pl gms-server -am clean package -DskipTests
    if ($LASTEXITCODE -ne 0) {
        throw "Maven clean package failed with exit code $LASTEXITCODE."
    }
}

$jarPath = Join-Path $repoRoot "gms-server/target/BeiDou.jar"
if (!(Test-Path -LiteralPath $jarPath -PathType Leaf)) {
    throw "Missing built jar: $jarPath"
}

$safePatchVersion = $PatchVersion -replace '[^A-Za-z0-9._-]', '-'
$artifactName = "BeiDou-Server-$safePatchVersion-patch"
$buildRoot = Join-Path $outputRoot "$artifactName-build"
$payloadRoot = Join-Path $buildRoot "payload"
$installerRoot = Join-Path $repoRoot "tools/server-patch/Installer"
$resourceZip = Join-Path $installerRoot "Resources/patch-data.zip"

if (Test-Path -LiteralPath $buildRoot) {
    Remove-Item -LiteralPath $buildRoot -Recurse -Force
}
New-Item -ItemType Directory -Force -Path $payloadRoot | Out-Null
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $resourceZip) | Out-Null
New-Item -ItemType Directory -Force -Path $outputRoot | Out-Null

$copyManifest = [System.Collections.Generic.List[object]]::new()
$deleteManifest = [System.Collections.Generic.List[string]]::new()

Add-PayloadFile -Source $jarPath -RelativePath "BeiDou.jar" -Manifest $copyManifest -PayloadRoot $payloadRoot

$diffLines = [System.Collections.Generic.List[string]]::new()
git -c core.quotepath=false diff --name-status "$From..$To" | ForEach-Object { $diffLines.Add($_) | Out-Null }
if ($LASTEXITCODE -ne 0) {
    throw "git diff failed for $From..$To"
}

if ($IncludeWorkingTree) {
    git -c core.quotepath=false diff --name-status $To | ForEach-Object { $diffLines.Add($_) | Out-Null }
    if ($LASTEXITCODE -ne 0) {
        throw "git working tree diff failed for $To"
    }
    git -c core.quotepath=false ls-files --others --exclude-standard | ForEach-Object { $diffLines.Add("A`t$_") | Out-Null }
    if ($LASTEXITCODE -ne 0) {
        throw "git untracked file listing failed"
    }
}

$diffLines = $diffLines | Sort-Object -Unique

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

    $payloadPath = ConvertTo-RuntimePayloadPath -Path $path
    if ($status.StartsWith("D", [StringComparison]::OrdinalIgnoreCase)) {
        $deleteManifest.Add($payloadPath) | Out-Null
        continue
    }

    $source = Join-Path $repoRoot ($path.Replace("/", [IO.Path]::DirectorySeparatorChar))
    Add-PayloadFile -Source $source -RelativePath $payloadPath -Manifest $copyManifest -PayloadRoot $payloadRoot
}

if ($StaticVersions.Count -gt 0 -and (Test-Path -LiteralPath "client-update/manifest.json" -PathType Leaf)) {
    $clientManifestPath = (Resolve-Path -LiteralPath "client-update/manifest.json").Path
    $clientManifest = [System.IO.File]::ReadAllText(
        $clientManifestPath,
        [System.Text.Encoding]::UTF8
    ) | ConvertFrom-Json
    foreach ($version in $StaticVersions) {
        $versionEntry = $clientManifest.versions | Where-Object { $_.version -eq $version } | Select-Object -First 1
        if ($null -eq $versionEntry) {
            throw "Static update version is missing from client manifest: $version"
        }
        if ($null -eq $versionEntry.releaseNotes -or @($versionEntry.releaseNotes).Count -eq 0) {
            throw "Static update version must include releaseNotes: $version"
        }
    }
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

$assemblyName = $artifactName
& $Dotnet publish $installerRoot -c Release -r win-x64 --self-contained true `
    -p:PublishSingleFile=true `
    -p:AssemblyName=$assemblyName `
    -p:ApplicationTitle="BeiDou Server $PatchVersion Patch" `
    -o (Join-Path $buildRoot "publish")
if ($LASTEXITCODE -ne 0) {
    throw "dotnet publish failed."
}

$exeSource = Join-Path $buildRoot "publish/$assemblyName.exe"
$exeTarget = Join-Path $outputRoot "$assemblyName.exe"
Copy-Item -LiteralPath $exeSource -Destination $exeTarget -Force
$exeHash = (Get-FileHash -LiteralPath $exeTarget -Algorithm SHA256).Hash

$zipPath = $null
$zipHash = $null
if ($CreateZip) {
    $zipTarget = Join-Path $outputRoot "$assemblyName.zip"
    if (Test-Path -LiteralPath $zipTarget) {
        Remove-Item -LiteralPath $zipTarget -Force
    }
    Compress-Archive -LiteralPath $exeTarget -DestinationPath $zipTarget -Force
    $zipPath = ConvertTo-RelativePath -Root $repoRoot -Path $zipTarget
    $zipHash = (Get-FileHash -LiteralPath $zipTarget -Algorithm SHA256).Hash
}

[PSCustomObject]@{
    Exe = ConvertTo-RelativePath -Root $repoRoot -Path $exeTarget
    ExeSha256 = $exeHash
    Zip = $zipPath
    ZipSha256 = $zipHash
    PayloadFileCount = $copyManifest.Count
    DeleteFileCount = $deleteManifest.Count
}
