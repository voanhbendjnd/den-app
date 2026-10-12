package com.denhub.web.rest;

import com.denhub.service.FileService;
import com.denhub.service.dto.FileUploadResponseDTO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/files")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor
@Slf4j
@Tag(name = "File", description = "API quản lý Tệp tin và Ảnh")
public class FileResource {

    FileService fileService;

    @PostMapping("/upload")
    @Operation(summary = "Tải lên một file ảnh", description = "Nhận file qua multipart/form-data và trả về URL tĩnh của ảnh. Bắt buộc đăng nhập.", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<FileUploadResponseDTO> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folder", defaultValue = "servers") String folder
    ) {
        FileUploadResponseDTO response = fileService.uploadImage(file, folder);

        String baseUrl = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        response.setUrl(baseUrl + response.getUrl());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{folder}/{filename:.+}")
    @Operation(summary = "Lấy file tĩnh", description = "Phục vụ file tĩnh (ảnh) cho trình duyệt. Endpoint này công khai, không cần JWT.")
    public ResponseEntity<Resource> getFile(
            @PathVariable String folder,
            @PathVariable String filename,
            HttpServletRequest request
    ) {
        Resource resource = fileService.loadFileAsResource(folder, filename);

        String contentType = null;
        try {
            contentType = request.getServletContext().getMimeType(resource.getFile().getAbsolutePath());
        } catch (IOException ex) {
            log.warn("Could not determine file type for: {}", filename);
        }

        if (contentType == null) {
            contentType = "application/octet-stream";
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                .body(resource);
    }
}
