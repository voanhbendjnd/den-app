package com.denhub.web.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.denhub.service.ServerService;
import com.denhub.service.dto.CreateServerDTO;
import com.denhub.service.dto.ServerMemberResponseDTO;
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
    void getServerDetail_UsesRequestedId() throws Exception {
        ServerResponseDTO dto = new ServerResponseDTO();
        dto.setId(12L);
        dto.setName("Detail server");
        dto.setOwnerId(100L);
        when(serverService.getServerDetail(12L)).thenReturn(dto);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/servers/12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(12))
                .andExpect(jsonPath("$.name").value("Detail server"))
                .andExpect(jsonPath("$.ownerId").value(100));
        org.mockito.Mockito.verify(serverService).getServerDetail(12L);
    }

    @Test
    void getServerMembers_Success() throws Exception {
        ServerMemberResponseDTO memberDto = new ServerMemberResponseDTO();
        memberDto.setId(101L);
        memberDto.setServerId(12L);
        memberDto.setUserId(1L);
        memberDto.setUsername("nam123");
        memberDto.setName("Hoang Nam");
        memberDto.setNickname("Nam Boss");
        memberDto.setAvatar("https://example.test/avatar.png");
        memberDto.setRole("OWNER");

        when(serverService.getServerMembers(12L)).thenReturn(java.util.List.of(memberDto));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/servers/12/members"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(101))
                .andExpect(jsonPath("$[0].serverId").value(12))
                .andExpect(jsonPath("$[0].userId").value(1))
                .andExpect(jsonPath("$[0].username").value("nam123"))
                .andExpect(jsonPath("$[0].name").value("Hoang Nam"))
                .andExpect(jsonPath("$[0].nickname").value("Nam Boss"))
                .andExpect(jsonPath("$[0].avatar").value("https://example.test/avatar.png"))
                .andExpect(jsonPath("$[0].role").value("OWNER"));
        org.mockito.Mockito.verify(serverService).getServerMembers(12L);
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

    @Test
    void createServerInvite_Success() throws Exception {
        com.denhub.service.dto.ServerInviteResponseDTO inviteDto = new com.denhub.service.dto.ServerInviteResponseDTO();
        inviteDto.setId(1L);
        inviteDto.setServerId(12L);
        inviteDto.setCode("aB7kX9mQ");
        inviteDto.setCreatedBy(5L);
        inviteDto.setStatus("ACTIVE");
        inviteDto.setCreatedAt(java.time.Instant.parse("2026-10-14T14:30:00Z"));

        when(serverService.createServerInvite(org.mockito.ArgumentMatchers.eq(12L), any())).thenReturn(inviteDto);

        mockMvc.perform(post("/api/v1/servers/12/invites")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.serverId").value(12))
                .andExpect(jsonPath("$.code").value("aB7kX9mQ"))
                .andExpect(jsonPath("$.createdBy").value(5))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        org.mockito.Mockito.verify(serverService).createServerInvite(org.mockito.ArgumentMatchers.eq(12L), any());
    }

    @Test
    void joinServerByInviteCode_Success() throws Exception {
        ServerResponseDTO dto = new ServerResponseDTO();
        dto.setId(25L);
        dto.setName("Joined Server");

        when(serverService.joinServerByInviteCode("abc12345")).thenReturn(dto);

        mockMvc.perform(post("/api/v1/servers/join-invite/abc12345"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(25))
                .andExpect(jsonPath("$.name").value("Joined Server"));

        org.mockito.Mockito.verify(serverService).joinServerByInviteCode("abc12345");
    }
}
