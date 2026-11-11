package com.denhub.service;

import com.denhub.domain.Authority;
import com.denhub.domain.Server;
import com.denhub.domain.ServerMember;
import com.denhub.domain.User;
import com.denhub.repository.*;
import com.denhub.security.AuthoritiesConstants;
import com.denhub.service.dto.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock UserRepository userRepository;
    @Mock AuthorityRepository authorityRepository;
    @Mock ServerRepository serverRepository;
    @Mock ServerMemberRepository serverMemberRepository;
    @Mock ChannelRepository channelRepository;
    @Mock InviteRepository inviteRepository;
    @Mock MessageRepository messageRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock CacheManager cacheManager;

    @InjectMocks AdminUserService adminUserService;

    User adminUser;

    @BeforeEach
    void setUp() {
        Authority adminAuth = new Authority();
        adminAuth.setName(AuthoritiesConstants.ADMIN);
        Authority userAuth = new Authority();
        userAuth.setName(AuthoritiesConstants.USER);

        adminUser = new User();
        adminUser.setId(1L);
        adminUser.setEmail("admin@denhub.test");
        adminUser.setUsername("admin");
        adminUser.setName("Admin");
        adminUser.setActivated(true);
        adminUser.setAuthorities(Set.of(adminAuth, userAuth));

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        adminUser.getEmail(),
                        null,
                        List.of(new SimpleGrantedAuthority(AuthoritiesConstants.ADMIN))
                )
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void mockAdminAuth() {
        when(userRepository.findOneWithAuthoritiesByEmail("admin@denhub.test"))
                .thenReturn(Optional.of(adminUser));
    }

    @Test
    void getUsers_returnsPageWithOwnedServerCounts() {
        mockAdminAuth();
        User u2 = new User();
        u2.setId(2L);
        u2.setEmail("user2@denhub.test");
        u2.setUsername("user2");
        u2.setName("User 2");
        u2.setActivated(true);

        when(userRepository.searchAdminUsers(eq("%user2%"), isNull(), eq(true), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(u2)));
        when(serverRepository.countByOwnerIds(List.of(2L)))
                .thenReturn(List.<Object[]>of(new Object[]{2L, 3L}));

        AdminUserPageDTO result = adminUserService.getUsers("user2", null, true, 0, 10);

        assertEquals(1, result.getItems().size());
        assertEquals(3L, result.getItems().get(0).getOwnedServerCount());
        assertEquals("user2@denhub.test", result.getItems().get(0).getEmail());
    }

    @Test
    void createUser_createsAndAssignsAuthorities() {
        mockAdminAuth();
        Authority userAuth = new Authority();
        userAuth.setName(AuthoritiesConstants.USER);
        when(authorityRepository.findById(AuthoritiesConstants.USER)).thenReturn(Optional.of(userAuth));
        when(userRepository.findOneByEmail("new@denhub.test")).thenReturn(Optional.empty());
        when(userRepository.findOneByUsernameIgnoreCase("newuser")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("secret123")).thenReturn("hashed_secret");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(10L);
            return u;
        });

        AdminCreateUserDTO dto = AdminCreateUserDTO.builder()
                .email("new@denhub.test")
                .username("newuser")
                .name("New User")
                .password("secret123")
                .role("ROLE_USER")
                .activated(true)
                .build();

        AdminUserResponseDTO res = adminUserService.createUser(dto);

        assertEquals(10L, res.getId());
        assertEquals("new@denhub.test", res.getEmail());
        assertEquals("ROLE_USER", res.getRole());
        assertTrue(res.getActivated());
    }

    @Test
    void softDeleteUser_transfersServerToEarliestJoinedActiveMemberAndDeletesEmptyServer() {
        mockAdminAuth();

        User targetOwner = new User();
        targetOwner.setId(5L);
        targetOwner.setEmail("owner@denhub.test");
        targetOwner.setUsername("owner5");
        targetOwner.setName("Owner 5");
        targetOwner.setActivated(true);
        targetOwner.setSessionId("sess-5");
        targetOwner.setRefreshToken("ref-5");

        User inactiveMember = new User();
        inactiveMember.setId(6L);
        inactiveMember.setUsername("inactive6");
        inactiveMember.setName("Inactive 6");
        inactiveMember.setActivated(false);

        User earliestActiveMember = new User();
        earliestActiveMember.setId(7L);
        earliestActiveMember.setUsername("early7");
        earliestActiveMember.setName("Early 7");
        earliestActiveMember.setActivated(true);

        User laterActiveMember = new User();
        laterActiveMember.setId(8L);
        laterActiveMember.setUsername("later8");
        laterActiveMember.setName("Later 8");
        laterActiveMember.setActivated(true);

        // Server 100 has owner(5), inactive(6), earliestActive(7), laterActive(8)
        Server serverWithMembers = new Server();
        serverWithMembers.setId(100L);
        serverWithMembers.setName("Community Server");
        serverWithMembers.setOwnerId(5L);

        ServerMember mOwner = new ServerMember();
        mOwner.setId(1L); mOwner.setServerId(100L); mOwner.setUserId(5L); mOwner.setNickname("Owner");
        ServerMember mInactive = new ServerMember();
        mInactive.setId(2L); mInactive.setServerId(100L); mInactive.setUserId(6L); mInactive.setNickname("Inact");
        ServerMember mEarliest = new ServerMember();
        mEarliest.setId(3L); mEarliest.setServerId(100L); mEarliest.setUserId(7L); mEarliest.setNickname("Early");
        ServerMember mLater = new ServerMember();
        mLater.setId(4L); mLater.setServerId(100L); mLater.setUserId(8L); mLater.setNickname("Later");

        // Server 200 has only owner(5) -> empty server
        Server emptyServer = new Server();
        emptyServer.setId(200L);
        emptyServer.setName("Solo Server");
        emptyServer.setOwnerId(5L);

        ServerMember mSolo = new ServerMember();
        mSolo.setId(5L); mSolo.setServerId(200L); mSolo.setUserId(5L); mSolo.setNickname("Owner");

        when(userRepository.findOneWithAuthoritiesById(5L)).thenReturn(Optional.of(targetOwner));
        when(serverRepository.findAllByOwnerIdForUpdate(5L)).thenReturn(List.of(serverWithMembers, emptyServer));

        when(serverMemberRepository.findAllByServerIdOrderByIdAsc(100L))
                .thenReturn(List.of(mOwner, mInactive, mEarliest, mLater));
        when(userRepository.findByIdIn(List.of(6L, 7L, 8L)))
                .thenReturn(List.of(inactiveMember, earliestActiveMember, laterActiveMember));

        when(serverMemberRepository.findAllByServerIdOrderByIdAsc(200L))
                .thenReturn(List.of(mSolo));
        when(messageRepository.hasConversationReferencesInServer(200L)).thenReturn(false);

        when(userRepository.saveAndFlush(targetOwner)).thenReturn(targetOwner);
        when(serverRepository.countByOwnerId(5L)).thenReturn(0L);

        AdminSoftDeleteResultDTO result = adminUserService.softDeleteUser(5L);

        // Verify targetOwner is soft-deleted and session/token cleared
        assertFalse(targetOwner.getActivated());
        assertNull(targetOwner.getSessionId());
        assertNull(targetOwner.getRefreshToken());

        // Verify Server 100 ownership transferred to user 7 (earliest active member, skipping inactive user 6)
        assertEquals(7L, serverWithMembers.getOwnerId());
        verify(serverRepository).saveAndFlush(serverWithMembers);
        verify(serverMemberRepository).deleteByServerIdAndUserId(100L, 5L);

        // Verify Server 200 (empty server) was completely deleted
        verify(messageRepository).deleteChannelMessagesByServerId(200L);
        verify(channelRepository).deleteAllByServerId(200L);
        verify(inviteRepository).deleteAllByServerId(200L);
        verify(serverMemberRepository).deleteAllByServerId(200L);
        verify(serverRepository).delete(emptyServer);

        assertEquals(1, result.getTransferredServerCount());
        assertEquals(1, result.getDeletedEmptyServerCount());
        assertEquals(2, result.getServerResolutions().size());
    }

    @Test
    void softDeleteUser_preventsAdminSelfDelete() {
        mockAdminAuth();
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> adminUserService.softDeleteUser(1L));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void updateUser_preventsAdminSelfRoleDemotion() {
        mockAdminAuth();
        when(userRepository.findOneWithAuthoritiesById(1L)).thenReturn(Optional.of(adminUser));
        Authority userAuth = new Authority();
        userAuth.setName(AuthoritiesConstants.USER);
        when(authorityRepository.findById(AuthoritiesConstants.USER)).thenReturn(Optional.of(userAuth));

        AdminUpdateUserDTO dto = AdminUpdateUserDTO.builder()
                .role("ROLE_USER")
                .build();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> adminUserService.updateUser(1L, dto));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }
}

