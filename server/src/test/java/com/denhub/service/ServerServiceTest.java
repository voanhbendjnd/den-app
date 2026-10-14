package com.denhub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.denhub.domain.Server;
import com.denhub.domain.ServerMember;
import com.denhub.domain.User;
import com.denhub.repository.ServerMemberRepository;
import com.denhub.repository.ServerRepository;
import com.denhub.repository.UserRepository;
import com.denhub.service.dto.CreateServerDTO;
import com.denhub.service.dto.ServerMemberResponseDTO;
import com.denhub.service.dto.ServerResponseDTO;
import com.denhub.web.rest.errors.BadRequestAlertException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.Arguments;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class ServerServiceTest {

    @Mock
    private ServerRepository serverRepository;

    @Mock
    private ServerMemberRepository serverMemberRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private com.denhub.repository.InviteRepository inviteRepository;

    @Mock
    private com.denhub.repository.ChannelRepository channelRepository;

    @InjectMocks
    private ServerService serverService;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getServerDetail_OwnerCanReadWithoutMembership() {
        Server server = prepareDetailRequest();
        server.setOwnerId(1L);
        server.setIconUrl("https://example.test/icon.png");
        Instant createdDate = Instant.parse("2026-10-13T01:00:00Z");
        server.setCreatedDate(createdDate);

        ServerResponseDTO response = serverService.getServerDetail(10L);

        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getName()).isEqualTo("Detail server");
        assertThat(response.getIconUrl()).isEqualTo(server.getIconUrl());
        assertThat(response.getOwnerId()).isEqualTo(1L);
        assertThat(response.getCreatedDate()).isEqualTo(createdDate);
        verifyNoInteractions(serverMemberRepository);
        verify(serverRepository, never()).save(any());
    }

    @Test
    void getServerDetail_MemberCanRead() {
        prepareDetailRequest();
        when(serverMemberRepository.existsByServerIdAndUserId(10L, 1L)).thenReturn(true);
        assertThat(serverService.getServerDetail(10L).getId()).isEqualTo(10L);
    }

    @Test
    void getServerDetail_RejectsOutsider() {
        prepareDetailRequest();
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> serverService.getServerDetail(10L));
        assertThat(error.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void getServerDetail_RejectsMissingServer() {
        prepareDetailUser();
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> serverService.getServerDetail(10L));
        assertThat(error.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verifyNoInteractions(serverMemberRepository);
    }

    @Test
    void getServerDetail_RejectsMissingAuthentication() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> serverService.getServerDetail(10L));
        assertThat(error.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(userRepository, serverRepository, serverMemberRepository);
    }

    @Test
    void getServerDetail_RejectsInactiveUser() {
        User user = prepareDetailUser();
        user.setActivated(false);
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> serverService.getServerDetail(10L));
        assertThat(error.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(serverRepository, serverMemberRepository);
    }

    @Test
    void getServerDetail_RejectsUserNotInDatabase() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("missing@example.test", null, java.util.List.of()));
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> serverService.getServerDetail(10L));
        assertThat(error.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(serverRepository, serverMemberRepository);
    }

    private User prepareDetailUser() {
        String email = "detail@example.test";
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, null, java.util.List.of()));
        User user = new User();
        user.setId(1L);
        user.setActivated(true);
        when(userRepository.findOneByEmail(email)).thenReturn(Optional.of(user));
        return user;
    }

    private Server prepareDetailRequest() {
        prepareDetailUser();
        Server server = new Server();
        server.setId(10L);
        server.setName("Detail server");
        server.setOwnerId(99L);
        when(serverRepository.findById(10L)).thenReturn(Optional.of(server));
        return server;
    }

    @Test
    void createServer_Success() {
        // Arrange
        String email = "test@example.com";
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(email, "password", java.util.List.of())
        );

        User mockUser = new User();
        mockUser.setId(1L);
        mockUser.setEmail(email);
        mockUser.setName("Test User");
        mockUser.setActivated(true);

        when(userRepository.findOneByEmail(email)).thenReturn(Optional.of(mockUser));

        Server mockSavedServer = new Server();
        mockSavedServer.setId(10L);
        mockSavedServer.setName("Test Server");
        mockSavedServer.setIconUrl("http://example.com/icon.png");
        mockSavedServer.setOwnerId(1L);
        mockSavedServer.setCreatedDate(Instant.now());

        when(serverRepository.save(any(Server.class))).thenReturn(mockSavedServer);

        CreateServerDTO dto = new CreateServerDTO();
        dto.setName("Test Server");
        dto.setIconUrl("http://example.com/icon.png");

        // Act
        ServerResponseDTO response = serverService.createServer(dto);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getName()).isEqualTo("Test Server");
        assertThat(response.getOwnerId()).isEqualTo(1L);

        verify(serverRepository, times(1)).save(any(Server.class));
        verify(serverMemberRepository, times(1)).save(any(ServerMember.class));
    }

    @Test
    void createServer_FailWhenNotLoggedIn() {
        // Arrange
        CreateServerDTO dto = new CreateServerDTO();
        dto.setName("Test Server");

        // Act & Assert
        assertThat(assertThrows(ResponseStatusException.class, () -> serverService.createServer(dto))
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(serverRepository, never()).save(any());
    }

    @Test
    void getUserServers_Success() {
        String email = "test@example.com";
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(email, "password", java.util.List.of())
        );

        User mockUser = new User();
        mockUser.setId(1L);
        mockUser.setEmail(email);
        mockUser.setActivated(true);

        when(userRepository.findOneByEmail(email)).thenReturn(Optional.of(mockUser));

        Server server = new Server();
        server.setId(10L);
        server.setName("My Server");
        server.setOwnerId(1L);

        when(serverRepository.findAllByUserIdOrOwnerId(1L)).thenReturn(java.util.List.of(server));

        java.util.List<ServerResponseDTO> result = serverService.getUserServers();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("My Server");
    }

    @ParameterizedTest
    @MethodSource("ownerNames")
    void createServer_NormalizesOnlyGeneratedOwnerNickname(String name, String expected) {
        User user = prepareDetailUser();
        user.setName(name);
        when(serverRepository.save(any(Server.class))).thenAnswer(invocation -> {
            Server saved = invocation.getArgument(0);
            saved.setId(10L);
            return saved;
        });
        CreateServerDTO dto = new CreateServerDTO();
        dto.setName("New server");

        serverService.createServer(dto);

        ArgumentCaptor<ServerMember> member = ArgumentCaptor.forClass(ServerMember.class);
        verify(serverMemberRepository).save(member.capture());
        assertThat(member.getValue().getServerId()).isEqualTo(10L);
        assertThat(member.getValue().getUserId()).isEqualTo(user.getId());
        assertThat(member.getValue().getNickname()).isEqualTo(expected);
        assertThat(user.getName()).isEqualTo(name);
        verify(userRepository, never()).save(any());
    }

    static java.util.stream.Stream<Arguments> ownerNames() {
        return java.util.stream.Stream.of(
                Arguments.of(null, "Owner"),
                Arguments.of("", "Owner"),
                Arguments.of(" \t\u2003 ", "Owner"),
                Arguments.of("  Test User  ", "Test User"),
                Arguments.of("a".repeat(50), "a".repeat(20)),
                Arguments.of("a".repeat(19) + "🙂x", "a".repeat(19)),
                Arguments.of("🙂".repeat(11), "🙂".repeat(10)));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void createAndListRejectInactiveAccountWithoutAccessingServers(boolean create) {
        User user = prepareDetailUser();
        user.setActivated(false);
        assertThat(assertThrows(ResponseStatusException.class, () -> {
            if (create) serverService.createServer(new CreateServerDTO());
            else serverService.getUserServers();
        }).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(serverRepository, serverMemberRepository);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void createAndListRejectMissingAccount(boolean create) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("missing@example.test", null, java.util.List.of()));
        assertThat(assertThrows(ResponseStatusException.class, () -> {
            if (create) serverService.createServer(new CreateServerDTO());
            else serverService.getUserServers();
        }).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(serverRepository, serverMemberRepository);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void createAndListRejectUnauthenticatedToken(boolean create) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("detail@example.test", null));
        assertThat(assertThrows(ResponseStatusException.class, () -> {
            if (create) serverService.createServer(new CreateServerDTO());
            else serverService.getUserServers();
        }).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(userRepository, serverRepository, serverMemberRepository);
    }

    @Test
    void joinServer_Success() {
        String email = "test@example.com";
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(email, "password")
        );

        User mockUser = new User();
        mockUser.setId(1L);
        mockUser.setEmail(email);
        mockUser.setName("Test User");

        when(userRepository.findOneByEmail(email)).thenReturn(Optional.of(mockUser));

        Server server = new Server();
        server.setId(10L);

        when(serverRepository.findById(10L)).thenReturn(Optional.of(server));
        when(serverMemberRepository.existsByServerIdAndUserId(10L, 1L)).thenReturn(false);

        serverService.joinServer(10L);

        verify(serverMemberRepository, times(1)).save(any(ServerMember.class));
    }

    @Test
    void joinServer_FailWhenNotLoggedIn() {
        assertThrows(BadRequestAlertException.class, () -> serverService.joinServer(10L));
    }

    @Test
    void joinServer_FailWhenServerNotFound() {
        String email = "test@example.com";
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(email, "password")
        );

        User mockUser = new User();
        mockUser.setId(1L);
        mockUser.setEmail(email);

        when(userRepository.findOneByEmail(email)).thenReturn(Optional.of(mockUser));
        when(serverRepository.findById(10L)).thenReturn(Optional.empty());

        assertThrows(BadRequestAlertException.class, () -> serverService.joinServer(10L));
    }

    @Test
    void joinServer_FailWhenAlreadyMember() {
        String email = "test@example.com";
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(email, "password")
        );

        User mockUser = new User();
        mockUser.setId(1L);
        mockUser.setEmail(email);

        when(userRepository.findOneByEmail(email)).thenReturn(Optional.of(mockUser));

        Server server = new Server();
        server.setId(10L);

        when(serverRepository.findById(10L)).thenReturn(Optional.of(server));
        when(serverMemberRepository.existsByServerIdAndUserId(10L, 1L)).thenReturn(true);

        assertThrows(BadRequestAlertException.class, () -> serverService.joinServer(10L));
    }

    @Test
    void getServerMembers_Success_WhenOwner() {
        Server server = prepareDetailRequest();
        server.setOwnerId(1L);

        ServerMember member1 = new ServerMember();
        member1.setId(101L);
        member1.setServerId(10L);
        member1.setUserId(1L);
        member1.setNickname("Owner Nick");

        ServerMember member2 = new ServerMember();
        member2.setId(102L);
        member2.setServerId(10L);
        member2.setUserId(2L);
        member2.setNickname("Member Nick");

        when(serverMemberRepository.findAllByServerId(10L)).thenReturn(java.util.List.of(member1, member2));

        User user1 = new User();
        user1.setId(1L);
        user1.setUsername("owner_user");
        user1.setName("Owner Name");
        user1.setAvatar("https://example.test/owner.png");

        User user2 = new User();
        user2.setId(2L);
        user2.setUsername("member_user");
        user2.setName("Member Name");
        user2.setAvatar("https://example.test/member.png");

        when(userRepository.findAllById(java.util.List.of(1L, 2L))).thenReturn(java.util.List.of(user1, user2));

        java.util.List<ServerMemberResponseDTO> result = serverService.getServerMembers(10L);

        assertThat(result).hasSize(2);

        ServerMemberResponseDTO res1 = result.get(0);
        assertThat(res1.getId()).isEqualTo(101L);
        assertThat(res1.getServerId()).isEqualTo(10L);
        assertThat(res1.getUserId()).isEqualTo(1L);
        assertThat(res1.getUsername()).isEqualTo("owner_user");
        assertThat(res1.getName()).isEqualTo("Owner Name");
        assertThat(res1.getNickname()).isEqualTo("Owner Nick");
        assertThat(res1.getAvatar()).isEqualTo("https://example.test/owner.png");
        assertThat(res1.getRole()).isEqualTo("OWNER");

        ServerMemberResponseDTO res2 = result.get(1);
        assertThat(res2.getId()).isEqualTo(102L);
        assertThat(res2.getServerId()).isEqualTo(10L);
        assertThat(res2.getUserId()).isEqualTo(2L);
        assertThat(res2.getUsername()).isEqualTo("member_user");
        assertThat(res2.getName()).isEqualTo("Member Name");
        assertThat(res2.getNickname()).isEqualTo("Member Nick");
        assertThat(res2.getAvatar()).isEqualTo("https://example.test/member.png");
        assertThat(res2.getRole()).isEqualTo("MEMBER");
    }

    @Test
    void getServerMembers_Success_WhenMember() {
        prepareDetailRequest();
        when(serverMemberRepository.existsByServerIdAndUserId(10L, 1L)).thenReturn(true);

        ServerMember member = new ServerMember();
        member.setId(201L);
        member.setServerId(10L);
        member.setUserId(1L);
        member.setNickname("MemberNick");

        when(serverMemberRepository.findAllByServerId(10L)).thenReturn(java.util.List.of(member));

        User user = new User();
        user.setId(1L);
        user.setUsername("user1");
        user.setName("User One");

        when(userRepository.findAllById(java.util.List.of(1L))).thenReturn(java.util.List.of(user));

        java.util.List<ServerMemberResponseDTO> result = serverService.getServerMembers(10L);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRole()).isEqualTo("MEMBER");
    }

    @Test
    void getServerMembers_EmptyList_WhenNoMembers() {
        prepareDetailRequest();
        when(serverMemberRepository.existsByServerIdAndUserId(10L, 1L)).thenReturn(true);
        when(serverMemberRepository.findAllByServerId(10L)).thenReturn(java.util.Collections.emptyList());

        java.util.List<ServerMemberResponseDTO> result = serverService.getServerMembers(10L);
        assertThat(result).isEmpty();
        verify(userRepository, never()).findAllById(any());
    }

    @Test
    void getServerMembers_RejectsOutsider() {
        prepareDetailRequest();
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> serverService.getServerMembers(10L));
        assertThat(error.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(serverMemberRepository, never()).findAllByServerId(any());
    }

    @Test
    void getServerMembers_RejectsMissingServer() {
        prepareDetailUser();
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> serverService.getServerMembers(10L));
        assertThat(error.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(serverMemberRepository, never()).findAllByServerId(any());
    }

    @Test
    void getServerMembers_RejectsMissingAuthentication() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> serverService.getServerMembers(10L));
        assertThat(error.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(serverRepository, serverMemberRepository);
    }

    @Test
    void getServerMembers_RejectsInactiveUser() {
        User user = prepareDetailUser();
        user.setActivated(false);
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> serverService.getServerMembers(10L));
        assertThat(error.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(serverRepository, serverMemberRepository);
    }

    @Test
    void createServerInvite_Success_WhenOwner() {
        Server server = prepareDetailRequest();
        server.setOwnerId(1L);

        when(inviteRepository.existsByCode(anyString())).thenReturn(false);
        when(inviteRepository.save(any(com.denhub.domain.Invite.class))).thenAnswer(invocation -> {
            com.denhub.domain.Invite inv = invocation.getArgument(0);
            inv.setId(999L);
            return inv;
        });

        com.denhub.service.dto.ServerInviteResponseDTO result = serverService.createServerInvite(10L, null);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(999L);
        assertThat(result.getServerId()).isEqualTo(10L);
        assertThat(result.getCode()).hasSize(8);
        assertThat(result.getCreatedBy()).isEqualTo(1L);
        assertThat(result.getExpiresAt()).isNull();
        assertThat(result.getStatus()).isEqualTo("ACTIVE");
        assertThat(result.getCreatedAt()).isNotNull();

        verify(inviteRepository).save(any(com.denhub.domain.Invite.class));
    }

    @Test
    void createServerInvite_Success_WithCustomExpiresAt() {
        Server server = prepareDetailRequest();
        server.setOwnerId(1L);

        Instant future = Instant.now().plusSeconds(3600);
        com.denhub.service.dto.CreateServerInviteRequestDTO dto = new com.denhub.service.dto.CreateServerInviteRequestDTO();
        dto.setExpiresAt(future);

        when(inviteRepository.existsByCode(anyString())).thenReturn(false);
        when(inviteRepository.save(any(com.denhub.domain.Invite.class))).thenAnswer(invocation -> {
            com.denhub.domain.Invite inv = invocation.getArgument(0);
            inv.setId(1001L);
            return inv;
        });

        com.denhub.service.dto.ServerInviteResponseDTO result = serverService.createServerInvite(10L, dto);

        assertThat(result.getExpiresAt()).isEqualTo(future);
        verify(inviteRepository).save(any(com.denhub.domain.Invite.class));
    }

    @Test
    void createServerInvite_RejectsNonOwner() {
        Server server = prepareDetailRequest();
        server.setOwnerId(2L); // Different user is owner

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> serverService.createServerInvite(10L, null));
        assertThat(error.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(inviteRepository, never()).save(any());
    }

    @Test
    void createServerInvite_RejectsMissingServer() {
        prepareDetailUser();
        when(serverRepository.findById(10L)).thenReturn(Optional.empty());

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> serverService.createServerInvite(10L, null));
        assertThat(error.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(inviteRepository, never()).save(any());
    }

    @Test
    void createServerInvite_RejectsInvalidServerId() {
        ResponseStatusException error1 = assertThrows(ResponseStatusException.class,
                () -> serverService.createServerInvite(null, null));
        assertThat(error1.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseStatusException error2 = assertThrows(ResponseStatusException.class,
                () -> serverService.createServerInvite(0L, null));
        assertThat(error2.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseStatusException error3 = assertThrows(ResponseStatusException.class,
                () -> serverService.createServerInvite(-5L, null));
        assertThat(error3.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createServerInvite_RejectsPastExpiresAt() {
        Server server = prepareDetailRequest();
        server.setOwnerId(1L);

        Instant past = Instant.now().minusSeconds(3600);
        com.denhub.service.dto.CreateServerInviteRequestDTO dto = new com.denhub.service.dto.CreateServerInviteRequestDTO();
        dto.setExpiresAt(past);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> serverService.createServerInvite(10L, dto));
        assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(inviteRepository, never()).save(any());
    }

    @Test
    void createServerInvite_RetriesOnCodeCollision() {
        Server server = prepareDetailRequest();
        server.setOwnerId(1L);

        // First attempt collides, second attempt succeeds
        when(inviteRepository.existsByCode(anyString())).thenReturn(true).thenReturn(false);
        when(inviteRepository.save(any(com.denhub.domain.Invite.class))).thenAnswer(invocation -> invocation.getArgument(0));

        com.denhub.service.dto.ServerInviteResponseDTO result = serverService.createServerInvite(10L, null);

        assertThat(result).isNotNull();
        verify(inviteRepository, times(2)).existsByCode(anyString());
        verify(inviteRepository).save(any(com.denhub.domain.Invite.class));
    }

    @Test
    void createServerInvite_FailsWhenAllCandidatesCollide() {
        Server server = prepareDetailRequest();
        server.setOwnerId(1L);

        // All 5 attempts collide
        when(inviteRepository.existsByCode(anyString())).thenReturn(true);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> serverService.createServerInvite(10L, null));
        assertThat(error.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        verify(inviteRepository, times(5)).existsByCode(anyString());
        verify(inviteRepository, never()).save(any());
    }

    @Test
    void createServerInvite_RejectsMissingAuthentication() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> serverService.createServerInvite(10L, null));
        assertThat(error.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(serverRepository, inviteRepository);
    }

    @Test
    void createServerInvite_RejectsInactiveUser() {
        User user = prepareDetailUser();
        user.setActivated(false);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> serverService.createServerInvite(10L, null));
        assertThat(error.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(serverRepository, inviteRepository);
    }

    @Test
    void createServer_CreatesServerMemberAndDefaultGeneralChannel() {
        prepareDetailUser();

        when(serverRepository.save(any(Server.class))).thenAnswer(inv -> {
            Server s = inv.getArgument(0);
            s.setId(99L);
            return s;
        });
        when(serverMemberRepository.save(any(ServerMember.class))).thenAnswer(inv -> inv.getArgument(0));
        when(channelRepository.save(any(com.denhub.domain.Channel.class))).thenAnswer(inv -> inv.getArgument(0));

        com.denhub.service.dto.CreateServerDTO dto = new com.denhub.service.dto.CreateServerDTO();
        dto.setName("My Study Server");
        dto.setIconUrl("https://example.test/icon.png");

        ServerResponseDTO response = serverService.createServer(dto);

        assertThat(response.getId()).isEqualTo(99L);
        assertThat(response.getName()).isEqualTo("My Study Server");
        assertThat(response.getOwnerId()).isEqualTo(1L);

        // Verify ServerMember saved as OWNER
        org.mockito.ArgumentCaptor<ServerMember> memberCaptor = org.mockito.ArgumentCaptor.forClass(ServerMember.class);
        verify(serverMemberRepository).save(memberCaptor.capture());
        ServerMember savedMember = memberCaptor.getValue();
        assertThat(savedMember.getServerId()).isEqualTo(99L);
        assertThat(savedMember.getUserId()).isEqualTo(1L);
        assertThat(savedMember.getRole()).isEqualTo("OWNER");

        // Verify default channel #general saved
        org.mockito.ArgumentCaptor<com.denhub.domain.Channel> channelCaptor = org.mockito.ArgumentCaptor.forClass(com.denhub.domain.Channel.class);
        verify(channelRepository).save(channelCaptor.capture());
        com.denhub.domain.Channel savedChannel = channelCaptor.getValue();
        assertThat(savedChannel.getServerId()).isEqualTo(99L);
        assertThat(savedChannel.getName()).isEqualTo("general");
        assertThat(savedChannel.getPosition()).isEqualTo(0);
    }

    @Test
    void joinServerByInviteCode_Success() {
        prepareDetailUser();

        com.denhub.domain.Invite invite = new com.denhub.domain.Invite();
        invite.setId(5L);
        invite.setServerId(10L);
        invite.setCode("valid123");
        invite.setStatus("ACTIVE");
        invite.setCreatedBy(2L);

        Server server = new Server();
        server.setId(10L);
        server.setName("Target Server");
        server.setOwnerId(2L);

        when(inviteRepository.findByCode("valid123")).thenReturn(Optional.of(invite));
        when(serverRepository.findById(10L)).thenReturn(Optional.of(server));
        when(serverMemberRepository.existsByServerIdAndUserId(10L, 1L)).thenReturn(false);
        when(serverMemberRepository.save(any(ServerMember.class))).thenAnswer(inv -> inv.getArgument(0));

        ServerResponseDTO result = serverService.joinServerByInviteCode(" valid123 ");

        assertThat(result.getId()).isEqualTo(10L);
        assertThat(result.getName()).isEqualTo("Target Server");

        org.mockito.ArgumentCaptor<ServerMember> captor = org.mockito.ArgumentCaptor.forClass(ServerMember.class);
        verify(serverMemberRepository).save(captor.capture());
        ServerMember saved = captor.getValue();
        assertThat(saved.getServerId()).isEqualTo(10L);
        assertThat(saved.getUserId()).isEqualTo(1L);
        assertThat(saved.getRole()).isEqualTo("MEMBER");
    }

    @Test
    void joinServerByInviteCode_RejectsBlankCode() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> serverService.joinServerByInviteCode("   "));
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void joinServerByInviteCode_RejectsNonExistentInvite() {
        prepareDetailUser();
        when(inviteRepository.findByCode("notfound")).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> serverService.joinServerByInviteCode("notfound"));
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void joinServerByInviteCode_RejectsRevokedInvite() {
        prepareDetailUser();

        com.denhub.domain.Invite invite = new com.denhub.domain.Invite();
        invite.setCode("revoked1");
        invite.setStatus("REVOKED");

        when(inviteRepository.findByCode("revoked1")).thenReturn(Optional.of(invite));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> serverService.joinServerByInviteCode("revoked1"));
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void joinServerByInviteCode_RejectsExpiredInvite() {
        prepareDetailUser();

        com.denhub.domain.Invite invite = new com.denhub.domain.Invite();
        invite.setCode("expired1");
        invite.setStatus("ACTIVE");
        invite.setExpiresAt(Instant.now().minusSeconds(3600));

        when(inviteRepository.findByCode("expired1")).thenReturn(Optional.of(invite));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> serverService.joinServerByInviteCode("expired1"));
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void joinServerByInviteCode_RejectsAlreadyMember() {
        prepareDetailUser();

        com.denhub.domain.Invite invite = new com.denhub.domain.Invite();
        invite.setCode("already1");
        invite.setStatus("ACTIVE");
        invite.setServerId(10L);

        Server server = new Server();
        server.setId(10L);

        when(inviteRepository.findByCode("already1")).thenReturn(Optional.of(invite));
        when(serverRepository.findById(10L)).thenReturn(Optional.of(server));
        when(serverMemberRepository.existsByServerIdAndUserId(10L, 1L)).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> serverService.joinServerByInviteCode("already1"));
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
