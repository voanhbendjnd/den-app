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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

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
    void createServer_Success() {
        // Arrange
        String email = "test@example.com";
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(email, "password")
        );

        User mockUser = new User();
        mockUser.setId(1L);
        mockUser.setEmail(email);
        mockUser.setName("Test User");

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
        assertThrows(BadRequestAlertException.class, () -> serverService.createServer(dto));
        verify(serverRepository, never()).save(any());
    }

    @Test
    void getUserServers_Success() {
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
        server.setName("My Server");
        server.setOwnerId(1L);

        when(serverRepository.findAllByUserIdOrOwnerId(1L)).thenReturn(java.util.List.of(server));

        java.util.List<ServerResponseDTO> result = serverService.getUserServers();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("My Server");
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
