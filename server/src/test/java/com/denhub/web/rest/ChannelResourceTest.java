package com.denhub.web.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.denhub.service.ChannelService;
import com.denhub.service.dto.ChannelResponseDTO;
import com.denhub.service.dto.CreateChannelDTO;
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
class ChannelResourceTest {

    private MockMvc mockMvc;

    @Mock
    private ChannelService channelService;

    @InjectMocks
    private ChannelResource channelResource;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(channelResource).build();
    }

    @Test
    void createChannel_Success() throws Exception {
        CreateChannelDTO dto = new CreateChannelDTO();
        dto.setServerId(10L);
        dto.setName("general");

        ChannelResponseDTO responseDto = new ChannelResponseDTO();
        responseDto.setId(100L);
        responseDto.setServerId(10L);
        responseDto.setName("general");
        responseDto.setPosition(0);

        when(channelService.createChannel(any(CreateChannelDTO.class))).thenReturn(responseDto);

        mockMvc.perform(post("/api/v1/channels")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.serverId").value(10))
                .andExpect(jsonPath("$.name").value("general"))
                .andExpect(jsonPath("$.position").value(0));
    }

    @Test
    void createChannel_Fail_WhenNameTooShort() throws Exception {
        CreateChannelDTO dto = new CreateChannelDTO();
        dto.setServerId(10L);
        dto.setName("a"); // length < 2

        mockMvc.perform(post("/api/v1/channels")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createChannel_Fail_WhenServerIdNull() throws Exception {
        CreateChannelDTO dto = new CreateChannelDTO();
        dto.setName("general");
        dto.setServerId(null);

        mockMvc.perform(post("/api/v1/channels")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }
}
