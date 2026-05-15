package tn.esprit.msprofile.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tn.esprit.msprofile.config.properties.FileStorageProperties;
import tn.esprit.msprofile.exception.FileSizeLimitExceededException;
import tn.esprit.msprofile.exception.UnsupportedFileFormatException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;

@Service
public class FileStorageService {

    private final FileStorageProperties properties;

    public FileStorageService(FileStorageProperties properties) {
        this.properties = properties;
    }

    public String store(MultipartFile file, UUID userId) {
        String ext = extractAndValidateExtension(file.getOriginalFilename());
        validateSize(file);

        String relativePath = userId + "/" + UUID.randomUUID() + "." + ext;
        Path fullPath = resolveFullPath(relativePath);

        try {
            Path parent = fullPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.copy(file.getInputStream(), fullPath, StandardCopyOption.REPLACE_EXISTING);
            return relativePath.replace('\\', '/');
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store uploaded file", e);
        }
    }

    public void delete(String filePath) {
        Path fullPath = resolveFullPath(filePath);
        try {
            Files.deleteIfExists(fullPath);
        } catch (IOException ignored) {
            // Storage cleanup should not fail the request flow in MVP mode.
        }
    }

    public Path resolveFullPath(String filePath) {
        return Paths.get(properties.getBasePath()).toAbsolutePath().normalize().resolve(filePath).normalize();
    }

    private String extractAndValidateExtension(String fileName) {
        if (fileName == null || fileName.isBlank() || !fileName.contains(".")) {
            throw new UnsupportedFileFormatException("Only PDF and DOCX files are supported");
        }
        String ext = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        if (!"pdf".equals(ext) && !"docx".equals(ext)) {
            throw new UnsupportedFileFormatException("Only PDF and DOCX files are supported");
        }
        return ext;
    }

    private void validateSize(MultipartFile file) {
        long maxBytes = (long) properties.getMaxFileSizeMb() * 1024L * 1024L;
        if (file.getSize() > maxBytes) {
            throw new FileSizeLimitExceededException("File exceeds maximum size of 5MB");
        }
    }
}
