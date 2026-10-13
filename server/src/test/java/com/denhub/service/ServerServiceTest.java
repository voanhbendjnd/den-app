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
}
