package com.denhub.web.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import java.util.List;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.denhub.service.ChannelService;
import com.denhub.service.dto.ChannelResponseDTO;
import com.denhub.service.dto.CreateChannelDTO;
import com.denhub.service.dto.UpdateChannelDTO;
import com.denhub.web.rest.errors.BadRequestAlertException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

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
        mockMvc = MockMvcBuilders.standaloneSetup(channelResource)
                .setControllerAdvice(new com.denhub.web.rest.errors.ExceptionTranslator())
                .build();
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

    @Test
    void getChannelsByServerId_Success() throws Exception {
        ChannelResponseDTO ch1 = new ChannelResponseDTO();
        ch1.setId(100L);
        ch1.setServerId(10L);
        ch1.setName("general");
        ch1.setPosition(0);

        ChannelResponseDTO ch2 = new ChannelResponseDTO();
        ch2.setId(101L);
        ch2.setServerId(10L);
        ch2.setName("random");
        ch2.setPosition(1);

        when(channelService.getChannelsByServerId(10L)).thenReturn(List.of(ch1, ch2));

        mockMvc.perform(get("/api/v1/servers/10/channels")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(100))
                .andExpect(jsonPath("$[0].name").value("general"))
                .andExpect(jsonPath("$[0].position").value(0))
                .andExpect(jsonPath("$[1].id").value(101))
                .andExpect(jsonPath("$[1].name").value("random"))
                .andExpect(jsonPath("$[1].position").value(1));
    }

    @Test
    void getChannelsByServerId_Success_WithoutParameterNameMetadata() throws Exception {
        // Reproduce an IDE build that does not retain Java parameter names.
        mockMvc.getDispatcherServlet().getWebApplicationContext()
                .getBean(RequestMappingHandlerAdapter.class)
                .setParameterNameDiscoverer(mock(ParameterNameDiscoverer.class));
        when(channelService.getChannelsByServerId(42L)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/servers/42/channels"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        verify(channelService).getChannelsByServerId(42L);
    }

    @Test
    void updateChannel_Success() throws Exception {
        UpdateChannelDTO dto = new UpdateChannelDTO();
        dto.setName("renamed-channel");
        dto.setPosition(3);

        ChannelResponseDTO responseDto = new ChannelResponseDTO();
        responseDto.setId(100L);
        responseDto.setServerId(10L);
        responseDto.setName("renamed-channel");
        responseDto.setPosition(3);

        when(channelService.updateChannel(eq(100L), any(UpdateChannelDTO.class))).thenReturn(responseDto);

        mockMvc.perform(put("/api/v1/channels/100")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.serverId").value(10))
                .andExpect(jsonPath("$.name").value("renamed-channel"))
                .andExpect(jsonPath("$.position").value(3));
    }

    @Test
    void updateChannel_Fail_WhenNameTooShort() throws Exception {
        UpdateChannelDTO dto = new UpdateChannelDTO();
        dto.setName("x"); // length < 2

        mockMvc.perform(put("/api/v1/channels/100")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateChannel_Fail_WhenNameBlank() throws Exception {
        UpdateChannelDTO dto = new UpdateChannelDTO();
        dto.setName("   ");

        mockMvc.perform(put("/api/v1/channels/100")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteChannel_Success() throws Exception {
        mockMvc.perform(delete("/api/v1/channels/100"))
                .andExpect(status().isNoContent());

        verify(channelService).deleteChannel(100L);
    }

    @Test
    void deleteChannel_Success_WithoutParameterNameMetadata() throws Exception {
        mockMvc.getDispatcherServlet().getWebApplicationContext()
                .getBean(RequestMappingHandlerAdapter.class)
                .setParameterNameDiscoverer(mock(ParameterNameDiscoverer.class));

        mockMvc.perform(delete("/api/v1/channels/100"))
                .andExpect(status().isNoContent());

        verify(channelService).deleteChannel(100L);
    }

    @Test
    void deleteChannel_Fail_WhenNotFound() throws Exception {
        doThrow(new BadRequestAlertException("Channel not found with id: 999", "channel", "channelnotfound"))
                .when(channelService).deleteChannel(999L);

        mockMvc.perform(delete("/api/v1/channels/999"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteChannel_Fail_WhenCannotDeleteGeneral() throws Exception {
        doThrow(new BadRequestAlertException("Kênh #general là kênh mặc định và không thể bị xóa", "channel", "cannotdeletegeneral"))
                .when(channelService).deleteChannel(101L);

        mockMvc.perform(delete("/api/v1/channels/101"))
                .andExpect(status().isBadRequest());
    }
}
