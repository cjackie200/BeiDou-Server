using System.IO.Compression;
using System.Reflection;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;

ApplicationConfiguration.Initialize();
Application.Run(new PatchForm());

internal sealed class PatchForm : Form
{
    private readonly TextBox targetBox = new();
    private readonly TextBox logBox = new();
    private readonly Button installButton = new();
    private PatchMetadata metadata = PatchMetadata.Default;

    public PatchForm()
    {
        LoadMetadataPreview();

        Text = metadata.Title;
        Width = 760;
        Height = 520;
        StartPosition = FormStartPosition.CenterScreen;
        FormBorderStyle = FormBorderStyle.FixedDialog;
        MaximizeBox = false;

        Controls.Add(new Label
        {
            Text = metadata.Title,
            Left = 16,
            Top = 16,
            Width = 700,
            Height = 28,
            Font = new Font(Font.FontFamily, 13, FontStyle.Bold),
        });

        Controls.Add(new Label
        {
            Text = "Select the server directory that contains BeiDou.jar. Files are backed up before overwrite.",
            Left = 16,
            Top = 54,
            Width = 710,
            Height = 24,
        });

        targetBox.Left = 16;
        targetBox.Top = 88;
        targetBox.Width = 590;
        targetBox.Text = DetectDefaultTarget();
        Controls.Add(targetBox);

        var browseButton = new Button { Text = "Browse", Left = 620, Top = 86, Width = 100 };
        browseButton.Click += (_, _) => BrowseTarget();
        Controls.Add(browseButton);

        installButton.Text = "Install";
        installButton.Left = 620;
        installButton.Top = 124;
        installButton.Width = 100;
        installButton.Click += (_, _) => InstallPatch();
        Controls.Add(installButton);

        logBox.Left = 16;
        logBox.Top = 124;
        logBox.Width = 590;
        logBox.Height = 330;
        logBox.Multiline = true;
        logBox.ScrollBars = ScrollBars.Vertical;
        logBox.ReadOnly = true;
        Controls.Add(logBox);

        Log("Patch version: " + metadata.Version);
    }

    private void BrowseTarget()
    {
        using var dialog = new OpenFileDialog
        {
            Title = "Select BeiDou.jar in the server directory",
            Filter = "BeiDou.jar|BeiDou.jar|Jar files (*.jar)|*.jar|All files (*.*)|*.*",
            CheckFileExists = true,
            Multiselect = false,
        };

        var current = targetBox.Text.Trim();
        if (Directory.Exists(current))
        {
            dialog.InitialDirectory = current;
        }

        if (dialog.ShowDialog(this) == DialogResult.OK)
        {
            targetBox.Text = Path.GetDirectoryName(dialog.FileName) ?? "";
        }
    }

    private static string DetectDefaultTarget()
    {
        var current = AppContext.BaseDirectory;
        return File.Exists(Path.Combine(current, "BeiDou.jar")) ? current : "";
    }

    private void InstallPatch()
    {
        var target = targetBox.Text.Trim();
        if (string.IsNullOrWhiteSpace(target) || !Directory.Exists(target))
        {
            MessageBox.Show(this, "Select a server directory first.", "BeiDou Server Patch", MessageBoxButtons.OK, MessageBoxIcon.Warning);
            return;
        }

        if (!File.Exists(Path.Combine(target, "BeiDou.jar")))
        {
            MessageBox.Show(this, "BeiDou.jar was not found in the selected directory.", "Invalid directory", MessageBoxButtons.OK, MessageBoxIcon.Warning);
            return;
        }

        installButton.Enabled = false;
        try
        {
            InstallPatchCore(target);
        }
        catch (Exception ex)
        {
            Log("Install failed: " + ex);
            TryWriteLog(target, metadata.FailedLogName);
            MessageBox.Show(this, ex.Message, "Install failed", MessageBoxButtons.OK, MessageBoxIcon.Error);
        }
        finally
        {
            installButton.Enabled = true;
        }
    }

    private void InstallPatchCore(string target)
    {
        var tempRoot = Path.Combine(Path.GetTempPath(), "beidou-server-patch-" + Guid.NewGuid().ToString("N"));
        Directory.CreateDirectory(tempRoot);

        var zipPath = Path.Combine(tempRoot, "patch-data.zip");
        ExtractEmbeddedPayload(zipPath);

        var payloadDir = Path.Combine(tempRoot, "payload");
        ZipFile.ExtractToDirectory(zipPath, payloadDir);

        metadata = ReadJson<PatchMetadata>(Path.Combine(payloadDir, "patch-metadata.json")) ?? PatchMetadata.Default;
        var copyManifest = ReadJson<List<PatchFile>>(Path.Combine(payloadDir, "copy-manifest.json")) ?? new();
        var deleteManifest = ReadJson<List<string>>(Path.Combine(payloadDir, "delete-manifest.json")) ?? new();

        var backupDir = Path.Combine(target, "backup", "patch-" + metadata.Version + "-" + DateTime.Now.ToString("yyyyMMdd-HHmmss"));
        Directory.CreateDirectory(backupDir);
        Log("Backup directory: " + backupDir);

        CheckWritable(target);
        BackupDeletes(target, backupDir, deleteManifest);
        BackupCopies(target, backupDir, copyManifest);
        ApplyDeletes(target, deleteManifest);
        ApplyCopies(payloadDir, target, copyManifest);
        VerifyCopies(target, copyManifest);

        TryWriteLog(target, metadata.LogName);
        Log("Install complete.");
        MessageBox.Show(this, "Install complete. Restart the BeiDou server.", "Complete", MessageBoxButtons.OK, MessageBoxIcon.Information);
    }

