package com.denhub.web.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.denhub.service.ServerService;
import com.denhub.service.dto.CreateServerDTO;
import com.denhub.service.dto.ServerResponseDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class ServerResourceTest {

    private MockMvc mockMvc;

    @Mock
    private ServerService serverService;

    @InjectMocks
    private ServerResource serverResource;

    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(serverResource).build();
    }

    @Test
    void createServer_Success() throws Exception {
        CreateServerDTO dto = new CreateServerDTO();
        dto.setName("Test Server");
        dto.setIconUrl("http://example.com/icon.png");

        ServerResponseDTO responseDto = new ServerResponseDTO();
        responseDto.setId(1L);
        responseDto.setName("Test Server");
        responseDto.setOwnerId(100L);

        when(serverService.createServer(any(CreateServerDTO.class))).thenReturn(responseDto);

        mockMvc.perform(post("/api/v1/servers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Test Server"))
                .andExpect(jsonPath("$.ownerId").value(100));
    }

    @Test
    void createServer_BadRequest_WhenNameEmpty() throws Exception {
        CreateServerDTO dto = new CreateServerDTO();
        dto.setName(""); // Invalid name

        mockMvc.perform(post("/api/v1/servers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getUserServers_Success() throws Exception {
        ServerResponseDTO responseDto = new ServerResponseDTO();
        responseDto.setId(1L);
        responseDto.setName("My Server");
        responseDto.setOwnerId(100L);

        when(serverService.getUserServers()).thenReturn(java.util.List.of(responseDto));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/servers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("My Server"));
    }

    @Test
    void joinServer_Success() throws Exception {
        mockMvc.perform(post("/api/v1/servers/10/join")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());
    }

    @Test
    void joinServer_Fail_ServerNotFound() throws Exception {
        org.mockito.Mockito.doThrow(new com.denhub.web.rest.errors.BadRequestAlertException("Server not found", "server", "servernotfound"))
                .when(serverService).joinServer(10L);

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
            mockMvc.perform(post("/api/v1/servers/10/join")
                    .contentType(MediaType.APPLICATION_JSON))
        ).hasCauseInstanceOf(com.denhub.web.rest.errors.BadRequestAlertException.class);
    }

    @Test
    void joinServer_Fail_AlreadyMember() throws Exception {
        org.mockito.Mockito.doThrow(new com.denhub.web.rest.errors.BadRequestAlertException("Already member", "server", "alreadymember"))
                .when(serverService).joinServer(10L);

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
            mockMvc.perform(post("/api/v1/servers/10/join")
                    .contentType(MediaType.APPLICATION_JSON))
        ).hasCauseInstanceOf(com.denhub.web.rest.errors.BadRequestAlertException.class);
    }
}
