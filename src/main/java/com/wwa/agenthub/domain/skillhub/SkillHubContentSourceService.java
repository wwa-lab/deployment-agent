package com.wwa.agenthub.domain.skillhub;

import com.wwa.agenthub.errors.ValidationAppException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

@Service
public class SkillHubContentSourceService {

    private static final String SOURCE_TYPE_FILE_PATH = "FILE_PATH";

    private final Path repositoryRoot;
    private final Path storageDirectory;

    public SkillHubContentSourceService(
            @Value("${app.skillhub.storage-directory:${user.dir}/skills}") String storageDirectory
    ) {
        this.repositoryRoot = Path.of("").toAbsolutePath().normalize();
        this.storageDirectory = Path.of(storageDirectory).toAbsolutePath().normalize();
    }

    public SkillContentSnapshot readMarkdownSnapshot(String sourcePath) {
        String normalizedPath = normalizeSourcePath(sourcePath);
        Path resolved = repositoryRoot.resolve(normalizedPath).normalize();
        if (!resolved.startsWith(repositoryRoot)) {
            throw new ValidationAppException("Skill source path must stay inside the repository.");
        }
        if (!Files.isRegularFile(resolved)) {
            throw new ValidationAppException("Skill source file does not exist: " + normalizedPath);
        }
        try {
            String content = Files.readString(resolved, StandardCharsets.UTF_8);
            return new SkillContentSnapshot(SOURCE_TYPE_FILE_PATH, normalizedPath, content, sha256(content));
        } catch (IOException ex) {
            throw new ValidationAppException("Unable to read skill source file: " + normalizedPath);
        }
    }

    public String validateSourcePath(String sourcePath) {
        return readMarkdownSnapshot(sourcePath).sourcePath();
    }

    public SkillContentSnapshot createSkillFile(
            String skillId,
            String name,
            String description,
            String category,
            List<String> tags,
            String owner,
            String status,
            String version,
            String versionNotes,
            String content,
            String createdBy
    ) {
        Path file = storageDirectory.resolve(safeSlug(name) + "-" + skillId + ".md").normalize();
        if (!file.startsWith(storageDirectory)) {
            throw new ValidationAppException("Generated skill file path is invalid.");
        }
        String body = renderSkillFile(
                name,
                description,
                category,
                tags,
                owner,
                status,
                version,
                versionNotes,
                requireContent(content),
                createdBy,
                Instant.now()
        );
        writeFile(file, body);
        return snapshotForFile(file, requireContent(content));
    }

    public SkillContentSnapshot appendVersion(
            String sourcePath,
            String version,
            String versionNotes,
            String content,
            String createdBy
    ) {
        String normalizedPath = normalizeSourcePath(sourcePath);
        Path file = repositoryRoot.resolve(normalizedPath).normalize();
        if (!file.startsWith(repositoryRoot) || !Files.isRegularFile(file)) {
            throw new ValidationAppException("Skill file does not exist: " + normalizedPath);
        }
        String existing;
        try {
            existing = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new ValidationAppException("Unable to read skill file: " + normalizedPath);
        }
        String newVersionBlock = renderVersionBlock(version, versionNotes, requireContent(content), createdBy, Instant.now());
        String updated = existing + System.lineSeparator() + newVersionBlock;
        writeFile(file, updated);
        return snapshotForFile(file, requireContent(content));
    }

