package tn.esprit.msjob.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;

@Service
public class ResumeStorageService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );

    private final Path storageDir;
    private final String publicBaseUrl;

    public ResumeStorageService(
            @Value("${msjob.resume.storage.path}") String storagePath,
            @Value("${msjob.resume.public-base-url}") String publicBaseUrl
    ) {
        this.storageDir = Paths.get(storagePath).toAbsolutePath().normalize();
        this.publicBaseUrl = publicBaseUrl;
    }

    public String storeResume(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Resume file is required");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Unsupported resume type. Please upload PDF or Word document.");
        }

        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename() == null ? "resume" : file.getOriginalFilename());
        String extension = extractExtension(originalFilename, contentType);
        String storedFileName = UUID.randomUUID() + extension;

        try {
            Files.createDirectories(storageDir);
            Path target = storageDir.resolve(storedFileName).normalize();

            // Prevent path traversal
            if (!target.startsWith(storageDir)) {
                throw new IllegalArgumentException("Invalid file path");
            }

            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to store resume", e);
        }

        String base = publicBaseUrl.endsWith("/") ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1) : publicBaseUrl;
        return base + "/" + storedFileName;
    }

    private static String extractExtension(String originalFilename, String contentType) {
        String ext = "";
        int dot = originalFilename.lastIndexOf('.');
        if (dot >= 0 && dot < originalFilename.length() - 1) {
            ext = originalFilename.substring(dot).toLowerCase();
        }

        if (!ext.isBlank()) {
            return ext;
        }

        return switch (contentType) {
            case "application/pdf" -> ".pdf";
            case "application/msword" -> ".doc";
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> ".docx";
            default -> "";
        };
    }
}

