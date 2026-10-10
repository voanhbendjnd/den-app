package com.denhub.service;

import com.denhub.service.errors.BadRequestResourceException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@FieldDefaults(level = AccessLevel.PRIVATE)
@RequiredArgsConstructor
public class FileService {
    @Value("${denhub.upload-file.base-uri:uploads/}")
    private String absolutePath;

    public List<String> saveAndGetUrls(List<MultipartFile> files) throws IOException {
        if(files == null || files.isEmpty()) {
            throw new BadRequestResourceException("File not found", "fileManagement", "filenotfound");
        }
        List<String> errorMessage = new ArrayList<>();
        for(MultipartFile file : files) {
            if(file == null || file.isEmpty()) {
                throw new BadRequestResourceException("File null or email", "fileManagement", "cannotdefine");
            }
            if(file.getOriginalFilename() == null || file.getOriginalFilename().isEmpty()) {
                errorMessage.add("File has invalid original file name");
            }
        }
        if(!errorMessage.isEmpty()) {
            throw new BadRequestResourceException(String.join("/n", errorMessage), "fileManagement", "filenameinvalid");
        }
        var directoryPath = Paths.get(absolutePath);
        Files.createDirectories(directoryPath);
        List<String> urlFiles = new ArrayList<>();
        for(MultipartFile file : files) {
            String fileName = "denhub-" + System.currentTimeMillis() + "-" + UUID.randomUUID() + ".webp";
            var filePath = directoryPath.resolve(fileName);
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, filePath, StandardCopyOption.REPLACE_EXISTING);
            }
            urlFiles.add(fileName);
        }
        return urlFiles;
    }

}
