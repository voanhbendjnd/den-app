package com.denhub.web.rest;

import com.denhub.domain.*;
import com.denhub.repository.*;
import com.denhub.security.SecurityUtils;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** HTTP, real signed JWT, service transaction and H2/JPA; never connects to the local SQL Server. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ServerDeleteIT {
    @Autowired MockMvc mvc;
    @Autowired JwtEncoder encoder;
    @Autowired UserRepository users;
    @Autowired ServerRepository servers;
    @Autowired ChannelRepository channels;
    @Autowired ServerMemberRepository members;
    @Autowired MessageRepository messages;
    @Autowired ConversationRepository conversations;
    @MockitoSpyBean InviteRepository invites;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired com.denhub.service.ChannelService channelService;
    User owner;
    Server target;

    @BeforeEach
    void setup() {
        owner = new User();
        owner.setEmail("delete-" + UUID.randomUUID() + "@example.test");
        owner.setPassword(UUID.randomUUID().toString());
        owner.setName("Delete owner");
        owner.setActivated(true);
        owner.setSessionId(UUID.randomUUID().toString());
        owner = users.saveAndFlush(owner);
        target = server("Target");
    }

    @Test
    void deletesEntireGraphIncludingGeneralButPreservesOtherServerDirectChatAndUsers() throws Exception {
        Channel general = channel(target, "general");
        Channel second = channel(target, "other");
        Message firstMessage = message(general.getId(), null);
        Message secondMessage = message(second.getId(), null);
        membership(target, "OWNER");
        Invite targetInvite = invite(target);
        Server other = server("Other");
        Channel otherChannel = channel(other, "general");
        Message otherMessage = message(otherChannel.getId(), null);
        ServerMember otherMember = membership(other, "MEMBER");
        Invite otherInvite = invite(other);
        Conversation conversation = conversations.saveAndFlush(new Conversation(null, "DIRECT", null));
        Message direct = message(null, conversation.getId());
        conversation.setLastMessageId(direct.getId());
        conversations.saveAndFlush(conversation);

        request(token()).andExpect(status().isNoContent()).andExpect(content().string(""));
        em.flush();
        em.clear();
        assertFalse(servers.existsById(target.getId()));
        assertFalse(channels.existsById(general.getId()));
        assertFalse(channels.existsById(second.getId()));
        assertFalse(messages.existsById(firstMessage.getId()));
        assertFalse(messages.existsById(secondMessage.getId()));
        assertTrue(members.findAllByServerId(target.getId()).isEmpty());
        assertFalse(invites.existsById(targetInvite.getId()));
        assertTrue(servers.existsById(other.getId()));
        assertTrue(channels.existsById(otherChannel.getId()));
        assertTrue(messages.existsById(otherMessage.getId()));
        assertTrue(members.existsById(otherMember.getId()));
        assertTrue(invites.existsById(otherInvite.getId()));
        assertEquals("keep content", messages.findById(direct.getId()).orElseThrow().getContent());
        assertEquals(direct.getId(), conversations.findById(conversation.getId()).orElseThrow().getLastMessageId());
        assertTrue(users.existsById(owner.getId()));
        problem(request(token()), 404);
    }

    @Test
    void ownerCanDeleteEmptyServerWithoutMembership() throws Exception {
        request(token()).andExpect(status().isNoContent()).andExpect(content().string(""));
        em.clear();
        assertFalse(servers.existsById(target.getId()));
    }

    @Test
    void membershipCannotReplaceActualOwnerEvenWithGlobalAdmin() throws Exception {
        target.setOwnerId(owner.getId() + 100000);
        servers.saveAndFlush(target);
        membership(target, "OWNER");
        Channel general = channel(target, "general");
        Message saved = message(general.getId(), null);
        problem(request(token(owner.getSessionId(), Instant.now().plusSeconds(600), List.of("ROLE_ADMIN"))), 403);
        assertTrue(servers.existsById(target.getId()));
        assertTrue(messages.existsById(saved.getId()));
    }

    @Test
    void outsiderCannotDelete() throws Exception {
        target.setOwnerId(owner.getId() + 100000);
        servers.saveAndFlush(target);
        problem(request(token()), 403);
        assertTrue(servers.existsById(target.getId()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc", "9223372036854775808"})
    void rejectsInvalidIds(String id) throws Exception {
        problem(mvc.perform(delete("/api/v1/servers/" + id).header(HttpHeaders.AUTHORIZATION, token())), 400);
        assertTrue(servers.existsById(target.getId()));
    }

    @Test
    void missingServerIs404() throws Exception {
        problem(mvc.perform(delete("/api/v1/servers/" + Long.MAX_VALUE).header(HttpHeaders.AUTHORIZATION, token())), 404);
    }

    @Test
    void rejectsMissingMalformedExpiredWrongSignatureAndWrongSessionTokens() throws Exception {
        problem(mvc.perform(delete(url())), 401);
        problem(request("Bearer malformed"), 401);
        problem(request(token(owner.getSessionId(), Instant.now().minusSeconds(60), List.of("ROLE_USER"))), 401);
        problem(request(token("wrong-session", Instant.now().plusSeconds(600), List.of("ROLE_USER"))), 401);
        String valid = token();
        int signature = valid.lastIndexOf('.') + 1;
        String wrong = valid.substring(0, signature) + (valid.charAt(signature) == 'A' ? 'B' : 'A') + valid.substring(signature + 1);
        problem(request(wrong), 401);
        assertTrue(servers.existsById(target.getId()));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rejectsInactiveOrDeletedAccount(boolean deleted) throws Exception {
        String bearer = token();
        if (deleted) users.delete(owner);
        else { owner.setActivated(false); users.saveAndFlush(owner); }
        problem(request(bearer), 401);
        assertTrue(servers.existsById(target.getId()));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void refusesCrossDomainMessageReferencesWithoutDeletingAnything(boolean lastMessageReference) throws Exception {
        Channel general = channel(target, "general");
        Conversation conversation = conversations.saveAndFlush(new Conversation(null, "DIRECT", null));
        Message mixed = message(general.getId(), lastMessageReference ? null : conversation.getId());
        if (lastMessageReference) {
            conversation.setLastMessageId(mixed.getId());
            conversations.saveAndFlush(conversation);
        }
        problem(request(token()), 409);
        assertTrue(servers.existsById(target.getId()));
        assertTrue(channels.existsById(general.getId()));
        assertTrue(messages.existsById(mixed.getId()));
        assertTrue(conversations.existsById(conversation.getId()));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void deletionWaitsForExistingWriterAndRemovesTheCommittedChannel() throws Exception {
        var writerLocked = new java.util.concurrent.CountDownLatch(1);
        var allowWriter = new java.util.concurrent.CountDownLatch(1);
        var deletionStarted = new java.util.concurrent.CountDownLatch(1);
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        Long targetId = target.getId();
        String bearer = token();
        try {
            var writer = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                assertTrue(servers.findByIdForUpdate(targetId).isPresent());
                Channel late = new Channel(); late.setServerId(targetId); late.setName("in-flight");
                Channel saved = channels.saveAndFlush(late);
                writerLocked.countDown();
                try { assertTrue(allowWriter.await(10, java.util.concurrent.TimeUnit.SECONDS)); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted); }
                return saved.getId();
            }));
            assertTrue(writerLocked.await(10, java.util.concurrent.TimeUnit.SECONDS));
            var deletion = executor.submit(() -> {
                deletionStarted.countDown();
                return request(bearer).andReturn().getResponse().getStatus();
            });
            assertTrue(deletionStarted.await(10, java.util.concurrent.TimeUnit.SECONDS));
            assertThrows(java.util.concurrent.TimeoutException.class,
                    () -> deletion.get(200, java.util.concurrent.TimeUnit.MILLISECONDS));
            allowWriter.countDown();
            Long createdChannel = writer.get(10, java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(204, deletion.get(10, java.util.concurrent.TimeUnit.SECONDS));
            assertFalse(servers.existsById(targetId));
            assertFalse(channels.existsById(createdChannel));
        } finally {
            allowWriter.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS));
            cleanup();
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void waitingChannelCreationRechecksServerAfterDeletionCommits() throws Exception {
        var deletionEntered = new java.util.concurrent.CountDownLatch(1);
        var allowDeletion = new java.util.concurrent.CountDownLatch(1);
        var writerStarted = new java.util.concurrent.CountDownLatch(1);
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        String bearer = token();
        String ownerEmail = owner.getEmail();
        Long targetId = target.getId();
        channel(target, "general");
        doAnswer(invocation -> {
            deletionEntered.countDown();
            assertTrue(allowDeletion.await(10, java.util.concurrent.TimeUnit.SECONDS));
            return em.createQuery("DELETE FROM Invite i WHERE i.serverId = :serverId")
                    .setParameter("serverId", targetId).executeUpdate();
        }).when(invites).deleteAllByServerId(targetId);
        try {
            var deletion = executor.submit(() -> request(bearer).andReturn().getResponse().getStatus());
            assertTrue(deletionEntered.await(10, java.util.concurrent.TimeUnit.SECONDS));
            var writer = executor.submit(() -> {
                org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                        new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(ownerEmail, null, List.of()));
                try {
                    var dto = new com.denhub.service.dto.CreateChannelDTO();
                    dto.setServerId(targetId); dto.setName("late-channel");
                    writerStarted.countDown();
                    return assertThrows(com.denhub.web.rest.errors.BadRequestAlertException.class,
                            () -> channelService.createChannel(dto)).getErrorKey();
                } finally {
                    org.springframework.security.core.context.SecurityContextHolder.clearContext();
                }
            });
            assertTrue(writerStarted.await(10, java.util.concurrent.TimeUnit.SECONDS));
            assertThrows(java.util.concurrent.TimeoutException.class,
                    () -> writer.get(200, java.util.concurrent.TimeUnit.MILLISECONDS));
            allowDeletion.countDown();
            assertEquals(204, deletion.get(10, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals("servernotfound", writer.get(10, java.util.concurrent.TimeUnit.SECONDS));
            assertFalse(servers.existsById(targetId));
            assertTrue(channels.findAllByServerIdOrderByPositionAsc(targetId).isEmpty());
        } finally {
            allowDeletion.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS));
            reset(invites);
            cleanup();
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void failureAfterDeletingMessagesAndChannelsRollsBackWholeServiceTransaction() throws Exception {
        Channel general = channel(target, "general");
        Message saved = message(general.getId(), null);
        ServerMember member = membership(target, "OWNER");
        Invite invite = invite(target);
        doThrow(new IllegalStateException("simulated storage failure")).when(invites).deleteAllByServerId(target.getId());
        try {
            problem(request(token()), 500).andExpect(jsonPath("$.detail").value("Đã xảy ra lỗi hệ thống"));
            assertTrue(servers.existsById(target.getId()));
            assertTrue(channels.existsById(general.getId()));
            assertTrue(messages.existsById(saved.getId()));
            assertTrue(members.existsById(member.getId()));
            assertTrue(invites.existsById(invite.getId()));
        } finally {
            reset(invites);
            cleanup();
        }
    }

    private void cleanup() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            messages.deleteChannelMessagesByServerId(target.getId());
            channels.deleteAllByServerId(target.getId());
            invites.deleteAllByServerId(target.getId());
            members.deleteAllByServerId(target.getId());
            if (servers.existsById(target.getId())) servers.deleteById(target.getId());
            users.deleteById(owner.getId());
        });
    }

    private Server server(String name) {
        Server s = new Server(); s.setName(name); s.setOwnerId(owner.getId());
        return servers.saveAndFlush(s);
    }
    private Channel channel(Server s, String name) {
        Channel c = new Channel(); c.setServerId(s.getId()); c.setName(name); c.setPosition(0);
        return channels.saveAndFlush(c);
    }
    private Message message(Long channelId, Long conversationId) {
        Message m = new Message(); m.setChannelId(channelId); m.setConversationId(conversationId);
        m.setAuthorId(owner.getId()); m.setContent("keep content");
        return messages.saveAndFlush(m);
    }
    private ServerMember membership(Server s, String role) {
        ServerMember m = new ServerMember(); m.setServerId(s.getId()); m.setUserId(owner.getId()); m.setNickname("Owner"); m.setRole(role);
        return members.saveAndFlush(m);
    }
    private Invite invite(Server s) {
        Invite i = new Invite(); i.setServerId(s.getId()); i.setCreatedBy(owner.getId()); i.setCode(UUID.randomUUID().toString().replace("-", "")); i.setStatus("ACTIVE");
        return invites.saveAndFlush(i);
    }
    private String url() { return "/api/v1/servers/" + target.getId(); }
    private ResultActions request(String token) throws Exception { return mvc.perform(delete(url()).header(HttpHeaders.AUTHORIZATION, token)); }
    private ResultActions problem(ResultActions result, int status) throws Exception {
        return result.andExpect(status().is(status)).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(status)).andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.data").doesNotExist()).andExpect(jsonPath("$.stackTrace").doesNotExist());
    }
    private String token() { return token(owner.getSessionId(), Instant.now().plusSeconds(600), List.of("ROLE_USER")); }
    private String token(String session, Instant expiry, List<String> roles) {
        JwtClaimsSet claims = JwtClaimsSet.builder().subject(owner.getEmail()).issuedAt(Instant.now().minusSeconds(1200))
                .expiresAt(expiry).claim("sessionId", session).claim("authorities", roles).build();
        return "Bearer " + encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SecurityUtils.JWT_ALGORITHM).build(), claims)).getTokenValue();
    }
}
