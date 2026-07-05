param(
    [Parameter(Mandatory = $true)]
    [string] $UpdateRoot,

    [Parameter(Mandatory = $true)]
    [string] $LatestVersion,

    [string] $BaseVersion = $null,

    [string] $BaseUrl = "/client-update/files",

    [string] $Output = "manifest.json"
)

$ErrorActionPreference = "Stop"

$forbiddenNames = @("config.ini")
$forbiddenExtensions = @(".log", ".dmp", ".dump")

function Test-ForbiddenClientUpdatePath {
    param([string] $RelativePath)

    $normalized = $RelativePath.Replace("\", "/")
    $lower = $normalized.ToLowerInvariant()

    if ([string]::IsNullOrWhiteSpace($normalized)) {
        return $true
    }
    if ($normalized.StartsWith("/") -or $normalized.Contains("../") -or $normalized -eq "..") {
        return $true
    }
    if ($forbiddenNames -contains $lower) {
        return $true
    }
    foreach ($extension in $forbiddenExtensions) {
        if ($lower.EndsWith($extension)) {
            return $true
        }
    }
    if ($lower.StartsWith("backup/") -or $lower.Contains("/backup/") -or $lower.Contains(".wzpatch-backup")) {
        return $true
    }

    return $false
}

function Get-ClientUpdateRelativePath {
    param(
        [string] $Root,
        [string] $Path
    )

    $rootPath = [System.IO.Path]::GetFullPath($Root)
    if (-not $rootPath.EndsWith([System.IO.Path]::DirectorySeparatorChar)) {
        $rootPath += [System.IO.Path]::DirectorySeparatorChar
    }

    $pathFull = [System.IO.Path]::GetFullPath($Path)
    $rootUri = New-Object System.Uri($rootPath)
    $pathUri = New-Object System.Uri($pathFull)
    return [System.Uri]::UnescapeDataString($rootUri.MakeRelativeUri($pathUri).ToString()).Replace("\", "/")
}

function Get-ClientUpdateVersionSortKey {
    param([string] $Version)

    $normalized = $Version.Trim()
    if ($normalized.StartsWith("v", [System.StringComparison]::OrdinalIgnoreCase)) {
        $normalized = $normalized.Substring(1)
    }

    $tokens = [regex]::Matches($normalized, "\d+|[A-Za-z]+")
    if ($tokens.Count -eq 0) {
        return $Version.ToLowerInvariant()
    }

    $parts = foreach ($token in $tokens) {
        $value = $token.Value
        if ($value -match "^\d+$") {
            "n" + ([int64]$value).ToString("D12")
        } else {
            "s" + $value.ToLowerInvariant()
        }
    }

    return ($parts -join ".")
}

$root = (Resolve-Path -LiteralPath $UpdateRoot).Path
$filesRoot = Join-Path $root "files"
if (-not (Test-Path -LiteralPath $filesRoot -PathType Container)) {
    throw "Missing files directory: $filesRoot"
}

$versions = @()
$previousVersion = $BaseVersion
Get-ChildItem -LiteralPath $filesRoot -Directory | Sort-Object @{ Expression = { Get-ClientUpdateVersionSortKey $_.Name } } | ForEach-Object {
    $version = $_.Name
    $versionRoot = $_.FullName
    $files = @()

    Get-ChildItem -LiteralPath $versionRoot -File -Recurse | Sort-Object FullName | ForEach-Object {
        $relativePath = Get-ClientUpdateRelativePath -Root $versionRoot -Path $_.FullName
        if (Test-ForbiddenClientUpdatePath $relativePath) {
            throw "Forbidden client update file: $version/$relativePath"
        }

        $hash = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash
        $files += [ordered]@{
            path = $relativePath
            size = $_.Length
            sha256 = $hash
            url = "$($BaseUrl.TrimEnd('/'))/$version/$relativePath"
        }
    }

    $versions += [ordered]@{
        version = $version
        requiredFrom = $previousVersion
        files = $files
    }
    $previousVersion = $version
}

if ($versions.Count -gt 0 -and $versions[-1].version -ne $LatestVersion) {
    throw "LatestVersion '$LatestVersion' must match the last update directory '$($versions[-1].version)'"
}

$manifest = [ordered]@{
    latestVersion = $LatestVersion
    versions = $versions
}

$outputPath = if ([System.IO.Path]::IsPathRooted($Output)) {
    $Output
} else {
    Join-Path $root $Output
}

$json = $manifest | ConvertTo-Json -Depth 8
Set-Content -LiteralPath $outputPath -Value $json -Encoding UTF8
Write-Host "Wrote $outputPath"
