using System.Diagnostics;
using System.Net.Http.Json;
using System.Security.Cryptography;
using System.Text.Json;
using System.Windows.Forms;

namespace BeiDouLauncher;

internal static class Program
{
    private const string ConfigFileName = "BeiDouLauncher.json";
    internal const string VersionFileName = "version.txt";
    internal const string CacheDirectoryName = ".bd-update-cache";
    internal const string BackupDirectoryName = "backup";

    [STAThread]
    private static async Task Main()
    {
        ApplicationConfiguration.Initialize();

        try
        {
            var clientRoot = AppContext.BaseDirectory;
            var config = await LauncherConfig.LoadAsync(Path.Combine(clientRoot, ConfigFileName));
            var updater = new ClientUpdater(clientRoot, config);
            await updater.RunAsync();
        }
        catch (Exception ex)
        {
            MessageBox.Show(ex.Message, "BeiDou Launcher", MessageBoxButtons.OK, MessageBoxIcon.Error);
        }
    }
}

internal sealed record LauncherConfig(string ManifestUrl, string GameExe)
{
    public static async Task<LauncherConfig> LoadAsync(string path)
    {
        if (!File.Exists(path))
        {
            throw new InvalidOperationException(
                $"Missing {Path.GetFileName(path)}. Please copy launcher.example.json and set manifestUrl.");
        }

        await using var stream = File.OpenRead(path);
        var config = await JsonSerializer.DeserializeAsync<LauncherConfig>(
            stream,
            new JsonSerializerOptions { PropertyNameCaseInsensitive = true });

        if (config == null || string.IsNullOrWhiteSpace(config.ManifestUrl))
        {
            throw new InvalidOperationException("Launcher config must include manifestUrl.");
        }

        var gameExe = string.IsNullOrWhiteSpace(config.GameExe) ? "BeiDou.exe" : config.GameExe;
        return config with { GameExe = gameExe };
    }
}

internal sealed class UpdateManifest
{
    public string LatestVersion { get; set; } = "";
    public List<UpdateVersion> Versions { get; set; } = [];
}

internal sealed class UpdateVersion
{
    public string Version { get; set; } = "";
    public string? RequiredFrom { get; set; }
    public List<UpdateFile> Files { get; set; } = [];
}

internal sealed class UpdateFile
{
    public string Path { get; set; } = "";
    public long Size { get; set; }
    public string Sha256 { get; set; } = "";
    public string Url { get; set; } = "";
}

internal sealed class ClientUpdater
{
    private static readonly JsonSerializerOptions JsonOptions = new() { PropertyNameCaseInsensitive = true };
    private readonly string clientRoot;
    private readonly LauncherConfig config;

    public ClientUpdater(string clientRoot, LauncherConfig config)
    {
        this.clientRoot = NormalizeRootPath(clientRoot);
        this.config = config;
    }

    public async Task RunAsync()
    {
        EnsureGameIsClosed();

        using var httpClient = new HttpClient();
        var manifest = await LoadManifestAsync(httpClient);
        var currentVersion = ReadCurrentVersion();
        var updates = SelectUpdates(manifest, currentVersion);
        if (updates.Count > 0)
        {
            MessageBox.Show(
                $"发现客户端更新：{DisplayVersion(currentVersion)} -> {manifest.LatestVersion}\r\n\r\n点击确定后开始下载并安装更新。",
                "BeiDou Launcher",
                MessageBoxButtons.OK,
                MessageBoxIcon.Information);
        }

        foreach (var update in updates)
        {
            await ApplyUpdateAsync(httpClient, update, manifest);
            WriteCurrentVersion(update.Version);
            currentVersion = update.Version;
        }

        if (!string.Equals(currentVersion, manifest.LatestVersion, StringComparison.OrdinalIgnoreCase))
        {
            throw new InvalidOperationException(
                $"Update manifest cannot reach latest version {manifest.LatestVersion} from current version {ReadCurrentVersion()}.");
        }

        if (updates.Count > 0)
        {
            MessageBox.Show(
                $"客户端已更新到 {manifest.LatestVersion}，即将启动游戏。",
                "BeiDou Launcher",
                MessageBoxButtons.OK,
                MessageBoxIcon.Information);
        }

        StartGame();
    }

    private void EnsureGameIsClosed()
    {
        var exeName = Path.GetFileNameWithoutExtension(config.GameExe);
        if (string.IsNullOrWhiteSpace(exeName))
        {
            throw new InvalidOperationException("gameExe is invalid.");
        }

        if (Process.GetProcessesByName(exeName).Length > 0)
        {
            throw new InvalidOperationException($"Please close {config.GameExe} before updating.");
        }
    }

