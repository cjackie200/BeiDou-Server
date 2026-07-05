package org.gms.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/client-update")
public class ClientUpdateController {
    private static final Pattern VERSION_PATTERN = Pattern.compile("[A-Za-z0-9._-]+");
    private static final String FILE_PREFIX = "/client-update/files/";
    private final Path updateRoot = Path.of(System.getProperty("user.dir"), "client-update").toAbsolutePath().normalize();

    @GetMapping(value = "/manifest.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Resource> manifest() {
        Path manifestPath = updateRoot.resolve("manifest.json").normalize();
        if (!Files.isRegularFile(manifestPath)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .body(new FileSystemResource(manifestPath));
    }

    @GetMapping("/files/{version}/**")
    public ResponseEntity<Resource> file(@PathVariable String version, HttpServletRequest request) throws IOException {
        if (!VERSION_PATTERN.matcher(version).matches()) {
            return ResponseEntity.badRequest().build();
        }

        String relativePath = extractRelativePath(request, version);
        if (relativePath == null || isForbiddenClientPath(relativePath)) {
            return ResponseEntity.badRequest().build();
        }

        Path versionRoot = updateRoot.resolve("files").resolve(version).normalize();
        Path filePath = versionRoot.resolve(relativePath).normalize();
        if (!filePath.startsWith(versionRoot) || !Files.isRegularFile(filePath)) {
            return ResponseEntity.notFound().build();
        }

        FileSystemResource resource = new FileSystemResource(filePath);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(Files.size(filePath))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filePath.getFileName() + "\"")
                .body(resource);
    }

    private String extractRelativePath(HttpServletRequest request, String version) {
        String requestPath = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && requestPath.startsWith(contextPath)) {
            requestPath = requestPath.substring(contextPath.length());
        }

        String prefix = FILE_PREFIX + version + "/";
        if (!requestPath.startsWith(prefix)) {
            return null;
        }

        String decoded = URLDecoder.decode(requestPath.substring(prefix.length()), StandardCharsets.UTF_8);
        return decoded.replace('\\', '/');
    }

    private boolean isForbiddenClientPath(String relativePath) {
        String normalized = relativePath.replace('\\', '/');
        if (normalized.isBlank() || normalized.startsWith("/") || normalized.contains("../") || normalized.equals("..")) {
            return true;
        }

        String lower = normalized.toLowerCase(Locale.ROOT);
        return lower.equals("config.ini")
                || lower.endsWith(".log")
                || lower.endsWith(".dmp")
                || lower.endsWith(".dump")
                || lower.startsWith("backup/")
                || lower.contains("/backup/")
                || lower.contains(".wzpatch-backup");
    }
}
