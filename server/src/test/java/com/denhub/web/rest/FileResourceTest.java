package com.denhub.web.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.denhub.service.FileService;
import com.denhub.service.dto.FileUploadResponseDTO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

@ExtendWith(MockitoExtension.class)
class FileResourceTest {

    private MockMvc mockMvc;

    @TempDir
    Path tempDirectory;

    @Mock
    private FileService fileService;

    @InjectMocks
    private FileResource fileResource;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(fileResource).build();
    }

    @Test
    void uploadFile_Success() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "icon.png", "image/png", "sample data".getBytes()
        );

        FileUploadResponseDTO responseDto = FileUploadResponseDTO.builder()
                .fileName("test-uuid.png")
                .url("/api/v1/files/servers/test-uuid.png")
                .size(11L)
                .contentType("image/png")
                .build();

        when(fileService.uploadImage(any(), eq("servers"))).thenReturn(responseDto);

        mockMvc.perform(multipart("/api/v1/files/upload")
                .file(file)
                .param("folder", "servers"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileName").value("test-uuid.png"))
                .andExpect(jsonPath("$.url").isNotEmpty());
    }

    @Test
    void getFile_Success_WithoutParameterNameMetadata() throws Exception {
        // Reproduce an IDE build that does not retain Java parameter names.
        mockMvc.getDispatcherServlet().getWebApplicationContext()
                .getBean(RequestMappingHandlerAdapter.class)
                .setParameterNameDiscoverer(mock(ParameterNameDiscoverer.class));

        byte[] image = Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aX1cAAAAASUVORK5CYII="
        );
        Path imagePath = tempDirectory.resolve("server.icon.png");
        Files.write(imagePath, image);
        when(fileService.loadFileAsResource("servers", "server.icon.png"))
                .thenReturn(new FileSystemResource(imagePath));

        mockMvc.perform(get("/api/v1/files/servers/server.icon.png"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(image))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "public, max-age=86400"));

        verify(fileService).loadFileAsResource("servers", "server.icon.png");
    }
}