    private async Task<UpdateManifest> LoadManifestAsync(HttpClient httpClient)
    {
        var manifest = await httpClient.GetFromJsonAsync<UpdateManifest>(config.ManifestUrl, JsonOptions);
        if (manifest == null)
        {
            throw new InvalidOperationException("Update manifest is empty.");
        }

        if (string.IsNullOrWhiteSpace(manifest.LatestVersion))
        {
            throw new InvalidOperationException("Update manifest must include latestVersion.");
        }

        manifest.Versions ??= [];
        ValidateManifest(manifest);
        return manifest;
    }

    private string ReadCurrentVersion()
    {
        var path = Path.Combine(clientRoot, Program.VersionFileName);
        if (!File.Exists(path))
        {
            return "";
        }

        return File.ReadAllText(path).Trim();
    }

    private static List<UpdateVersion> SelectUpdates(UpdateManifest manifest, string currentVersion)
    {
        if (string.Equals(currentVersion, manifest.LatestVersion, StringComparison.OrdinalIgnoreCase))
        {
            return [];
        }

        var versions = manifest.Versions;
        var currentIndex = versions.FindIndex(v =>
            string.Equals(v.Version, currentVersion, StringComparison.OrdinalIgnoreCase));
        var updates = currentIndex >= 0
            ? versions.Skip(currentIndex + 1).ToList()
            : string.IsNullOrWhiteSpace(currentVersion)
                ? versions
                : versions;

        if (updates.Count == 0
            || !string.Equals(updates.Last().Version, manifest.LatestVersion, StringComparison.OrdinalIgnoreCase))
        {
            throw new InvalidOperationException(
                $"Update manifest cannot reach latest version {manifest.LatestVersion} from current version {currentVersion}.");
        }

        var expectedFrom = currentIndex >= 0
            ? currentVersion
            : string.IsNullOrWhiteSpace(currentVersion)
                ? updates.First().RequiredFrom
                : currentVersion;

        foreach (var update in updates)
        {
            if (!string.IsNullOrWhiteSpace(update.RequiredFrom)
                && !string.Equals(update.RequiredFrom, expectedFrom, StringComparison.OrdinalIgnoreCase))
            {
                throw new InvalidOperationException(
                    $"Client version {expectedFrom} cannot update to {update.Version}. Required: {update.RequiredFrom}.");
            }
            expectedFrom = update.Version;
        }

        return updates;
    }

    private async Task ApplyUpdateAsync(HttpClient httpClient, UpdateVersion update, UpdateManifest manifest)
    {
        if (string.IsNullOrWhiteSpace(update.Version))
        {
            throw new InvalidOperationException("Update version is empty.");
        }

        if (update.Files == null || update.Files.Count == 0)
        {
            return;
        }

        var downloaded = new List<(UpdateFile File, string CachePath)>();
        foreach (var file in update.Files)
        {
            ValidateUpdateFile(file);
            var targetPath = ResolveClientPath(file.Path);
            if (File.Exists(targetPath) && await VerifyFileAsync(targetPath, file))
            {
                continue;
            }

            var cachePath = await DownloadFileAsync(httpClient, update.Version, file);
            downloaded.Add((file, cachePath));
        }

        var backupRoot = Path.Combine(
            clientRoot,
            Program.BackupDirectoryName,
            $"launcher-{manifest.LatestVersion}-{DateTime.Now:yyyyMMdd-HHmmss}");

        foreach (var item in downloaded)
        {
            var targetPath = ResolveClientPath(item.File.Path);
            BackupExistingFile(targetPath, backupRoot, item.File.Path);
            Directory.CreateDirectory(Path.GetDirectoryName(targetPath)!);
            File.Copy(item.CachePath, targetPath, true);
        }
    }

    private async Task<string> DownloadFileAsync(HttpClient httpClient, string version, UpdateFile file)
    {
        var cachePath = ResolveCachePath(version, file.Path);
        if (File.Exists(cachePath) && await VerifyFileAsync(cachePath, file))
        {
            return cachePath;
        }

        Directory.CreateDirectory(Path.GetDirectoryName(cachePath)!);
        var downloadUrl = ResolveDownloadUrl(file.Url);
        await using (var responseStream = await httpClient.GetStreamAsync(downloadUrl))
        await using (var outputStream = File.Create(cachePath))
        {
            await responseStream.CopyToAsync(outputStream);
        }

        if (!await VerifyFileAsync(cachePath, file))
        {
            File.Delete(cachePath);
            throw new InvalidOperationException($"Downloaded file hash mismatch: {file.Path}");
        }

        return cachePath;
    }

    private Uri ResolveDownloadUrl(string url)
    {
        if (Uri.TryCreate(url, UriKind.Absolute, out var absolute))
        {
            return absolute;
        }

        var manifestUri = new Uri(config.ManifestUrl, UriKind.Absolute);
        return new Uri(manifestUri, url);
    }

    private async Task<bool> VerifyFileAsync(string filePath, UpdateFile file)
    {
        var info = new FileInfo(filePath);
        if (info.Length != file.Size)
        {
            return false;
        }

        await using var stream = File.OpenRead(filePath);
        var hash = await SHA256.HashDataAsync(stream);
        var actual = Convert.ToHexString(hash);
        return string.Equals(actual, file.Sha256, StringComparison.OrdinalIgnoreCase);
    }

