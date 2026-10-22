package com.denhub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.denhub.domain.Channel;
import com.denhub.domain.Server;
import com.denhub.domain.User;
import com.denhub.repository.ChannelRepository;
import com.denhub.repository.MessageRepository;
import com.denhub.repository.ServerMemberRepository;
import com.denhub.repository.ServerRepository;
import com.denhub.repository.UserRepository;
import com.denhub.service.dto.ChannelResponseDTO;
import com.denhub.service.dto.CreateChannelDTO;
import com.denhub.service.dto.UpdateChannelDTO;
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
import java.util.List;
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

    @Mock
    private MessageRepository messageRepository;

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
        when(serverRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(mockServer));
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
        when(serverRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(mockServer));
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
        when(serverRepository.findByIdForUpdate(999L)).thenReturn(Optional.empty());

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
        when(serverRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(mockServer));
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
        when(serverRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(mockServer));
        when(channelRepository.existsByServerIdAndName(10L, "general")).thenReturn(true);

        CreateChannelDTO dto = new CreateChannelDTO();
        dto.setServerId(10L);
        dto.setName("general");

        BadRequestAlertException ex = assertThrows(BadRequestAlertException.class, () -> channelService.createChannel(dto));
        assertThat(ex.getErrorKey()).isEqualTo("namealreadyexists");
        verify(channelRepository, never()).save(any());
    }

    @Test
    void getChannels_Success_WhenOwner() {
        authenticateUser();
        when(userRepository.findOneByEmail(TEST_EMAIL)).thenReturn(Optional.of(mockUser));
        when(serverRepository.findById(10L)).thenReturn(Optional.of(mockServer));

        Channel ch1 = new Channel();
        ch1.setId(101L);
        ch1.setServerId(10L);
        ch1.setName("general");
        ch1.setPosition(0);
        ch1.setCreatedDate(Instant.now());

        Channel ch2 = new Channel();
        ch2.setId(102L);
        ch2.setServerId(10L);
        ch2.setName("announcements");
        ch2.setPosition(1);
        ch2.setCreatedDate(Instant.now());

        when(channelRepository.findAllByServerIdOrderByPositionAsc(10L)).thenReturn(List.of(ch1, ch2));

        List<ChannelResponseDTO> result = channelService.getChannelsByServerId(10L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getName()).isEqualTo("general");
        assertThat(result.get(0).getPosition()).isEqualTo(0);
        assertThat(result.get(1).getName()).isEqualTo("announcements");
        assertThat(result.get(1).getPosition()).isEqualTo(1);
    }

    @Test
    void getChannels_Success_WhenMember() {
        authenticateUser();
        mockServer.setOwnerId(99L); // different owner

        when(userRepository.findOneByEmail(TEST_EMAIL)).thenReturn(Optional.of(mockUser));
        when(serverRepository.findById(10L)).thenReturn(Optional.of(mockServer));
        when(serverMemberRepository.existsByServerIdAndUserId(10L, 1L)).thenReturn(true);

        Channel ch = new Channel();
        ch.setId(101L);
        ch.setServerId(10L);
        ch.setName("general");
        ch.setPosition(0);

        when(channelRepository.findAllByServerIdOrderByPositionAsc(10L)).thenReturn(List.of(ch));

        List<ChannelResponseDTO> result = channelService.getChannelsByServerId(10L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(101L);
    }

    @Test
    void getChannels_Fail_WhenNotLoggedIn() {
        BadRequestAlertException ex = assertThrows(BadRequestAlertException.class,
                () -> channelService.getChannelsByServerId(10L));
        assertThat(ex.getErrorKey()).isEqualTo("notloggedin");
    }

    @Test
    void getChannels_Fail_WhenServerNotFound() {
        authenticateUser();
        when(userRepository.findOneByEmail(TEST_EMAIL)).thenReturn(Optional.of(mockUser));
        when(serverRepository.findById(999L)).thenReturn(Optional.empty());

        BadRequestAlertException ex = assertThrows(BadRequestAlertException.class,
                () -> channelService.getChannelsByServerId(999L));
        assertThat(ex.getErrorKey()).isEqualTo("servernotfound");
    }

    @Test
    void getChannels_Fail_WhenNotMemberOrOwner() {
        authenticateUser();
        mockServer.setOwnerId(99L);

        when(userRepository.findOneByEmail(TEST_EMAIL)).thenReturn(Optional.of(mockUser));
        when(serverRepository.findById(10L)).thenReturn(Optional.of(mockServer));
        when(serverMemberRepository.existsByServerIdAndUserId(10L, 1L)).thenReturn(false);

        BadRequestAlertException ex = assertThrows(BadRequestAlertException.class,
                () -> channelService.getChannelsByServerId(10L));
        assertThat(ex.getErrorKey()).isEqualTo("forbidden");
    }

    @Test
    void updateChannel_Success_WhenOwner() {
        authenticateUser();
        Channel channel = new Channel();
        channel.setId(101L);
        channel.setServerId(10L);
        channel.setName("old-name");
        channel.setPosition(1);

        when(userRepository.findOneByEmail(TEST_EMAIL)).thenReturn(Optional.of(mockUser));
        when(channelRepository.findById(101L)).thenReturn(Optional.of(channel));
        when(serverRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(mockServer));
        when(channelRepository.existsByServerIdAndNameAndIdNot(10L, "new-name", 101L)).thenReturn(false);
        when(channelRepository.save(any(Channel.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateChannelDTO dto = new UpdateChannelDTO();
        dto.setName("new-name");
        dto.setPosition(5);

        ChannelResponseDTO response = channelService.updateChannel(101L, dto);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(101L);
        assertThat(response.getName()).isEqualTo("new-name");
        assertThat(response.getPosition()).isEqualTo(5);
        verify(channelRepository, times(1)).save(channel);
    }

    @Test
    void updateChannel_Success_WhenMember() {
        authenticateUser();
        mockServer.setOwnerId(99L); // different owner

        Channel channel = new Channel();
        channel.setId(101L);
        channel.setServerId(10L);
        channel.setName("general");
        channel.setPosition(0);

        when(userRepository.findOneByEmail(TEST_EMAIL)).thenReturn(Optional.of(mockUser));
        when(channelRepository.findById(101L)).thenReturn(Optional.of(channel));
        when(serverRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(mockServer));
        when(serverMemberRepository.existsByServerIdAndUserId(10L, 1L)).thenReturn(true);
        when(channelRepository.existsByServerIdAndNameAndIdNot(10L, "general-chat", 101L)).thenReturn(false);
        when(channelRepository.save(any(Channel.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateChannelDTO dto = new UpdateChannelDTO();
        dto.setName("general-chat");

        ChannelResponseDTO response = channelService.updateChannel(101L, dto);

        assertThat(response).isNotNull();
        assertThat(response.getName()).isEqualTo("general-chat");
        assertThat(response.getPosition()).isEqualTo(0); // position remains unchanged
        verify(channelRepository, times(1)).save(channel);
    }

    @Test
    void updateChannel_Success_WhenKeepingSameName() {
        authenticateUser();
        Channel channel = new Channel();
        channel.setId(101L);
        channel.setServerId(10L);
        channel.setName("general");
        channel.setPosition(0);

        when(userRepository.findOneByEmail(TEST_EMAIL)).thenReturn(Optional.of(mockUser));
        when(channelRepository.findById(101L)).thenReturn(Optional.of(channel));
        when(serverRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(mockServer));
        when(channelRepository.existsByServerIdAndNameAndIdNot(10L, "general", 101L)).thenReturn(false);
        when(channelRepository.save(any(Channel.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateChannelDTO dto = new UpdateChannelDTO();
        dto.setName("general");
        dto.setPosition(2);

        ChannelResponseDTO response = channelService.updateChannel(101L, dto);

        assertThat(response).isNotNull();
        assertThat(response.getName()).isEqualTo("general");
        assertThat(response.getPosition()).isEqualTo(2);
    }

    @Test
    void updateChannel_Fail_WhenNotLoggedIn() {
        UpdateChannelDTO dto = new UpdateChannelDTO();
        dto.setName("new-name");

        BadRequestAlertException ex = assertThrows(BadRequestAlertException.class,
                () -> channelService.updateChannel(101L, dto));
        assertThat(ex.getErrorKey()).isEqualTo("notloggedin");
    }

    @Test
    void updateChannel_Fail_WhenChannelNotFound() {
        authenticateUser();
        when(userRepository.findOneByEmail(TEST_EMAIL)).thenReturn(Optional.of(mockUser));
        when(channelRepository.findById(999L)).thenReturn(Optional.empty());

        UpdateChannelDTO dto = new UpdateChannelDTO();
        dto.setName("new-name");

        BadRequestAlertException ex = assertThrows(BadRequestAlertException.class,
                () -> channelService.updateChannel(999L, dto));
        assertThat(ex.getErrorKey()).isEqualTo("channelnotfound");
    }

    @Test
    void updateChannel_Fail_WhenNotMemberOrOwner() {
        authenticateUser();
        mockServer.setOwnerId(99L);

        Channel channel = new Channel();
        channel.setId(101L);
        channel.setServerId(10L);
        channel.setName("general");

        when(userRepository.findOneByEmail(TEST_EMAIL)).thenReturn(Optional.of(mockUser));
        when(channelRepository.findById(101L)).thenReturn(Optional.of(channel));
        when(serverRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(mockServer));
        when(serverMemberRepository.existsByServerIdAndUserId(10L, 1L)).thenReturn(false);

        UpdateChannelDTO dto = new UpdateChannelDTO();
        dto.setName("new-name");

        BadRequestAlertException ex = assertThrows(BadRequestAlertException.class,
                () -> channelService.updateChannel(101L, dto));
        assertThat(ex.getErrorKey()).isEqualTo("forbidden");
        verify(channelRepository, never()).save(any());
    }

    @Test
    void updateChannel_Fail_WhenNameDuplicate() {
        authenticateUser();
        Channel channel = new Channel();
        channel.setId(101L);
        channel.setServerId(10L);
        channel.setName("dev-chat");

        when(userRepository.findOneByEmail(TEST_EMAIL)).thenReturn(Optional.of(mockUser));
        when(channelRepository.findById(101L)).thenReturn(Optional.of(channel));
        when(serverRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(mockServer));
        when(channelRepository.existsByServerIdAndNameAndIdNot(10L, "general", 101L)).thenReturn(true);

        UpdateChannelDTO dto = new UpdateChannelDTO();
        dto.setName("general");

        BadRequestAlertException ex = assertThrows(BadRequestAlertException.class,
                () -> channelService.updateChannel(101L, dto));
        assertThat(ex.getErrorKey()).isEqualTo("namealreadyexists");
        verify(channelRepository, never()).save(any());
    }

    // ==========================================
    // Tests for deleteChannel
    // ==========================================

    @Test
    void deleteChannel_Success_WhenOwner() {
        authenticateUser();
        Channel channel = new Channel();
        channel.setId(101L);
        channel.setServerId(10L);
        channel.setName("random-chat");

        when(userRepository.findOneByEmail(TEST_EMAIL)).thenReturn(Optional.of(mockUser));
        when(channelRepository.findById(101L)).thenReturn(Optional.of(channel));
        when(serverRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(mockServer));
        when(channelRepository.countByServerId(10L)).thenReturn(2);
        when(messageRepository.hasConversationReferencesInChannel(101L)).thenReturn(false);

        channelService.deleteChannel(101L);

        verify(messageRepository).deleteByChannelId(101L);
        verify(channelRepository).delete(channel);
        verify(channelRepository).flush();
    }

    @Test
    void deleteChannel_Success_WhenMember() {
        authenticateUser();
        mockServer.setOwnerId(99L); // mockUser (id 1L) is not the owner
        Channel channel = new Channel();
        channel.setId(102L);
        channel.setServerId(10L);
        channel.setName("voice-chat");

        when(userRepository.findOneByEmail(TEST_EMAIL)).thenReturn(Optional.of(mockUser));
        when(channelRepository.findById(102L)).thenReturn(Optional.of(channel));
        when(serverRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(mockServer));
        when(serverMemberRepository.existsByServerIdAndUserId(10L, 1L)).thenReturn(true);
        when(channelRepository.countByServerId(10L)).thenReturn(3);
        when(messageRepository.hasConversationReferencesInChannel(102L)).thenReturn(false);

        channelService.deleteChannel(102L);

        verify(messageRepository).deleteByChannelId(102L);
        verify(channelRepository).delete(channel);
        verify(channelRepository).flush();
    }

    @Test
    void deleteChannel_Fail_WhenInvalidId() {
        BadRequestAlertException exNull = assertThrows(BadRequestAlertException.class,
                () -> channelService.deleteChannel(null));
        assertThat(exNull.getErrorKey()).isEqualTo("invalidid");

        BadRequestAlertException exNegative = assertThrows(BadRequestAlertException.class,
                () -> channelService.deleteChannel(-1L));
        assertThat(exNegative.getErrorKey()).isEqualTo("invalidid");

        BadRequestAlertException exZero = assertThrows(BadRequestAlertException.class,
                () -> channelService.deleteChannel(0L));
        assertThat(exZero.getErrorKey()).isEqualTo("invalidid");

        verify(channelRepository, never()).delete(any());
    }

    @Test
    void deleteChannel_Fail_WhenNotLoggedIn() {
        BadRequestAlertException ex = assertThrows(BadRequestAlertException.class,
                () -> channelService.deleteChannel(101L));
        assertThat(ex.getErrorKey()).isEqualTo("notloggedin");
        verify(channelRepository, never()).delete(any());
    }

    @Test
    void deleteChannel_Fail_WhenUserNotFound() {
        authenticateUser();
        when(userRepository.findOneByEmail(TEST_EMAIL)).thenReturn(Optional.empty());

        BadRequestAlertException ex = assertThrows(BadRequestAlertException.class,
                () -> channelService.deleteChannel(101L));
        assertThat(ex.getErrorKey()).isEqualTo("usernotfound");
        verify(channelRepository, never()).delete(any());
    }

    @Test
    void deleteChannel_Fail_WhenChannelNotFound() {
        authenticateUser();
        when(userRepository.findOneByEmail(TEST_EMAIL)).thenReturn(Optional.of(mockUser));
        when(channelRepository.findById(999L)).thenReturn(Optional.empty());

        BadRequestAlertException ex = assertThrows(BadRequestAlertException.class,
                () -> channelService.deleteChannel(999L));
        assertThat(ex.getErrorKey()).isEqualTo("channelnotfound");
        verify(channelRepository, never()).delete(any());
    }

    @Test
    void deleteChannel_Fail_WhenNotMemberOrOwner() {
        authenticateUser();
        mockServer.setOwnerId(99L);
        Channel channel = new Channel();
        channel.setId(101L);
        channel.setServerId(10L);
        channel.setName("test-chat");

        when(userRepository.findOneByEmail(TEST_EMAIL)).thenReturn(Optional.of(mockUser));
        when(channelRepository.findById(101L)).thenReturn(Optional.of(channel));
        when(serverRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(mockServer));
        when(serverMemberRepository.existsByServerIdAndUserId(10L, 1L)).thenReturn(false);

        BadRequestAlertException ex = assertThrows(BadRequestAlertException.class,
                () -> channelService.deleteChannel(101L));
        assertThat(ex.getErrorKey()).isEqualTo("forbidden");
        verify(channelRepository, never()).delete(any());
    }

    @Test
    void deleteChannel_Fail_WhenChannelIsGeneral() {
        authenticateUser();
        Channel channel = new Channel();
        channel.setId(101L);
        channel.setServerId(10L);
        channel.setName("general");

        when(userRepository.findOneByEmail(TEST_EMAIL)).thenReturn(Optional.of(mockUser));
        when(channelRepository.findById(101L)).thenReturn(Optional.of(channel));
        when(serverRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(mockServer));

        BadRequestAlertException ex = assertThrows(BadRequestAlertException.class,
                () -> channelService.deleteChannel(101L));
        assertThat(ex.getErrorKey()).isEqualTo("cannotdeletegeneral");
        verify(channelRepository, never()).delete(any());
    }

    @Test
    void deleteChannel_Fail_WhenLastChannelInServer() {
        authenticateUser();
        Channel channel = new Channel();
        channel.setId(101L);
        channel.setServerId(10L);
        channel.setName("only-channel");

        when(userRepository.findOneByEmail(TEST_EMAIL)).thenReturn(Optional.of(mockUser));
        when(channelRepository.findById(101L)).thenReturn(Optional.of(channel));
        when(serverRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(mockServer));
        when(channelRepository.countByServerId(10L)).thenReturn(1);

        BadRequestAlertException ex = assertThrows(BadRequestAlertException.class,
                () -> channelService.deleteChannel(101L));
        assertThat(ex.getErrorKey()).isEqualTo("cannotdeletelastchannel");
        verify(channelRepository, never()).delete(any());
    }

    @Test
    void deleteChannel_Fail_WhenHasConversationReferences() {
        authenticateUser();
        Channel channel = new Channel();
        channel.setId(101L);
        channel.setServerId(10L);
        channel.setName("announcements");

        when(userRepository.findOneByEmail(TEST_EMAIL)).thenReturn(Optional.of(mockUser));
        when(channelRepository.findById(101L)).thenReturn(Optional.of(channel));
        when(serverRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(mockServer));
        when(channelRepository.countByServerId(10L)).thenReturn(3);
        when(messageRepository.hasConversationReferencesInChannel(101L)).thenReturn(true);

        BadRequestAlertException ex = assertThrows(BadRequestAlertException.class,
                () -> channelService.deleteChannel(101L));
        assertThat(ex.getErrorKey()).isEqualTo("conflict");
        verify(channelRepository, never()).delete(any());
    }
}
