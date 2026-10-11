package com.denhub.web.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.denhub.service.FileService;
import com.denhub.service.dto.FileUploadResponseDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class FileResourceTest {

    private MockMvc mockMvc;

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
}
