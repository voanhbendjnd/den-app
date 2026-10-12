package com.denhub.service;

import com.denhub.service.dto.FileUploadResponseDTO;
import com.denhub.web.rest.errors.BadRequestAlertException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FileServiceTest {

    private FileService fileService;
    private final String testFolder = "test_folder";

    @BeforeEach
    void setUp() {
        fileService = new FileService();
    }

    @AfterEach
    void tearDown() throws IOException {
        Path testPath = Paths.get("uploads", testFolder);
        if (Files.exists(testPath)) {
            Files.walk(testPath)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
    }

    @Test
    void testUploadImage_Success() {
        byte[] content = "fake-png-content".getBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file", "avatar.png", "image/png", content
        );

        FileUploadResponseDTO response = fileService.uploadImage(file, testFolder);

        assertThat(response).isNotNull();
        assertThat(response.getFileName()).endsWith(".png");
        assertThat(response.getUrl()).contains("/api/v1/files/" + testFolder + "/");
        assertThat(response.getSize()).isEqualTo(content.length);

        // Verify resource can be loaded
        Resource resource = fileService.loadFileAsResource(testFolder, response.getFileName());
        assertThat(resource.exists()).isTrue();
        assertThat(resource.isReadable()).isTrue();
    }

    @Test
    void testUploadImage_EmptyFile_ThrowsException() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "empty.png", "image/png", new byte[0]
        );

        assertThrows(BadRequestAlertException.class, () -> {
            fileService.uploadImage(file, testFolder);
        });
    }

    @Test
    void testUploadImage_InvalidExtension_ThrowsException() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "script.sh", "text/plain", "echo hello".getBytes()
        );

        assertThrows(BadRequestAlertException.class, () -> {
            fileService.uploadImage(file, testFolder);
        });
    }
}