    public String readSkillFile(String sourcePath) {
        String normalizedPath = normalizeSourcePath(sourcePath);
        Path file = repositoryRoot.resolve(normalizedPath).normalize();
        if (!file.startsWith(repositoryRoot) || !Files.isRegularFile(file)) {
            throw new ValidationAppException("Skill file does not exist: " + normalizedPath);
        }
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new ValidationAppException("Unable to read skill file: " + normalizedPath);
        }
    }

    public void restoreSkillFile(String sourcePath, String content) {
        String normalizedPath = normalizeSourcePath(sourcePath);
        Path file = repositoryRoot.resolve(normalizedPath).normalize();
        if (!file.startsWith(repositoryRoot)) {
            throw new ValidationAppException("Skill source path must stay inside the repository.");
        }
        writeFile(file, content);
    }

    public void deleteSkillFileIfExists(String sourcePath) {
        String normalizedPath = normalizeSourcePath(sourcePath);
        Path file = repositoryRoot.resolve(normalizedPath).normalize();
        if (!file.startsWith(repositoryRoot)) {
            throw new ValidationAppException("Skill source path must stay inside the repository.");
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException ex) {
            throw new ValidationAppException("Unable to delete generated skill file: " + normalizedPath);
        }
    }

    private String normalizeSourcePath(String sourcePath) {
        if (sourcePath == null || sourcePath.isBlank()) {
            throw new ValidationAppException("Skill source path is required.");
        }
        String trimmed = sourcePath.trim().replace('\\', '/');
        if (trimmed.matches("^[a-zA-Z][a-zA-Z0-9+.-]*:.*") || trimmed.startsWith("//")) {
            throw new ValidationAppException("Skill source path must be repository-relative.");
        }
        try {
            Path parsed = Path.of(trimmed);
            if (parsed.isAbsolute()) {
                throw new ValidationAppException("Skill source path must be repository-relative.");
            }
            for (Path segment : parsed) {
                if ("..".equals(segment.toString())) {
                    throw new ValidationAppException("Skill source path cannot contain '..'.");
                }
            }
            String fileName = parsed.getFileName() == null ? "" : parsed.getFileName().toString();
            if (!fileName.equals("SKILL.md") && !fileName.toLowerCase().endsWith(".md")) {
                throw new ValidationAppException("Skill source path must point to SKILL.md or another Markdown file.");
            }
            return parsed.normalize().toString().replace('\\', '/');
        } catch (InvalidPathException ex) {
            throw new ValidationAppException("Skill source path is invalid.");
        }
    }

    private String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 digest is unavailable", ex);
        }
    }

    private SkillContentSnapshot snapshotForFile(Path file, String contentSnapshot) {
        Path normalizedFile = file.toAbsolutePath().normalize();
        String relative = repositoryRoot.relativize(normalizedFile).toString().replace('\\', '/');
        return new SkillContentSnapshot(SOURCE_TYPE_FILE_PATH, relative, contentSnapshot, sha256(contentSnapshot));
    }

    private String safeSlug(String value) {
        String slug = value == null ? "skill" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return slug.isBlank() ? "skill" : slug;
    }

    private String renderSkillFile(
            String name,
            String description,
            String category,
            List<String> tags,
            String owner,
            String status,
            String version,
            String versionNotes,
            String content,
            String createdBy,
            Instant createdAt
    ) {
        return """
                # %s

                ## Metadata

                - Category: %s
                - Owner: %s
                - Status: %s
                - Tags: %s

                ## Description

                %s

                ## Version History

                %s
                """.formatted(
                name,
                category,
                owner,
                status,
                String.join(", ", tags == null ? List.of() : tags),
                description,
                renderVersionBlock(version, versionNotes, content, createdBy, createdAt)
        );
    }

    private String renderVersionBlock(String version, String versionNotes, String content, String createdBy, Instant createdAt) {
        return """
                <!-- skill-hub-version version="%s" createdBy="%s" createdAt="%s" sha256="%s" -->
                ### Version %s

                **Created by:** %s
                **Created at:** %s

                **Notes:** %s

                ```markdown
                %s
                ```
                """.formatted(
                version,
                createdBy,
                createdAt,
                sha256(content),
                version,
                createdBy,
                createdAt,
                normalizeBlank(versionNotes) == null ? "No version notes recorded." : versionNotes,
                content
        );
    }

    private String requireContent(String content) {
        String normalized = normalizeBlank(content);
        if (normalized == null) {
            throw new ValidationAppException("Skill content is required.");
        }
        return normalized;
    }

    private String normalizeBlank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void writeFile(Path file, String content) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new ValidationAppException("Unable to write skill file: " + file.getFileName());
        }
    }

    public record SkillContentSnapshot(
            String contentSourceType,
            String sourcePath,
            String contentSnapshot,
            String contentSha256
    ) {}
}
