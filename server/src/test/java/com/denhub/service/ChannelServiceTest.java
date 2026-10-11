package com.denhub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.denhub.domain.Channel;
import com.denhub.domain.Server;
import com.denhub.domain.User;
import com.denhub.repository.ChannelRepository;
import com.denhub.repository.ServerMemberRepository;
import com.denhub.repository.ServerRepository;
import com.denhub.repository.UserRepository;
import com.denhub.service.dto.ChannelResponseDTO;
import com.denhub.service.dto.CreateChannelDTO;
import com.denhub.web.rest.errors.BadRequestAlertException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class ChannelServiceTest {

    @Mock
    private ChannelRepository channelRepository;

    @Mock
    private ServerRepository serverRepository;

    @Mock
    private ServerMemberRepository serverMemberRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ChannelService channelService;

    private static final String TEST_EMAIL = "test@example.com";
    private User mockUser;
    private Server mockServer;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();

        mockUser = new User();
        mockUser.setId(1L);
        mockUser.setEmail(TEST_EMAIL);
        mockUser.setName("Test User");

        mockServer = new Server();
        mockServer.setId(10L);
        mockServer.setName("Test Server");
        mockServer.setOwnerId(1L);
    }

    private void authenticateUser() {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(TEST_EMAIL, "password")
        );
    }

    @Test
    void createChannel_Success_WhenOwner() {
        authenticateUser();
        when(userRepository.findOneByEmail(TEST_EMAIL)).thenReturn(Optional.of(mockUser));
        when(serverRepository.findById(10L)).thenReturn(Optional.of(mockServer));
        when(channelRepository.existsByServerIdAndName(10L, "general")).thenReturn(false);
        when(channelRepository.countByServerId(10L)).thenReturn(2);

        Channel mockSavedChannel = new Channel();
        mockSavedChannel.setId(100L);
        mockSavedChannel.setServerId(10L);
        mockSavedChannel.setName("general");
        mockSavedChannel.setPosition(2);
        mockSavedChannel.setCreatedDate(Instant.now());

        when(channelRepository.save(any(Channel.class))).thenReturn(mockSavedChannel);

        CreateChannelDTO dto = new CreateChannelDTO();
        dto.setServerId(10L);
        dto.setName("general");

        ChannelResponseDTO response = channelService.createChannel(dto);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(100L);
        assertThat(response.getServerId()).isEqualTo(10L);
        assertThat(response.getName()).isEqualTo("general");
        assertThat(response.getPosition()).isEqualTo(2);

        verify(channelRepository, times(1)).save(any(Channel.class));
    }

    @Test
    void createChannel_Success_WhenMember() {
        authenticateUser();
        // Server belongs to another owner
        mockServer.setOwnerId(99L);

        when(userRepository.findOneByEmail(TEST_EMAIL)).thenReturn(Optional.of(mockUser));
        when(serverRepository.findById(10L)).thenReturn(Optional.of(mockServer));
        when(serverMemberRepository.existsByServerIdAndUserId(10L, 1L)).thenReturn(true);
        when(channelRepository.existsByServerIdAndName(10L, "dev-chat")).thenReturn(false);
        when(channelRepository.countByServerId(10L)).thenReturn(0);

        Channel mockSavedChannel = new Channel();
        mockSavedChannel.setId(101L);
        mockSavedChannel.setServerId(10L);
        mockSavedChannel.setName("dev-chat");
        mockSavedChannel.setPosition(0);
        mockSavedChannel.setCreatedDate(Instant.now());

        when(channelRepository.save(any(Channel.class))).thenReturn(mockSavedChannel);

        CreateChannelDTO dto = new CreateChannelDTO();
        dto.setServerId(10L);
        dto.setName("dev-chat");

        ChannelResponseDTO response = channelService.createChannel(dto);

        assertThat(response).isNotNull();
        assertThat(response.getName()).isEqualTo("dev-chat");
        assertThat(response.getPosition()).isEqualTo(0);
        verify(channelRepository, times(1)).save(any(Channel.class));
    }

    @Test
    void createChannel_Fail_WhenNotLoggedIn() {
        CreateChannelDTO dto = new CreateChannelDTO();
        dto.setServerId(10L);
        dto.setName("general");

        assertThrows(BadRequestAlertException.class, () -> channelService.createChannel(dto));
        verify(channelRepository, never()).save(any());
    }

    @Test
    void createChannel_Fail_WhenServerNotFound() {
        authenticateUser();
        when(userRepository.findOneByEmail(TEST_EMAIL)).thenReturn(Optional.of(mockUser));
        when(serverRepository.findById(999L)).thenReturn(Optional.empty());

        CreateChannelDTO dto = new CreateChannelDTO();
        dto.setServerId(999L);
        dto.setName("general");

        BadRequestAlertException ex = assertThrows(BadRequestAlertException.class, () -> channelService.createChannel(dto));
        assertThat(ex.getErrorKey()).isEqualTo("servernotfound");
        verify(channelRepository, never()).save(any());
    }

    @Test
    void createChannel_Fail_WhenNotMemberOrOwner() {
        authenticateUser();
        mockServer.setOwnerId(99L);

        when(userRepository.findOneByEmail(TEST_EMAIL)).thenReturn(Optional.of(mockUser));
        when(serverRepository.findById(10L)).thenReturn(Optional.of(mockServer));
        when(serverMemberRepository.existsByServerIdAndUserId(10L, 1L)).thenReturn(false);

        CreateChannelDTO dto = new CreateChannelDTO();
        dto.setServerId(10L);
        dto.setName("secret-channel");

        BadRequestAlertException ex = assertThrows(BadRequestAlertException.class, () -> channelService.createChannel(dto));
        assertThat(ex.getErrorKey()).isEqualTo("forbidden");
        verify(channelRepository, never()).save(any());
    }

    @Test
    void createChannel_Fail_WhenNameDuplicate() {
        authenticateUser();
        when(userRepository.findOneByEmail(TEST_EMAIL)).thenReturn(Optional.of(mockUser));
        when(serverRepository.findById(10L)).thenReturn(Optional.of(mockServer));
        when(channelRepository.existsByServerIdAndName(10L, "general")).thenReturn(true);

        CreateChannelDTO dto = new CreateChannelDTO();
        dto.setServerId(10L);
        dto.setName("general");

        BadRequestAlertException ex = assertThrows(BadRequestAlertException.class, () -> channelService.createChannel(dto));
        assertThat(ex.getErrorKey()).isEqualTo("namealreadyexists");
        verify(channelRepository, never()).save(any());
    }
}
