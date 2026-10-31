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
import com.denhub.service.dto.UpdateServerDTO;
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
import org.springframework.dao.DataIntegrityViolationException;
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
    void updateServer_OwnerChangesOnlyEditableFields() {
        Server server = prepareDetailRequest(true);
        server.setOwnerId(1L);
        Instant createdDate = Instant.parse("2026-10-13T01:00:00Z");
        server.setCreatedDate(createdDate);
        when(serverRepository.saveAndFlush(server)).thenReturn(server);

        ServerResponseDTO response = serverService.updateServer(10L, updateDto("  Nhóm mới  ", " https://example.test/new.png "));

        assertThat(response.getName()).isEqualTo("Nhóm mới");
        assertThat(response.getIconUrl()).isEqualTo("https://example.test/new.png");
        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getOwnerId()).isEqualTo(1L);
        assertThat(response.getCreatedDate()).isEqualTo(createdDate);
        verifyNoInteractions(serverMemberRepository, channelRepository, inviteRepository);
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.NullAndEmptySource
    @ValueSource(strings = {"   "})
    void updateServer_ClearsIcon(String icon) {
        Server server = prepareDetailRequest(true);
        server.setOwnerId(1L);
        server.setIconUrl("https://example.test/old.png");
        when(serverRepository.saveAndFlush(server)).thenReturn(server);
        assertThat(serverService.updateServer(10L, updateDto("New name", icon)).getIconUrl()).isNull();
    }

    @Test
    void updateServer_NonOwnerCannotMutateEntity() {
        Server server = prepareDetailRequest(true);
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> serverService.updateServer(10L, updateDto("Changed", null)));
        assertThat(error.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(server.getName()).isEqualTo("Detail server");
        verify(serverRepository, never()).save(any());
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.NullAndEmptySource
    @ValueSource(strings = {"   ", "\u00a0\u2003\u202f", "\u0085"})
    void updateServer_RejectsBlankNameBeforeMutation(String name) {
        Server server = prepareDetailRequest(true);
        server.setOwnerId(1L);
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> serverService.updateServer(10L, updateDto(name, null)));
        assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(server.getName()).isEqualTo("Detail server");
        verify(serverRepository, never()).save(any());
    }

    @Test
    void updateServer_RejectsOversizedFieldsBeforeMutation() {
        Server server = prepareDetailRequest(true);
        server.setOwnerId(1L);
        for (UpdateServerDTO dto : java.util.List.of(updateDto("x".repeat(101), null), updateDto("Valid", "x".repeat(256)))) {
            ResponseStatusException error = assertThrows(ResponseStatusException.class,
                    () -> serverService.updateServer(10L, dto));
            assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
        assertThat(server.getName()).isEqualTo("Detail server");
        verify(serverRepository, never()).save(any());
    }

    private UpdateServerDTO updateDto(String name, String iconUrl) {
        UpdateServerDTO dto = new UpdateServerDTO();
        dto.setName(name);
        dto.setIconUrl(iconUrl);
        return dto;
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
        return prepareDetailRequest(false);
    }

    private Server prepareDetailRequest(boolean writing) {
        prepareDetailUser();
        Server server = new Server();
        server.setId(10L);
        server.setName("Detail server");
        server.setOwnerId(99L);
        when(writing ? serverRepository.findByIdForUpdate(10L) : serverRepository.findById(10L)).thenReturn(Optional.of(server));
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
            new UsernamePasswordAuthenticationToken(email, "password", java.util.List.of())
        );

        User mockUser = new User();
        mockUser.setId(1L);
        mockUser.setEmail(email);
        mockUser.setActivated(true);
        mockUser.setName("Test User");

        when(userRepository.findOneByEmail(email)).thenReturn(Optional.of(mockUser));

        Server server = new Server();
        server.setVisibility(com.denhub.domain.enums.ServerVisibility.PUBLIC);
        server.setId(10L);

        when(serverRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(server));
        when(serverMemberRepository.existsByServerIdAndUserId(10L, 1L)).thenReturn(false);

        serverService.joinServer(10L);

        verify(serverMemberRepository, times(1)).saveAndFlush(any(ServerMember.class));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void joinServer_RejectsInactiveOrMissingAccountBeforeAccessingServer(boolean missing) {
        User user = prepareDetailUser();
        if (missing) when(userRepository.findOneByEmail("detail@example.test")).thenReturn(Optional.empty());
        else user.setActivated(false);
        assertThat(assertThrows(ResponseStatusException.class,
                () -> serverService.joinServer(10L)).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(serverRepository, serverMemberRepository);
    }

    @ParameterizedTest
    @MethodSource("joinNames")
    void joinServer_NormalizesGeneratedNicknameWithoutChangingUserName(String name, String expected) {
        User user = prepareDetailUser();
        user.setName(name);
        Server target = new Server();
        target.setVisibility(com.denhub.domain.enums.ServerVisibility.PUBLIC);
        target.setId(10L);
        when(serverRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(target));
        serverService.joinServer(10L);
        ArgumentCaptor<ServerMember> saved = ArgumentCaptor.forClass(ServerMember.class);
        verify(serverMemberRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getNickname()).isEqualTo(expected);
        assertThat(saved.getValue().getServerId()).isEqualTo(10L);
        assertThat(saved.getValue().getUserId()).isEqualTo(user.getId());
        assertThat(saved.getValue().getRole()).isEqualTo("MEMBER");
        assertThat(user.getName()).isEqualTo(name);
        verify(userRepository, never()).save(any());
    }

    static java.util.stream.Stream<Arguments> joinNames() {
        return java.util.stream.Stream.of(
                Arguments.of(null, "Member"),
                Arguments.of("", "Member"),
                Arguments.of(" \t\u2003 ", "Member"),
                Arguments.of("  Test User  ", "Test User"),
                Arguments.of("a".repeat(50), "a".repeat(20)),
                Arguments.of("a".repeat(19) + "🙂x", "a".repeat(19)),
                Arguments.of("🙂".repeat(11), "🙂".repeat(10)));
    }

    @Test
    void joinServer_FailWhenServerIdInvalid() {
        BadRequestAlertException exNull = assertThrows(BadRequestAlertException.class, () -> serverService.joinServer(null));
        assertThat(exNull.getErrorKey()).isEqualTo("idinvalid");

        BadRequestAlertException exNegative = assertThrows(BadRequestAlertException.class, () -> serverService.joinServer(-1L));
        assertThat(exNegative.getErrorKey()).isEqualTo("idinvalid");
    }

    @Test
    void joinServer_FailWhenNotLoggedIn() {
        assertThat(assertThrows(ResponseStatusException.class, () -> serverService.joinServer(10L)).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void joinServer_FailWhenServerNotFound() {
        String email = "test@example.com";
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(email, "password", java.util.List.of())
        );

        User mockUser = new User();
        mockUser.setId(1L);
        mockUser.setEmail(email);
        mockUser.setActivated(true);

        when(userRepository.findOneByEmail(email)).thenReturn(Optional.of(mockUser));
        when(serverRepository.findByIdForUpdate(10L)).thenReturn(Optional.empty());

        assertThrows(BadRequestAlertException.class, () -> serverService.joinServer(10L));
    }

    @Test
    void joinServer_FailWhenAlreadyMember() {
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
        server.setVisibility(com.denhub.domain.enums.ServerVisibility.PUBLIC);
        server.setId(10L);

        when(serverRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(server));
        when(serverMemberRepository.existsByServerIdAndUserId(10L, 1L)).thenReturn(true);

        BadRequestAlertException ex = assertThrows(BadRequestAlertException.class, () -> serverService.joinServer(10L));
        assertThat(ex.getErrorKey()).isEqualTo("alreadymember");
        verify(serverMemberRepository, never()).saveAndFlush(any());
    }

    @Test
    void joinServer_FailWhenConcurrentDuplicateConstraintViolation() {
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
        server.setVisibility(com.denhub.domain.enums.ServerVisibility.PUBLIC);
        server.setId(10L);

        when(serverRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(server));
        when(serverMemberRepository.existsByServerIdAndUserId(10L, 1L)).thenReturn(false);
        when(serverMemberRepository.saveAndFlush(any(ServerMember.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint ux_server_member"));

        BadRequestAlertException ex = assertThrows(BadRequestAlertException.class, () -> serverService.joinServer(10L));
        assertThat(ex.getErrorKey()).isEqualTo("alreadymember");
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
    void joinServer_ThenGetServerMembers_ContainsJoinedUser() {
        Server server = prepareDetailRequest();
        server.setOwnerId(1L);

        ServerMember ownerMember = new ServerMember();
        ownerMember.setId(101L);
        ownerMember.setServerId(10L);
        ownerMember.setUserId(1L);
        ownerMember.setNickname("Owner Nick");
        ownerMember.setRole("OWNER");

        ServerMember joinedMember = new ServerMember();
        joinedMember.setId(102L);
        joinedMember.setServerId(10L);
        joinedMember.setUserId(2L);
        joinedMember.setNickname("Joined User");
        joinedMember.setRole("MEMBER");

        when(serverMemberRepository.findAllByServerId(10L))
                .thenReturn(java.util.List.of(ownerMember, joinedMember));

        User user1 = new User();
        user1.setId(1L);
        user1.setUsername("owner");
        user1.setName("Owner");
        user1.setAvatar("https://example.test/owner.png");

        User user2 = new User();
        user2.setId(2L);
        user2.setUsername("joined_user");
        user2.setName("Joined User");
        user2.setAvatar("https://example.test/joined.png");

        when(userRepository.findAllById(java.util.List.of(1L, 2L)))
                .thenReturn(java.util.List.of(user1, user2));

        java.util.List<ServerMemberResponseDTO> members = serverService.getServerMembers(10L);

        assertThat(members).hasSize(2);
        ServerMemberResponseDTO memberDto = members.stream()
                .filter(m -> Long.valueOf(2L).equals(m.getUserId()))
                .findFirst()
                .orElseThrow();
        assertThat(memberDto.getId()).isEqualTo(102L);
        assertThat(memberDto.getUsername()).isEqualTo("joined_user");
        assertThat(memberDto.getName()).isEqualTo("Joined User");
        assertThat(memberDto.getRole()).isEqualTo("MEMBER");
        assertThat(memberDto.getAvatar()).isEqualTo("https://example.test/joined.png");
    }

    @Test
    void joinServerByInviteCode_ThenGetServerMembers_ContainsJoinedUser() {
        Server server = prepareDetailRequest();
        server.setOwnerId(1L);

        ServerMember joinedMember = new ServerMember();
        joinedMember.setId(103L);
        joinedMember.setServerId(10L);
        joinedMember.setUserId(3L);
        joinedMember.setNickname("Invite Joiner");
        joinedMember.setRole("MEMBER");

        when(serverMemberRepository.findAllByServerId(10L))
                .thenReturn(java.util.List.of(joinedMember));

        User user3 = new User();
        user3.setId(3L);
        user3.setUsername("invite_user");
        user3.setName("Invite Joiner");
        user3.setAvatar("https://example.test/invite.png");

        when(userRepository.findAllById(java.util.List.of(3L)))
                .thenReturn(java.util.List.of(user3));

        java.util.List<ServerMemberResponseDTO> members = serverService.getServerMembers(10L);

        assertThat(members).hasSize(1);
        ServerMemberResponseDTO dto = members.get(0);
        assertThat(dto.getUserId()).isEqualTo(3L);
        assertThat(dto.getUsername()).isEqualTo("invite_user");
        assertThat(dto.getRole()).isEqualTo("MEMBER");
    }

    @Test
    void getServerMembers_GracefulHandlingWhenUserEntityMissingInRepository() {
        Server server = prepareDetailRequest();
        server.setOwnerId(1L);

        ServerMember orphanMember = new ServerMember();
        orphanMember.setId(104L);
        orphanMember.setServerId(10L);
        orphanMember.setUserId(99L);
        orphanMember.setNickname("Ghost User");
        orphanMember.setRole("MEMBER");

        when(serverMemberRepository.findAllByServerId(10L))
                .thenReturn(java.util.List.of(orphanMember));
        when(userRepository.findAllById(java.util.List.of(99L)))
                .thenReturn(java.util.Collections.emptyList());

        java.util.List<ServerMemberResponseDTO> members = serverService.getServerMembers(10L);

        assertThat(members).hasSize(1);
        ServerMemberResponseDTO dto = members.get(0);
        assertThat(dto.getUserId()).isEqualTo(99L);
        assertThat(dto.getNickname()).isEqualTo("Ghost User");
        assertThat(dto.getUsername()).isNull();
        assertThat(dto.getRole()).isEqualTo("MEMBER");
    }

    @Test
    void createServerInvite_Success_WhenOwner() {
        Server server = prepareDetailRequest(true);
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
        Server server = prepareDetailRequest(true);
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
        Server server = prepareDetailRequest(true);
        server.setOwnerId(2L); // Different user is owner

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> serverService.createServerInvite(10L, null));
        assertThat(error.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(inviteRepository, never()).save(any());
    }

    @Test
    void createServerInvite_RejectsMissingServer() {
        prepareDetailUser();
        when(serverRepository.findByIdForUpdate(10L)).thenReturn(Optional.empty());

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
        Server server = prepareDetailRequest(true);
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
        Server server = prepareDetailRequest(true);
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
        Server server = prepareDetailRequest(true);
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
        when(serverRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(server));
        when(serverMemberRepository.existsByServerIdAndUserId(10L, 1L)).thenReturn(false);
        when(serverMemberRepository.saveAndFlush(any(ServerMember.class))).thenAnswer(inv -> inv.getArgument(0));

        ServerResponseDTO result = serverService.joinServerByInviteCode(" valid123 ");

        assertThat(result.getId()).isEqualTo(10L);
        assertThat(result.getName()).isEqualTo("Target Server");

        org.mockito.ArgumentCaptor<ServerMember> captor = org.mockito.ArgumentCaptor.forClass(ServerMember.class);
        verify(serverMemberRepository).saveAndFlush(captor.capture());
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
        when(serverRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(server));
        when(serverMemberRepository.existsByServerIdAndUserId(10L, 1L)).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> serverService.joinServerByInviteCode("already1"));
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getReason()).isEqualTo("Bạn đã là thành viên của Server này rồi");
        verify(serverMemberRepository, never()).saveAndFlush(any());
    }

    @Test
    void joinServerByInviteCode_RejectsConcurrentDuplicateConstraintViolation() {
        prepareDetailUser();

        com.denhub.domain.Invite invite = new com.denhub.domain.Invite();
        invite.setCode("concurr1");
        invite.setStatus("ACTIVE");
        invite.setServerId(10L);

        Server server = new Server();
        server.setId(10L);

        when(inviteRepository.findByCode("concurr1")).thenReturn(Optional.of(invite));
        when(serverRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(server));
        when(serverMemberRepository.existsByServerIdAndUserId(10L, 1L)).thenReturn(false);
        when(serverMemberRepository.saveAndFlush(any(ServerMember.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint ux_server_member"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> serverService.joinServerByInviteCode("concurr1"));
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getReason()).isEqualTo("Bạn đã là thành viên của Server này rồi");
    }


    @Test
    void leaveServer_Success_ServerDisappearsFromUserServers() {
        prepareDetailUser(); // User id = 1L (member)

        Server server1 = new Server();
        server1.setId(10L);
        server1.setName("Server Ten");
        server1.setOwnerId(2L);

        Server server2 = new Server();
        server2.setId(20L);
        server2.setName("Server Twenty");
        server2.setOwnerId(3L);

        when(serverRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(server1));
        when(serverMemberRepository.existsByServerIdAndUserId(10L, 1L)).thenReturn(true);

        // Before leave: user had server1 and server2
        when(serverRepository.findAllByUserIdOrOwnerId(1L)).thenReturn(java.util.List.of(server1, server2));
        java.util.List<ServerResponseDTO> beforeLeave = serverService.getUserServers();
        assertThat(beforeLeave).hasSize(2);
        assertThat(beforeLeave.stream().anyMatch(s -> Long.valueOf(10L).equals(s.getId()))).isTrue();

        // Perform leave
        serverService.leaveServer(10L);
        verify(serverMemberRepository, times(1)).deleteByServerIdAndUserId(10L, 1L);

        // After leave: user only has server2
        when(serverRepository.findAllByUserIdOrOwnerId(1L)).thenReturn(java.util.List.of(server2));
        java.util.List<ServerResponseDTO> afterLeave = serverService.getUserServers();
        assertThat(afterLeave).hasSize(1);
        assertThat(afterLeave.stream().anyMatch(s -> Long.valueOf(10L).equals(s.getId()))).isFalse();
        assertThat(afterLeave.get(0).getId()).isEqualTo(20L);
    }

    @Test
    void leaveServer_OwnerCannotLeave_ServerRemainsInOwnerServerList() {
        prepareDetailUser(); // User id = 1L (owner)

        Server ownedServer = new Server();
        ownedServer.setId(10L);
        ownedServer.setName("Owner Server");
        ownedServer.setOwnerId(1L);

        when(serverRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(ownedServer));
        when(serverRepository.findAllByUserIdOrOwnerId(1L)).thenReturn(java.util.List.of(ownedServer));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> serverService.leaveServer(10L));
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getReason()).contains("Chủ sở hữu không thể rời khỏi Server");
        verify(serverMemberRepository, never()).deleteByServerIdAndUserId(anyLong(), anyLong());

        java.util.List<ServerResponseDTO> ownerServers = serverService.getUserServers();
        assertThat(ownerServers).hasSize(1);
        assertThat(ownerServers.get(0).getId()).isEqualTo(10L);
    }

    @Test
    void leaveServer_Success() {
        prepareDetailUser();

        Server server = new Server();
        server.setId(10L);
        server.setOwnerId(2L);

        when(serverRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(server));
        when(serverMemberRepository.existsByServerIdAndUserId(10L, 1L)).thenReturn(true);

        serverService.leaveServer(10L);

        verify(serverMemberRepository, times(1)).deleteByServerIdAndUserId(10L, 1L);
    }

    @Test
    void leaveServer_FailWhenNotLoggedIn() {
        SecurityContextHolder.clearContext();
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> serverService.leaveServer(10L));
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void leaveServer_FailWhenServerNotFound() {
        prepareDetailUser();
        when(serverRepository.findByIdForUpdate(999L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> serverService.leaveServer(999L));
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void leaveServer_FailWhenOwnerAttemptsToLeave() {
        prepareDetailUser();

        Server server = new Server();
        server.setId(10L);
        server.setOwnerId(1L);

        when(serverRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(server));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> serverService.leaveServer(10L));
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getReason()).contains("Chủ sở hữu không thể rời khỏi Server");
        verify(serverMemberRepository, never()).deleteByServerIdAndUserId(anyLong(), anyLong());
    }

    @Test
    void leaveServer_FailWhenUserIsNotMember() {
        prepareDetailUser();

        Server server = new Server();
        server.setId(10L);
        server.setOwnerId(2L);

        when(serverRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(server));
        when(serverMemberRepository.existsByServerIdAndUserId(10L, 1L)).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> serverService.leaveServer(10L));
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getReason()).contains("Bạn không phải là thành viên");
        verify(serverMemberRepository, never()).deleteByServerIdAndUserId(anyLong(), anyLong());
    }

    @Test
    void leaveServer_FailWhenServerIdInvalid() {
        ResponseStatusException ex1 = assertThrows(ResponseStatusException.class,
                () -> serverService.leaveServer(null));
        assertThat(ex1.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseStatusException ex2 = assertThrows(ResponseStatusException.class,
                () -> serverService.leaveServer(0L));
        assertThat(ex2.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseStatusException ex3 = assertThrows(ResponseStatusException.class,
                () -> serverService.leaveServer(-1L));
        assertThat(ex3.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }


    @Test
    void removeMember_ThenTargetMemberBlockedFromAccessingServer() {
        prepareDetailUser(); // User id = 1L (Owner)

        Server server = new Server();
        server.setId(10L);
        server.setOwnerId(1L);

        when(serverRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(server));
        when(serverMemberRepository.existsByServerIdAndUserId(10L, 2L)).thenReturn(true);

        serverService.removeMember(10L, 2L);
        verify(serverMemberRepository).deleteByServerIdAndUserId(10L, 2L);

        // Target (User 2) is now removed. Target attempts to access Server 10
        String targetEmail = "target_kicked@example.test";
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(targetEmail, null, java.util.List.of()));
        User targetUser = new User();
        targetUser.setId(2L);
        targetUser.setActivated(true);
        when(userRepository.findOneByEmail(targetEmail)).thenReturn(Optional.of(targetUser));

        when(serverRepository.findById(10L)).thenReturn(Optional.of(server));
        when(serverMemberRepository.existsByServerIdAndUserId(10L, 2L)).thenReturn(false);

        ResponseStatusException exDetail = assertThrows(ResponseStatusException.class,
                () -> serverService.getServerDetail(10L));
        assertThat(exDetail.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseStatusException exMembers = assertThrows(ResponseStatusException.class,
                () -> serverService.getServerMembers(10L));
        assertThat(exMembers.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        when(serverRepository.findAllByUserIdOrOwnerId(2L)).thenReturn(java.util.Collections.emptyList());
        java.util.List<ServerResponseDTO> userServers = serverService.getUserServers();
        assertThat(userServers).isEmpty();
    }

    @Test
    void removeMember_TargetCannotPerformAdministrativeActions() {
        prepareDetailUser(); // User id = 1L (Owner)

        Server server = new Server();
        server.setId(10L);
        server.setOwnerId(1L);

        when(serverRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(server));
        when(serverMemberRepository.existsByServerIdAndUserId(10L, 2L)).thenReturn(true);

        serverService.removeMember(10L, 2L);

        String targetEmail = "target_admin_fail@example.test";
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(targetEmail, null, java.util.List.of()));
        User targetUser = new User();
        targetUser.setId(2L);
        targetUser.setActivated(true);
        when(userRepository.findOneByEmail(targetEmail)).thenReturn(Optional.of(targetUser));

        ResponseStatusException exInvite = assertThrows(ResponseStatusException.class,
                () -> serverService.createServerInvite(10L, null));
        assertThat(exInvite.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseStatusException exRemove = assertThrows(ResponseStatusException.class,
                () -> serverService.removeMember(10L, 3L));
        assertThat(exRemove.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void removeMember_SuccessWhenOwnerRemovesMember() {
        prepareDetailUser(); // User id = 1L

        Server server = new Server();
        server.setId(10L);
        server.setOwnerId(1L); // Caller is Owner

        when(serverRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(server));
        when(serverMemberRepository.existsByServerIdAndUserId(10L, 2L)).thenReturn(true);

        serverService.removeMember(10L, 2L);

        verify(serverMemberRepository).deleteByServerIdAndUserId(10L, 2L);
    }

    @Test
    void removeMember_FailWhenNotLoggedIn() {
        SecurityContextHolder.clearContext();
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> serverService.removeMember(10L, 2L));
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(serverRepository, serverMemberRepository);
    }

    @Test
    void removeMember_FailWhenServerIdInvalid() {
        ResponseStatusException ex1 = assertThrows(ResponseStatusException.class,
                () -> serverService.removeMember(null, 2L));
        assertThat(ex1.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseStatusException ex2 = assertThrows(ResponseStatusException.class,
                () -> serverService.removeMember(0L, 2L));
        assertThat(ex2.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseStatusException ex3 = assertThrows(ResponseStatusException.class,
                () -> serverService.removeMember(-1L, 2L));
        assertThat(ex3.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void removeMember_FailWhenUserIdInvalid() {
        ResponseStatusException ex1 = assertThrows(ResponseStatusException.class,
                () -> serverService.removeMember(10L, null));
        assertThat(ex1.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseStatusException ex2 = assertThrows(ResponseStatusException.class,
                () -> serverService.removeMember(10L, 0L));
        assertThat(ex2.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseStatusException ex3 = assertThrows(ResponseStatusException.class,
                () -> serverService.removeMember(10L, -5L));
        assertThat(ex3.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void removeMember_FailWhenServerNotFound() {
        prepareDetailUser();
        when(serverRepository.findByIdForUpdate(999L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> serverService.removeMember(999L, 2L));
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(serverMemberRepository, never()).deleteByServerIdAndUserId(anyLong(), anyLong());
    }

    @Test
    void removeMember_FailWhenCallerNotOwner() {
        prepareDetailUser(); // User id = 1L

        Server server = new Server();
        server.setId(10L);
        server.setOwnerId(99L); // Caller is NOT Owner

        when(serverRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(server));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> serverService.removeMember(10L, 2L));
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ex.getReason()).contains("Chỉ chủ sở hữu Server mới có quyền loại thành viên");
        verify(serverMemberRepository, never()).deleteByServerIdAndUserId(anyLong(), anyLong());
    }

    @Test
    void removeMember_FailWhenTargetIsOwner() {
        prepareDetailUser(); // User id = 1L

        Server server = new Server();
        server.setId(10L);
        server.setOwnerId(1L);

        when(serverRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(server));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> serverService.removeMember(10L, 1L));
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getReason()).contains("Không thể loại chủ sở hữu khỏi Server");
        verify(serverMemberRepository, never()).deleteByServerIdAndUserId(anyLong(), anyLong());
    }

    @Test
    void removeMember_FailWhenTargetNotMember() {
        prepareDetailUser(); // User id = 1L

        Server server = new Server();
        server.setId(10L);
        server.setOwnerId(1L);

        when(serverRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(server));
        when(serverMemberRepository.existsByServerIdAndUserId(10L, 50L)).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> serverService.removeMember(10L, 50L));
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ex.getReason()).contains("Thành viên không tồn tại trong Server");
        verify(serverMemberRepository, never()).deleteByServerIdAndUserId(anyLong(), anyLong());
    }

    @Test
    void removeMember_FailWhenTargetAlreadyRemoved_ReturnsNotFound() {
        prepareDetailUser(); // User id = 1L (Owner)

        Server server = new Server();
        server.setId(10L);
        server.setOwnerId(1L);

        when(serverRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(server));
        when(serverMemberRepository.existsByServerIdAndUserId(10L, 20L))
                .thenReturn(true)  // 1st call: member exists
                .thenReturn(false); // 2nd call: already removed

        // 1st call succeeds
        serverService.removeMember(10L, 20L);
        verify(serverMemberRepository, times(1)).deleteByServerIdAndUserId(10L, 20L);

        // 2nd call on the same member fails with 404
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> serverService.removeMember(10L, 20L));
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ex.getReason()).contains("Thành viên không tồn tại trong Server");
    }

    @Test
    void removeMember_FailWhenUserDeactivated() {
        String email = "deactivated@example.test";
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, null, java.util.List.of()));
        User inactiveUser = new User();
        inactiveUser.setId(1L);
        inactiveUser.setActivated(false);
        when(userRepository.findOneByEmail(email)).thenReturn(Optional.of(inactiveUser));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> serverService.removeMember(10L, 2L));
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(ex.getReason()).contains("Phiên đăng nhập không hợp lệ");
        verifyNoInteractions(serverRepository, serverMemberRepository);
    }

    @Test
    void removeMember_FailWhenCallerHasAdminAuthorityButNotServerOwner() {
        String email = "admin@example.test";
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        email,
                        null,
                        java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"))));
        User adminUser = new User();
        adminUser.setId(888L);
        adminUser.setActivated(true);
        when(userRepository.findOneByEmail(email)).thenReturn(Optional.of(adminUser));

        Server server = new Server();
        server.setId(10L);
        server.setOwnerId(1L); // Actual owner is 1L, not 888L

        when(serverRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(server));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> serverService.removeMember(10L, 2L));
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ex.getReason()).contains("Chỉ chủ sở hữu Server mới có quyền loại thành viên");
        verify(serverMemberRepository, never()).deleteByServerIdAndUserId(anyLong(), anyLong());
    }

    @Test
    void removeMember_FailWhenTargetMatchesPrincipal_ReturnsBadRequest() {
        prepareDetailUser(); // User id = 1L

        Server server = new Server();
        server.setId(10L);
        server.setOwnerId(1L);

        when(serverRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(server));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> serverService.removeMember(10L, 1L));
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getReason()).contains("Không thể loại chủ sở hữu khỏi Server");
        verify(serverMemberRepository, never()).deleteByServerIdAndUserId(anyLong(), anyLong());
    }
}
