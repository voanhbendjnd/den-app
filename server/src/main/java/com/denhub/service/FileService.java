package com.denhub.service;

import com.denhub.service.dto.FileUploadResponseDTO;
import com.denhub.web.rest.errors.BadRequestAlertException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
public class FileService {

    private static final String UPLOAD_DIR = "uploads";
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB
    private static final Set<String> ALLOWED_EXTENSIONS = new HashSet<>(
            Arrays.asList("jpg", "jpeg", "png", "webp", "gif")
    );

    public FileUploadResponseDTO uploadImage(MultipartFile file, String folder) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestAlertException("File is empty", "file", "fileempty");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BadRequestAlertException("File size exceeds 5MB limit", "file", "filesizetoolarge");
        }

        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename() != null ? file.getOriginalFilename() : "");
        String extension = getFileExtension(originalFilename).toLowerCase();

        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BadRequestAlertException("Only image files (jpg, jpeg, png, webp, gif) are allowed", "file", "invalidfiletype");
        }

        // Sanitize folder to prevent path traversal
        String sanitizedFolder = (folder == null || folder.isBlank()) ? "servers" : folder.replaceAll("[^a-zA-Z0-9_-]", "");
        
        try {
            Path uploadPath = Paths.get(UPLOAD_DIR, sanitizedFolder).toAbsolutePath().normalize();
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            String uniqueFilename = UUID.randomUUID().toString() + "." + extension;
            Path targetLocation = uploadPath.resolve(uniqueFilename);

            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
            log.info("File uploaded successfully to: {}", targetLocation);

            String accessUrl = "/api/v1/files/" + sanitizedFolder + "/" + uniqueFilename;

            return FileUploadResponseDTO.builder()
                    .fileName(uniqueFilename)
                    .url(accessUrl)
                    .size(file.getSize())
                    .contentType(file.getContentType())
                    .build();

        } catch (IOException ex) {
            log.error("Could not store file", ex);
            throw new BadRequestAlertException("Could not store file: " + ex.getMessage(), "file", "uploadfailed");
        }
    }

    public Resource loadFileAsResource(String folder, String filename) {
        String sanitizedFolder = (folder == null || folder.isBlank()) ? "servers" : folder.replaceAll("[^a-zA-Z0-9_-]", "");
        String sanitizedFilename = Paths.get(filename).getFileName().toString();

        try {
            Path filePath = Paths.get(UPLOAD_DIR, sanitizedFolder).resolve(sanitizedFilename).toAbsolutePath().normalize();
            Resource resource = new UrlResource(filePath.toUri());

            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new BadRequestAlertException("File not found: " + filename, "file", "filenotfound");
            }
        } catch (MalformedURLException ex) {
            log.error("File path invalid", ex);
            throw new BadRequestAlertException("File not found: " + filename, "file", "filenotfound");
        }
    }

    private String getFileExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < filename.length() - 1) {
            return filename.substring(dotIndex + 1);
        }
        return "";
    }
}