    private void LoadMetadataPreview()
    {
        try
        {
            var tempRoot = Path.Combine(Path.GetTempPath(), "beidou-server-patch-preview-" + Guid.NewGuid().ToString("N"));
            Directory.CreateDirectory(tempRoot);
            var zipPath = Path.Combine(tempRoot, "patch-data.zip");
            ExtractEmbeddedPayload(zipPath);
            ZipFile.ExtractToDirectory(zipPath, tempRoot);
            metadata = ReadJson<PatchMetadata>(Path.Combine(tempRoot, "patch-metadata.json")) ?? PatchMetadata.Default;
        }
        catch
        {
            metadata = PatchMetadata.Default;
        }
    }

    private static void ExtractEmbeddedPayload(string zipPath)
    {
        using var stream = Assembly.GetExecutingAssembly().GetManifestResourceStream("patch-data.zip")
            ?? throw new InvalidOperationException("The installer is missing embedded patch-data.zip.");
        using var output = File.Create(zipPath);
        stream.CopyTo(output);
    }

    private static T? ReadJson<T>(string path)
    {
        return File.Exists(path)
            ? JsonSerializer.Deserialize<T>(File.ReadAllText(path), new JsonSerializerOptions
            {
                PropertyNameCaseInsensitive = true,
            })
            : default;
    }

    private static void CheckWritable(string target)
    {
        var jarPath = Path.Combine(target, "BeiDou.jar");
        try
        {
            using var _ = File.Open(jarPath, FileMode.Open, FileAccess.ReadWrite, FileShare.None);
        }
        catch (IOException)
        {
            throw new InvalidOperationException("BeiDou.jar is in use. Stop the server before installing this patch.");
        }
    }

    private void BackupDeletes(string target, string backupDir, List<string> deleteManifest)
    {
        foreach (var relative in deleteManifest)
        {
            BackupIfExists(target, backupDir, relative);
        }
    }

    private void BackupCopies(string target, string backupDir, List<PatchFile> copyManifest)
    {
        foreach (var file in copyManifest)
        {
            BackupIfExists(target, backupDir, file.Path);
        }
    }

    private void BackupIfExists(string target, string backupDir, string relative)
    {
        var path = ResolveDestination(target, relative, metadata.StaticRoot);
        if (!File.Exists(path))
        {
            return;
        }

        var backupRelative = IsStaticUpdateFile(relative)
            ? Path.Combine("iis-wwwroot", Normalize(relative))
            : Normalize(relative);
        var backupPath = Path.Combine(backupDir, backupRelative);
        Directory.CreateDirectory(Path.GetDirectoryName(backupPath)!);
        File.Copy(path, backupPath, overwrite: true);
        Log("Backed up " + relative);
    }

    private void ApplyDeletes(string target, List<string> deleteManifest)
    {
        foreach (var relative in deleteManifest)
        {
            var path = ResolveDestination(target, relative, metadata.StaticRoot);
            if (File.Exists(path))
            {
                File.Delete(path);
                Log("Deleted " + relative);
            }
        }
    }

    private void ApplyCopies(string payloadDir, string target, List<PatchFile> copyManifest)
    {
        foreach (var file in copyManifest)
        {
            if (file.Path is "copy-manifest.json" or "delete-manifest.json" or "patch-metadata.json")
            {
                continue;
            }

            var source = Path.Combine(payloadDir, Normalize(file.Path));
            var destination = ResolveDestination(target, file.Path, metadata.StaticRoot);
            Directory.CreateDirectory(Path.GetDirectoryName(destination)!);
            File.Copy(source, destination, overwrite: true);
            Log("Copied " + file.Path);
        }
    }

    private void VerifyCopies(string target, List<PatchFile> copyManifest)
    {
        foreach (var file in copyManifest)
        {
            if (file.Path is "copy-manifest.json" or "delete-manifest.json" or "patch-metadata.json")
            {
                continue;
            }

            var destination = ResolveDestination(target, file.Path, metadata.StaticRoot);
            var hash = Convert.ToHexString(SHA256.HashData(File.ReadAllBytes(destination)));
            if (!string.Equals(hash, file.Sha256, StringComparison.OrdinalIgnoreCase))
            {
                throw new InvalidOperationException("Hash verification failed: " + file.Path);
            }
        }

        Log("Hash verification passed.");
    }

    private static string ResolveDestination(string target, string relative, string staticRoot)
    {
        if (IsStaticUpdateFile(relative))
        {
            if (!Directory.Exists(staticRoot))
            {
                throw new InvalidOperationException("Static update root was not found: " + staticRoot);
            }

            return Path.Combine(staticRoot, Normalize(relative));
        }

        return Path.Combine(target, Normalize(relative));
    }

    private static bool IsStaticUpdateFile(string relative) =>
        relative.StartsWith("client-update/", StringComparison.OrdinalIgnoreCase);

    private static string Normalize(string relative) => relative.Replace('/', Path.DirectorySeparatorChar);

    private void TryWriteLog(string target, string logName)
    {
        try
        {
            File.WriteAllText(Path.Combine(target, logName), logBox.Text, Encoding.UTF8);
        }
        catch
        {
        }
    }

    private void Log(string message)
    {
        logBox.AppendText("[" + DateTime.Now.ToString("HH:mm:ss") + "] " + message + Environment.NewLine);
    }

    private sealed record PatchFile(string Path, long Size, string Sha256);

    private sealed record PatchMetadata(string Version, string Title, string StaticRoot, string LogName, string FailedLogName)
    {
        public static PatchMetadata Default { get; } = new(
            "unknown",
            "BeiDou Server Patch",
            @"C:\inetpub\wwwroot",
            "patch-unknown.log",
            "patch-unknown.failed.log");
    }
}
