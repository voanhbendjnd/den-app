package com.denhub.service;

import com.denhub.domain.Server;
import com.denhub.domain.User;
import com.denhub.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ServerDeleteServiceTest {
    @Mock ServerRepository serverRepository;
    @Mock ServerMemberRepository serverMemberRepository;
    @Mock MessageRepository messageRepository;
    @Mock ChannelRepository channelRepository;
    @Mock InviteRepository inviteRepository;
    @Mock UserRepository userRepository;
    @InjectMocks ServerService service;

    @AfterEach void clearAuthentication() { SecurityContextHolder.clearContext(); }

    @Test void deletesChildrenBeforeParentUnderServerLock() {
        authenticate();
        Server server = new Server(); server.setId(10L); server.setOwnerId(1L);
        when(serverRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(server));
        service.deleteServer(10L);
        var order = inOrder(serverRepository, messageRepository, channelRepository, inviteRepository, serverMemberRepository);
        order.verify(serverRepository).findByIdForUpdate(10L);
        order.verify(messageRepository).hasConversationReferencesInServer(10L);
        order.verify(messageRepository).deleteChannelMessagesByServerId(10L);
        order.verify(channelRepository).deleteAllByServerId(10L);
        order.verify(inviteRepository).deleteAllByServerId(10L);
        order.verify(serverMemberRepository).deleteAllByServerId(10L);
        order.verify(serverRepository).delete(server);
        order.verify(serverRepository).flush();
    }

    @ParameterizedTest @NullSource @ValueSource(longs = {0, -1})
    void rejectsInvalidIdsBeforeAnyServerLookup(Long id) {
        authenticate();
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.deleteServer(id)).getStatusCode().value());
        verifyNoInteractions(serverRepository, messageRepository, channelRepository, inviteRepository, serverMemberRepository);
    }

    @Test void missingAuthenticationCannotReadOrDeleteData() {
        SecurityContextHolder.clearContext();
        assertEquals(401, assertThrows(ResponseStatusException.class, () -> service.deleteServer(10L)).getStatusCode().value());
        verifyNoInteractions(userRepository, serverRepository, messageRepository, channelRepository, inviteRepository, serverMemberRepository);
    }

    @Test void conflictDoesNotRunAnyDeletion() {
        authenticate();
        Server server = new Server(); server.setOwnerId(1L);
        when(serverRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(server));
        when(messageRepository.hasConversationReferencesInServer(10L)).thenReturn(true);
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> service.deleteServer(10L)).getStatusCode().value());
        verify(messageRepository, never()).deleteChannelMessagesByServerId(anyLong());
        verify(serverRepository, never()).delete(any());
        verifyNoInteractions(channelRepository, inviteRepository, serverMemberRepository);
    }

    private void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("owner@example.test", null, List.of()));
        User user = new User(); user.setId(1L); user.setActivated(true);
        when(userRepository.findOneByEmail("owner@example.test")).thenReturn(Optional.of(user));
    }
}