    private void ValidateUpdateFile(UpdateFile file)
    {
        if (file == null)
        {
            throw new InvalidOperationException("Update file entry is empty.");
        }

        if (string.IsNullOrWhiteSpace(file.Path)
            || string.IsNullOrWhiteSpace(file.Url)
            || string.IsNullOrWhiteSpace(file.Sha256)
            || file.Size < 0)
        {
            throw new InvalidOperationException("Update file entry is invalid.");
        }

        _ = ResolveClientPath(file.Path);
        _ = ResolveCachePath("check", file.Path);
    }

    private string ResolveClientPath(string relativePath)
    {
        if (IsForbiddenPath(relativePath))
        {
            throw new InvalidOperationException($"Forbidden update path: {relativePath}");
        }

        var fullPath = Path.GetFullPath(Path.Combine(clientRoot, relativePath));
        if (!fullPath.StartsWith(clientRoot, StringComparison.OrdinalIgnoreCase))
        {
            throw new InvalidOperationException($"Unsafe update path: {relativePath}");
        }

        return fullPath;
    }

    private string ResolveCachePath(string version, string relativePath)
    {
        var cacheRoot = NormalizeRootPath(Path.Combine(clientRoot, Program.CacheDirectoryName, version));
        var fullPath = Path.GetFullPath(Path.Combine(cacheRoot, relativePath));
        if (!fullPath.StartsWith(cacheRoot, StringComparison.OrdinalIgnoreCase))
        {
            throw new InvalidOperationException($"Unsafe cache path: {relativePath}");
        }

        return fullPath;
    }

    private static bool IsForbiddenPath(string relativePath)
    {
        var normalized = relativePath.Replace('\\', '/');
        if (string.IsNullOrWhiteSpace(normalized)
            || normalized.StartsWith('/')
            || normalized.Contains("../", StringComparison.Ordinal)
            || normalized.Equals("..", StringComparison.Ordinal))
        {
            return true;
        }

        var lower = normalized.ToLowerInvariant();
        return lower.Equals("config.ini", StringComparison.Ordinal)
               || lower.EndsWith(".log", StringComparison.Ordinal)
               || lower.EndsWith(".dmp", StringComparison.Ordinal)
               || lower.EndsWith(".dump", StringComparison.Ordinal)
               || lower.StartsWith("backup/", StringComparison.Ordinal)
               || lower.Contains("/backup/", StringComparison.Ordinal)
               || lower.Contains(".wzpatch-backup", StringComparison.Ordinal);
    }

    private static void ValidateManifest(UpdateManifest manifest)
    {
        var versions = manifest.Versions;
        var seen = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        foreach (var version in versions)
        {
            if (string.IsNullOrWhiteSpace(version.Version))
            {
                throw new InvalidOperationException("Update manifest includes an empty version.");
            }
            if (!seen.Add(version.Version))
            {
                throw new InvalidOperationException($"Update manifest includes duplicate version: {version.Version}");
            }
            version.Files ??= [];
        }

        if (versions.Count > 0
            && !versions.Any(v => string.Equals(v.Version, manifest.LatestVersion, StringComparison.OrdinalIgnoreCase)))
        {
            throw new InvalidOperationException($"Update manifest latestVersion is not listed: {manifest.LatestVersion}");
        }
    }

    private static string NormalizeRootPath(string path)
    {
        var fullPath = Path.GetFullPath(path);
        if (!fullPath.EndsWith(Path.DirectorySeparatorChar)
            && !fullPath.EndsWith(Path.AltDirectorySeparatorChar))
        {
            fullPath += Path.DirectorySeparatorChar;
        }
        return fullPath;
    }

    private static string DisplayVersion(string version)
    {
        return string.IsNullOrWhiteSpace(version) ? "未安装版本" : version;
    }

    private static void BackupExistingFile(string targetPath, string backupRoot, string relativePath)
    {
        if (!File.Exists(targetPath))
        {
            return;
        }

        var backupPath = Path.Combine(backupRoot, relativePath);
        Directory.CreateDirectory(Path.GetDirectoryName(backupPath)!);
        File.Copy(targetPath, backupPath, true);
    }

    private void WriteCurrentVersion(string version)
    {
        File.WriteAllText(Path.Combine(clientRoot, Program.VersionFileName), version);
    }

    private void StartGame()
    {
        var gamePath = Path.Combine(clientRoot, config.GameExe);
        if (!File.Exists(gamePath))
        {
            throw new InvalidOperationException($"Game executable not found: {config.GameExe}");
        }

        Process.Start(new ProcessStartInfo
        {
            FileName = gamePath,
            WorkingDirectory = clientRoot,
            UseShellExecute = true
        });
    }
}
