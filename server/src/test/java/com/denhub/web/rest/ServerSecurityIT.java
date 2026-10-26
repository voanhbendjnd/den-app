package com.denhub.web.rest;

import com.denhub.domain.Server;
import com.denhub.domain.ServerMember;
import com.denhub.domain.User;
import com.denhub.repository.ServerMemberRepository;
import com.denhub.repository.ServerRepository;
import com.denhub.repository.UserRepository;
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
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real JWT/filter/service/JPA chain; fixtures use H2 and roll back after each test. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ServerSecurityIT {
    @Autowired private MockMvc mvc;
    @Autowired private JwtEncoder encoder;
    @Autowired private UserRepository users;
    @Autowired private ServerRepository servers;
    @MockitoSpyBean private ServerMemberRepository members;
    @Autowired private com.denhub.repository.InviteRepository invites;
    @Autowired private EntityManager entityManager;

    private User reader;
    private Server server;

    @BeforeEach
    void setUp() {
        reader = new User();
        reader.setEmail("detail-" + UUID.randomUUID().toString().substring(0, 8) + "@example.test");
        reader.setPassword("fixture-" + UUID.randomUUID());
        reader.setName("Detail reader");
        reader.setActivated(true);
        reader.setSessionId(UUID.randomUUID().toString());
        reader = users.saveAndFlush(reader);
        server = new Server();
        server.setName("Detail server");
        server.setOwnerId(reader.getId());
        server.setIconUrl("https://example.test/icon.png");
        server = servers.saveAndFlush(server);
    }

    @Test
    void ownerReadsWrappedDetailWithoutMembershipAndWithoutWrites() throws Exception {
        long serverCount = servers.count();
        long memberCount = members.count();
        readsDetail();
        entityManager.flush();
        entityManager.clear();
        assertEquals(serverCount, servers.count());
        assertEquals(memberCount, members.count());
        assertEquals("Detail server", servers.findById(server.getId()).orElseThrow().getName());
    }

    @Test
    void memberReadsDetail() throws Exception {
        makeReaderOutsider();
        addMembership(server.getId());
        readsDetail();
    }

    @Test
    void outsiderIsForbiddenEvenWithGlobalAdminAuthority() throws Exception {
        makeReaderOutsider();
        problem(mvc.perform(get(detailUrl()).header(HttpHeaders.AUTHORIZATION,
                bearer(reader.getSessionId(), Instant.now().plusSeconds(600), List.of("ROLE_ADMIN")))), 403);
    }

    @Test
    void membershipInAnotherServerDoesNotGrantAccess() throws Exception {
        makeReaderOutsider();
        Server other = new Server();
        other.setName("Other server");
        other.setOwnerId(reader.getId());
        other = servers.saveAndFlush(other);
        addMembership(other.getId());
        problem(mvc.perform(get(detailUrl()).header(HttpHeaders.AUTHORIZATION, bearer())), 403);
    }

    @Test
    void missingServerIsNotFound() throws Exception {
        problem(mvc.perform(get("/api/v1/servers/" + Long.MAX_VALUE)
                .header(HttpHeaders.AUTHORIZATION, bearer())), 404);
    }

    @Test
    void nonNumericIdIsBadRequest() throws Exception {
        problem(mvc.perform(get("/api/v1/servers/not-a-number")
                .header(HttpHeaders.AUTHORIZATION, bearer())), 400);
    }

    @Test
    void missingJwtIsUnauthorized() throws Exception {
        problem(mvc.perform(get(detailUrl())), 401);
    }

    @Test
    void malformedJwtIsUnauthorized() throws Exception {
        problem(mvc.perform(get(detailUrl()).header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt")), 401);
    }

    @Test
    void invalidSignatureIsUnauthorized() throws Exception {
        String valid = bearer();
        int signatureStart = valid.lastIndexOf('.') + 1;
        char replacement = valid.charAt(signatureStart) == 'A' ? 'B' : 'A';
        String invalid = valid.substring(0, signatureStart) + replacement + valid.substring(signatureStart + 1);
        problem(mvc.perform(get(detailUrl()).header(HttpHeaders.AUTHORIZATION, invalid)), 401);
    }

    @Test
    void expiredJwtIsUnauthorized() throws Exception {
        problem(mvc.perform(get(detailUrl()).header(HttpHeaders.AUTHORIZATION,
                bearer(reader.getSessionId(), Instant.now().minusSeconds(600), List.of("ROLE_USER")))), 401);
    }

    @Test
    void invalidSessionIsUnauthorized() throws Exception {
        problem(mvc.perform(get(detailUrl()).header(HttpHeaders.AUTHORIZATION,
                bearer("invalid-session", Instant.now().plusSeconds(600), List.of("ROLE_USER")))), 401);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void missingOrInactiveAccountIsUnauthorized(boolean deleteAccount) throws Exception {
        String token = bearer();
        if (deleteAccount) users.delete(reader);
        else reader.setActivated(false);
        entityManager.flush();
        problem(mvc.perform(get(detailUrl()).header(HttpHeaders.AUTHORIZATION, token)), 401);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void createAndListRejectMissingOrInactiveAccountWithoutWriting(boolean deleteAccount) throws Exception {
        String token = bearer();
        if (deleteAccount) users.delete(reader);
        else reader.setActivated(false);
        entityManager.flush();
        long serverCount = servers.count();
        long memberCount = members.count();

        problem(mvc.perform(post("/api/v1/servers").header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"New server\"}")), 401);
        problem(mvc.perform(get("/api/v1/servers").header(HttpHeaders.AUTHORIZATION, token)), 401);

        assertEquals(serverCount, servers.count());
        assertEquals(memberCount, members.count());
    }

    @ParameterizedTest
    @ValueSource(ints = {55, 254})
    void longEmailCreatesServerAndPreservesBothAuditFields(int length) throws Exception {
        String local = "a".repeat(60);
        String domain = "b".repeat(60) + "." + "c".repeat(60) + "." + "d".repeat(60);
        String email = length == 254 ? local + "@" + domain + "." + "e".repeat(5) + ".test"
                : "a".repeat(length - "@example.test".length()) + "@example.test";
        assertEquals(length, email.length());
        reader.setEmail(email);
        reader = users.saveAndFlush(reader);
        long memberCount = members.count();
        String json = mvc.perform(post("/api/v1/servers").header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Long email server\",\"iconUrl\":\"https://example.test/new.png\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.ownerId").value(reader.getId()))
                .andReturn().getResponse().getContentAsString();
        Long createdId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json).path("data").path("id").longValue();
        entityManager.flush();
        entityManager.clear();
        Server created = servers.findById(createdId).orElseThrow();
        assertEquals(email, created.getCreatedBy());
        assertEquals(email, created.getLastModifiedBy());
        assertEquals("https://example.test/new.png", created.getIconUrl());
        assertEquals(memberCount + 1, members.count());
        org.junit.jupiter.api.Assertions.assertTrue(members.existsByServerIdAndUserId(createdId, reader.getId()));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, null, List.of()));
        try {
            created.setName("Updated server");
            servers.saveAndFlush(created);
            entityManager.clear();
            Server updated = servers.findById(createdId).orElseThrow();
            assertEquals(email, updated.getCreatedBy());
            assertEquals(email, updated.getLastModifiedBy());
            assertEquals("Updated server", updated.getName());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "                     ", "12345678901234567890123456789012345678901234567890", "1234567890123456789🙂x"})
    void validUserNameCannotBreakServerCreation(String name) throws Exception {
        reader.setName(name);
        reader = users.saveAndFlush(reader);
        mvc.perform(post("/api/v1/servers").header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"New server\"}"))
                .andExpect(status().isCreated());
        entityManager.flush();
        entityManager.clear();
        assertEquals(name, users.findById(reader.getId()).orElseThrow().getName());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void membershipFailureRollsBackServerCreation() throws Exception {
        long serverCount = servers.count();
        long memberCount = members.count();
        doThrow(new IllegalStateException("Fixture membership persistence failure"))
                .when(members).save(any(ServerMember.class));
        try {
            mvc.perform(post("/api/v1/servers").header(HttpHeaders.AUTHORIZATION, bearer())
                    .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Rollback server\"}"))
                    .andExpect(status().isInternalServerError());
            assertEquals(serverCount, servers.count());
            assertEquals(memberCount, members.count());
        } finally {
            reset(members);
            servers.deleteById(server.getId());
            users.deleteById(reader.getId());
        }
    }

    @Test
    void ownerReadsWrappedMembersList() throws Exception {
        addMembership(server.getId());
        User user2 = new User();
        user2.setEmail("user2-" + UUID.randomUUID().toString().substring(0, 8) + "@example.test");
        user2.setPassword("pwd-" + UUID.randomUUID());
        user2.setName("User Two");
        user2.setUsername("usertwo");
        user2.setActivated(true);
        user2 = users.saveAndFlush(user2);

        ServerMember member2 = new ServerMember();
        member2.setServerId(server.getId());
        member2.setUserId(user2.getId());
        member2.setNickname("TwoNick");
        members.saveAndFlush(member2);

        mvc.perform(get(membersUrl()).header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.error").value(nullValue()))
                .andExpect(jsonPath("$.message").value("Call API success!"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").isNumber())
                .andExpect(jsonPath("$.data[0].serverId").value(server.getId()))
                .andExpect(jsonPath("$.data[0].userId").value(reader.getId()))
                .andExpect(jsonPath("$.data[0].name").value(reader.getName()))
                .andExpect(jsonPath("$.data[0].nickname").value("Reader"))
                .andExpect(jsonPath("$.data[0].role").value("OWNER"))
                .andExpect(jsonPath("$.data[0].email").doesNotExist())
                .andExpect(jsonPath("$.data[0].password").doesNotExist())
                .andExpect(jsonPath("$.data[1].id").isNumber())
                .andExpect(jsonPath("$.data[1].serverId").value(server.getId()))
                .andExpect(jsonPath("$.data[1].userId").value(user2.getId()))
                .andExpect(jsonPath("$.data[1].username").value("usertwo"))
                .andExpect(jsonPath("$.data[1].name").value("User Two"))
                .andExpect(jsonPath("$.data[1].nickname").value("TwoNick"))
                .andExpect(jsonPath("$.data[1].role").value("MEMBER"))
                .andExpect(jsonPath("$.data[1].email").doesNotExist())
                .andExpect(jsonPath("$.data[1].password").doesNotExist());
    }

    @Test
    void memberReadsMembersList() throws Exception {
        makeReaderOutsider();
        addMembership(server.getId());

        mvc.perform(get(membersUrl()).header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.error").value(nullValue()))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].role").value("MEMBER"));
    }

    @Test
    void outsiderIsForbiddenFromReadingMembers() throws Exception {
        makeReaderOutsider();
        problem(mvc.perform(get(membersUrl()).header(HttpHeaders.AUTHORIZATION, bearer())), 403);
    }

    @Test
    void missingJwtIsUnauthorizedForMembers() throws Exception {
        problem(mvc.perform(get(membersUrl())), 401);
    }

    @Test
    void missingServerIsNotFoundForMembers() throws Exception {
        problem(mvc.perform(get("/api/v1/servers/" + Long.MAX_VALUE + "/members")
                .header(HttpHeaders.AUTHORIZATION, bearer())), 404);
    }

    @Test
    void nonNumericIdIsBadRequestForMembers() throws Exception {
        problem(mvc.perform(get("/api/v1/servers/not-a-number/members")
                .header(HttpHeaders.AUTHORIZATION, bearer())), 400);
    }

    @Test
    void ownerCreatesInviteSuccessfully() throws Exception {
        mvc.perform(post(invitesUrl()).header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statusCode").value(201))
                .andExpect(jsonPath("$.error").value(nullValue()))
                .andExpect(jsonPath("$.message").value("Tạo mã mời thành công"))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.serverId").value(server.getId()))
                .andExpect(jsonPath("$.data.code").isString())
                .andExpect(jsonPath("$.data.createdBy").value(reader.getId()))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.expiresAt").value(nullValue()))
                .andExpect(jsonPath("$.data.createdAt").isString());

        entityManager.flush();
        entityManager.clear();
        assertEquals(1, invites.findAllByServerId(server.getId()).size());
        com.denhub.domain.Invite saved = invites.findAllByServerId(server.getId()).get(0);
        assertEquals(8, saved.getCode().length());
        assertEquals("ACTIVE", saved.getStatus());
        assertEquals(reader.getId(), saved.getCreatedBy());
    }

    @Test
    void ownerCreatesInviteWithCustomExpiresAt() throws Exception {
        String body = "{\"expiresAt\": \"2030-01-01T00:00:00Z\"}";
        mvc.perform(post(invitesUrl())
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statusCode").value(201))
                .andExpect(jsonPath("$.data.expiresAt").value("2030-01-01T00:00:00Z"));
    }

    @Test
    void ownerCreatesInviteWithPastExpiresAtIsBadRequest() throws Exception {
        String body = "{\"expiresAt\": \"2020-01-01T00:00:00Z\"}";
        problem(mvc.perform(post(invitesUrl())
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)), 400);
    }

    @Test
    void memberIsForbiddenFromCreatingInvite() throws Exception {
        makeReaderOutsider();
        addMembership(server.getId());
        problem(mvc.perform(post(invitesUrl()).header(HttpHeaders.AUTHORIZATION, bearer())), 403);
    }

    @Test
    void outsiderIsForbiddenFromCreatingInvite() throws Exception {
        makeReaderOutsider();
        problem(mvc.perform(post(invitesUrl()).header(HttpHeaders.AUTHORIZATION, bearer())), 403);
    }

    @Test
    void missingJwtIsUnauthorizedForCreatingInvite() throws Exception {
        problem(mvc.perform(post(invitesUrl())), 401);
    }

    @Test
    void missingServerIsNotFoundForCreatingInvite() throws Exception {
        problem(mvc.perform(post("/api/v1/servers/" + Long.MAX_VALUE + "/invites")
                .header(HttpHeaders.AUTHORIZATION, bearer())), 404);
    }

    @Test
    void nonNumericIdIsBadRequestForCreatingInvite() throws Exception {
        problem(mvc.perform(post("/api/v1/servers/not-a-number/invites")
                .header(HttpHeaders.AUTHORIZATION, bearer())), 400);
    }

    @Test
    void uniqueConstraintOnInviteCodeWorks() {
        com.denhub.domain.Invite inv1 = new com.denhub.domain.Invite();
        inv1.setServerId(server.getId());
        inv1.setCode("UNIQUE123");
        inv1.setCreatedBy(reader.getId());
        inv1.setStatus("ACTIVE");
        invites.saveAndFlush(inv1);

        com.denhub.domain.Invite inv2 = new com.denhub.domain.Invite();
        inv2.setServerId(server.getId());
        inv2.setCode("UNIQUE123");
        inv2.setCreatedBy(reader.getId());
        inv2.setStatus("ACTIVE");

        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> {
            invites.saveAndFlush(inv2);
        });
    }

    @Test
    void joinServerByInviteCode_Success() throws Exception {
        User rawJoiner = new User();
        rawJoiner.setEmail("joiner-" + UUID.randomUUID().toString().substring(0, 8) + "@example.test");
        rawJoiner.setPassword("fixture-" + UUID.randomUUID());
        rawJoiner.setName("Joiner User");
        rawJoiner.setActivated(true);
        rawJoiner.setSessionId(UUID.randomUUID().toString());
        final User joiner = users.saveAndFlush(rawJoiner);

        com.denhub.domain.Invite inv = new com.denhub.domain.Invite();
        inv.setServerId(server.getId());
        inv.setCode("joinMe12");
        inv.setCreatedBy(reader.getId());
        inv.setStatus("ACTIVE");
        invites.saveAndFlush(inv);

        mvc.perform(post("/api/v1/servers/join-invite/joinMe12")
                .header(HttpHeaders.AUTHORIZATION, bearerFor(joiner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.data.id").value(server.getId()))
                .andExpect(jsonPath("$.data.name").value(server.getName()));

        assertEquals(true, members.existsByServerIdAndUserId(server.getId(), joiner.getId()));
        List<ServerMember> serverMembers = members.findAllByServerId(server.getId());
        ServerMember joinedMember = serverMembers.stream()
                .filter(m -> m.getUserId().equals(joiner.getId()))
                .findFirst().orElseThrow();
        assertEquals("MEMBER", joinedMember.getRole());
    }

    @Test
    void joinServerByInviteCode_Expired_Returns400() throws Exception {
        User joiner = new User();
        joiner.setEmail("joiner2-" + UUID.randomUUID().toString().substring(0, 8) + "@example.test");
        joiner.setPassword("fixture-" + UUID.randomUUID());
        joiner.setName("Joiner User 2");
        joiner.setActivated(true);
        joiner.setSessionId(UUID.randomUUID().toString());
        joiner = users.saveAndFlush(joiner);

        com.denhub.domain.Invite inv = new com.denhub.domain.Invite();
        inv.setServerId(server.getId());
        inv.setCode("expiredCode");
        inv.setCreatedBy(reader.getId());
        inv.setStatus("ACTIVE");
        inv.setExpiresAt(Instant.now().minusSeconds(100));
        invites.saveAndFlush(inv);

        problem(mvc.perform(post("/api/v1/servers/join-invite/expiredCode")
                .header(HttpHeaders.AUTHORIZATION, bearerFor(joiner))), 400);
    }

    @Test
    void joinServerByInviteCode_Revoked_Returns400() throws Exception {
        User joiner = new User();
        joiner.setEmail("joiner3-" + UUID.randomUUID().toString().substring(0, 8) + "@example.test");
        joiner.setPassword("fixture-" + UUID.randomUUID());
        joiner.setName("Joiner User 3");
        joiner.setActivated(true);
        joiner.setSessionId(UUID.randomUUID().toString());
        joiner = users.saveAndFlush(joiner);

        com.denhub.domain.Invite inv = new com.denhub.domain.Invite();
        inv.setServerId(server.getId());
        inv.setCode("revokedCode");
        inv.setCreatedBy(reader.getId());
        inv.setStatus("REVOKED");
        invites.saveAndFlush(inv);

        problem(mvc.perform(post("/api/v1/servers/join-invite/revokedCode")
                .header(HttpHeaders.AUTHORIZATION, bearerFor(joiner))), 400);
    }

    @Test
    void joinServerByInviteCode_NotFound_Returns404() throws Exception {
        problem(mvc.perform(post("/api/v1/servers/join-invite/nonexistentCode")
                .header(HttpHeaders.AUTHORIZATION, bearer())), 404);
    }

    @Test
    void joinServerByInviteCode_AlreadyMember_Returns400() throws Exception {
        com.denhub.domain.Invite inv = new com.denhub.domain.Invite();
        inv.setServerId(server.getId());
        inv.setCode("memberJoin");
        inv.setCreatedBy(reader.getId());
        inv.setStatus("ACTIVE");
        invites.saveAndFlush(inv);

        addMembership(server.getId());

        problem(mvc.perform(post("/api/v1/servers/join-invite/memberJoin")
                .header(HttpHeaders.AUTHORIZATION, bearer())), 400);
    }

    @Test
    void joinServerByInviteCode_MissingJwt_Returns401() throws Exception {
        problem(mvc.perform(post("/api/v1/servers/join-invite/anyCode")), 401);
    }

    private String bearerFor(User user) {
        return bearerFor(user, List.of("ROLE_USER"));
    }

    private String bearerFor(User user, List<String> authorities) {
        JwtClaimsSet claims = JwtClaimsSet.builder().subject(user.getEmail())
                .issuedAt(Instant.now().minusSeconds(1200)).expiresAt(Instant.now().plusSeconds(600))
                .claim("sessionId", user.getSessionId()).claim("authorities", authorities).build();
        return "Bearer " + encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SecurityUtils.JWT_ALGORITHM).build(), claims)).getTokenValue();
    }

    @Test
    void ownerUpdatesWrappedServerAndPreservesIdentityAndRelations() throws Exception {
        addMembership(server.getId());
        long memberCount = members.count();
        long serverCount = servers.count();
        entityManager.clear();
        Instant createdDate = servers.findById(server.getId()).orElseThrow().getCreatedDate();

        mvc.perform(put(detailUrl()).header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"  Nhóm mới  \",\"iconUrl\":\"https://example.test/new.png\",\"ownerId\":999}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.error").value(nullValue()))
                .andExpect(jsonPath("$.message").value("Cập nhật Server thành công"))
                .andExpect(jsonPath("$.data.id").value(server.getId()))
                .andExpect(jsonPath("$.data.name").value("Nhóm mới"))
                .andExpect(jsonPath("$.data.ownerId").value(reader.getId()));
        entityManager.flush();
        entityManager.clear();
        Server saved = servers.findById(server.getId()).orElseThrow();
        assertEquals("Nhóm mới", saved.getName());
        assertEquals("https://example.test/new.png", saved.getIconUrl());
        assertEquals(reader.getId(), saved.getOwnerId());
        assertEquals(createdDate, saved.getCreatedDate());
        assertEquals(reader.getEmail(), saved.getLastModifiedBy());
        assertEquals(serverCount, servers.count());
        assertEquals(memberCount, members.count());
    }

    @ParameterizedTest
    @ValueSource(strings = {"MEMBER", "ADMIN"})
    void serverMemberAndAdminCannotUpdate(String role) throws Exception {
        makeReaderOutsider();
        addMembership(server.getId());
        ServerMember membership = members.findAllByServerId(server.getId()).get(0);
        membership.setRole(role);
        members.saveAndFlush(membership);
        problem(updateRequest(bearer(), "{\"name\":\"Forbidden\",\"iconUrl\":null}"), 403);
        assertEquals("Detail server", servers.findById(server.getId()).orElseThrow().getName());
    }

    @Test
    void outsiderWithGlobalAdminAuthorityCannotUpdate() throws Exception {
        makeReaderOutsider();
        problem(updateRequest(bearer(reader.getSessionId(), Instant.now().plusSeconds(600), List.of("ROLE_ADMIN")),
                "{\"name\":\"Forbidden\"}"), 403);
    }

    @Test
    void updatingMissingServerIsNotFound() throws Exception {
        problem(mvc.perform(put("/api/v1/servers/" + Long.MAX_VALUE).header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Valid\"}")), 404);
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-number", "0", "-1"})
    void invalidUpdateIdIsBadRequest(String id) throws Exception {
        problem(mvc.perform(put("/api/v1/servers/" + id).header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Valid\"}")), 400);
    }

    @Test
    void updateRequiresValidJwtAndSession() throws Exception {
        String body = "{\"name\":\"Changed\"}";
        problem(mvc.perform(put(detailUrl()).contentType(MediaType.APPLICATION_JSON).content(body)), 401);
        problem(updateRequest("Bearer not-a-jwt", body), 401);
        problem(updateRequest(bearer(reader.getSessionId(), Instant.now().minusSeconds(600), List.of("ROLE_USER")), body), 401);
        problem(updateRequest(bearer("wrong-session", Instant.now().plusSeconds(600), List.of("ROLE_USER")), body), 401);
        assertEquals("Detail server", servers.findById(server.getId()).orElseThrow().getName());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void updateRejectsMissingOrInactiveAccount(boolean deleted) throws Exception {
        String token = bearer();
        if (deleted) users.delete(reader);
        else reader.setActivated(false);
        entityManager.flush();
        problem(updateRequest(token, "{\"name\":\"Changed\"}"), 401);
        assertEquals("Detail server", servers.findById(server.getId()).orElseThrow().getName());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"name\":null}", "{\"name\":\"\"}", "{\"name\":\"   \"}", "{\"name\":\"\u00a0\u2003\u202f\"}"})
    void updateRejectsBlankNameWithoutWriting(String body) throws Exception {
        problem(updateRequest(bearer(), body), 400);
        assertEquals("Detail server", servers.findById(server.getId()).orElseThrow().getName());
    }

    @Test
    void updateValidatesFieldBoundariesAndCanRemoveIcon() throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        problem(updateRequest(bearer(), mapper.writeValueAsString(java.util.Map.of("name", "x".repeat(101)))), 400);
        problem(updateRequest(bearer(), mapper.writeValueAsString(java.util.Map.of("name", "Valid", "iconUrl", "x".repeat(256)))), 400);
        updateRequest(bearer(), mapper.writeValueAsString(java.util.Map.of("name", "🙂".repeat(50), "iconUrl", "x".repeat(255))))
                .andExpect(status().isOk());
        entityManager.flush();
        entityManager.clear();
        Server saved = servers.findById(server.getId()).orElseThrow();
        assertEquals("🙂".repeat(50), saved.getName());
        assertEquals(255, saved.getIconUrl().length());
        for (String icon : List.of("null", "\"\"", "\"   \"")) {
            updateRequest(bearer(), "{\"name\":\"Valid\",\"iconUrl\":" + icon + "}")
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.iconUrl").value(nullValue()));
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {53, 254})
    void updatePreservesLongEmailAudit(int length) throws Exception {
        String email = length == 254
                ? "a".repeat(60) + "@" + "b".repeat(60) + "." + "c".repeat(60) + "." + "d".repeat(60) + "." + "e".repeat(5) + ".test"
                : "a".repeat(length - "@example.test".length()) + "@example.test";
        assertEquals(length, email.length());
        reader.setEmail(email);
        reader = users.saveAndFlush(reader);
        updateRequest(bearer(), "{\"name\":\"Updated audit\"}").andExpect(status().isOk());
        entityManager.flush();
        entityManager.clear();
        assertEquals(email, servers.findById(server.getId()).orElseThrow().getLastModifiedBy());
    }

    private ResultActions updateRequest(String token, String body) throws Exception {
        return mvc.perform(put(detailUrl()).header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private void readsDetail() throws Exception {
        mvc.perform(get(detailUrl()).header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.error").value(nullValue()))
                .andExpect(jsonPath("$.message").value("Call API success!"))
                .andExpect(jsonPath("$.data.id").value(server.getId()))
                .andExpect(jsonPath("$.data.name").value(server.getName()))
                .andExpect(jsonPath("$.data.iconUrl").value(server.getIconUrl()))
                .andExpect(jsonPath("$.data.ownerId").value(server.getOwnerId()))
                .andExpect(jsonPath("$.data.createdDate").isString())
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.createdBy").doesNotExist())
                .andExpect(jsonPath("$.data.channels").doesNotExist());
    }

    private ResultActions problem(ResultActions result, int expectedStatus) throws Exception {
        return result.andExpect(status().is(expectedStatus))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(expectedStatus))
                .andExpect(jsonPath("$.type").isString())
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }

    private void makeReaderOutsider() {
        server.setOwnerId(reader.getId() + 1000);
        servers.saveAndFlush(server);
    }

    private void addMembership(Long serverId) {
        ServerMember member = new ServerMember();
        member.setServerId(serverId);
        member.setUserId(reader.getId());
        member.setNickname("Reader");
        members.saveAndFlush(member);
    }

    private String detailUrl() {
        return "/api/v1/servers/" + server.getId();
    }

    private String membersUrl() {
        return "/api/v1/servers/" + server.getId() + "/members";
    }

    private String invitesUrl() {
        return "/api/v1/servers/" + server.getId() + "/invites";
    }

    private String bearer() {
        return bearer(reader.getSessionId(), Instant.now().plusSeconds(600), List.of("ROLE_USER"));
    }

    private String bearer(String sessionId, Instant expiry, List<String> authorities) {
        JwtClaimsSet claims = JwtClaimsSet.builder().subject(reader.getEmail())
                .issuedAt(Instant.now().minusSeconds(1200)).expiresAt(expiry)
                .claim("sessionId", sessionId).claim("authorities", authorities).build();
        return "Bearer " + encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SecurityUtils.JWT_ALGORITHM).build(), claims)).getTokenValue();
    }

    @Test
    void memberLeavesServerSuccessfully() throws Exception {
        User rawMember = new User();
        rawMember.setEmail("member-" + UUID.randomUUID().toString().substring(0, 8) + "@example.test");
        rawMember.setPassword("fixture-" + UUID.randomUUID());
        rawMember.setName("Server Member");
        rawMember.setActivated(true);
        rawMember.setSessionId(UUID.randomUUID().toString());
        User memberUser = users.saveAndFlush(rawMember);

        ServerMember sm = new ServerMember();
        sm.setServerId(server.getId());
        sm.setUserId(memberUser.getId());
        sm.setNickname("Member Nick");
        members.saveAndFlush(sm);

        assertTrue(members.existsByServerIdAndUserId(server.getId(), memberUser.getId()));

        mvc.perform(post("/api/v1/servers/" + server.getId() + "/leave")
                .header(HttpHeaders.AUTHORIZATION, bearerFor(memberUser)))
                .andExpect(status().isNoContent());

        entityManager.flush();
        entityManager.clear();
        assertFalse(members.existsByServerIdAndUserId(server.getId(), memberUser.getId()));
    }

    @Test
    void ownerCannotLeaveServer_Returns400() throws Exception {
        addMembership(server.getId());
        problem(mvc.perform(post("/api/v1/servers/" + server.getId() + "/leave")
                .header(HttpHeaders.AUTHORIZATION, bearer())), 400);
        assertTrue(members.existsByServerIdAndUserId(server.getId(), reader.getId()));
    }

    @Test
    void nonMemberCannotLeaveServer_Returns400() throws Exception {
        User rawOutsider = new User();
        rawOutsider.setEmail("outsider-" + UUID.randomUUID().toString().substring(0, 8) + "@example.test");
        rawOutsider.setPassword("fixture-" + UUID.randomUUID());
        rawOutsider.setName("Outsider User");
        rawOutsider.setActivated(true);
        rawOutsider.setSessionId(UUID.randomUUID().toString());
        User outsider = users.saveAndFlush(rawOutsider);

        problem(mvc.perform(post("/api/v1/servers/" + server.getId() + "/leave")
                .header(HttpHeaders.AUTHORIZATION, bearerFor(outsider))), 400);
    }

    @Test
    void missingServer_LeaveReturns404() throws Exception {
        problem(mvc.perform(post("/api/v1/servers/999999/leave")
                .header(HttpHeaders.AUTHORIZATION, bearer())), 404);
    }

    @Test
    void missingJwt_LeaveReturns401() throws Exception {
        problem(mvc.perform(post("/api/v1/servers/" + server.getId() + "/leave")), 401);
    }

    @Test
    void deleteServerMembersMeAlias_Success() throws Exception {
        User rawMember = new User();
        rawMember.setEmail("member2-" + UUID.randomUUID().toString().substring(0, 8) + "@example.test");
        rawMember.setPassword("fixture-" + UUID.randomUUID());
        rawMember.setName("Server Member 2");
        rawMember.setActivated(true);
        rawMember.setSessionId(UUID.randomUUID().toString());
        User memberUser = users.saveAndFlush(rawMember);

        ServerMember sm = new ServerMember();
        sm.setServerId(server.getId());
        sm.setUserId(memberUser.getId());
        sm.setNickname("Member 2");
        members.saveAndFlush(sm);

        assertTrue(members.existsByServerIdAndUserId(server.getId(), memberUser.getId()));

        mvc.perform(delete("/api/v1/servers/" + server.getId() + "/members/me")
                .header(HttpHeaders.AUTHORIZATION, bearerFor(memberUser)))
                .andExpect(status().isNoContent());

        entityManager.flush();
        entityManager.clear();
        assertFalse(members.existsByServerIdAndUserId(server.getId(), memberUser.getId()));
    }

    @Test
    void ownerRemovesMemberSuccessfully() throws Exception {
        User rawMember = new User();
        rawMember.setEmail("target-" + UUID.randomUUID().toString().substring(0, 8) + "@example.test");
        rawMember.setPassword("fixture-" + UUID.randomUUID());
        rawMember.setName("Target Member");
        rawMember.setActivated(true);
        rawMember.setSessionId(UUID.randomUUID().toString());
        User memberUser = users.saveAndFlush(rawMember);

        ServerMember sm = new ServerMember();
        sm.setServerId(server.getId());
        sm.setUserId(memberUser.getId());
        sm.setNickname("Target Nick");
        members.saveAndFlush(sm);

        assertTrue(members.existsByServerIdAndUserId(server.getId(), memberUser.getId()));

        mvc.perform(delete("/api/v1/servers/" + server.getId() + "/members/" + memberUser.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isNoContent());

        entityManager.flush();
        entityManager.clear();
        assertFalse(members.existsByServerIdAndUserId(server.getId(), memberUser.getId()));
    }

    @Test
    void nonOwnerCannotRemoveMember_Returns403() throws Exception {
        User rawMember1 = new User();
        rawMember1.setEmail("mem1-" + UUID.randomUUID().toString().substring(0, 8) + "@example.test");
        rawMember1.setPassword("fixture-" + UUID.randomUUID());
        rawMember1.setName("Member 1");
        rawMember1.setActivated(true);
        rawMember1.setSessionId(UUID.randomUUID().toString());
        User member1 = users.saveAndFlush(rawMember1);

        User rawMember2 = new User();
        rawMember2.setEmail("mem2-" + UUID.randomUUID().toString().substring(0, 8) + "@example.test");
        rawMember2.setPassword("fixture-" + UUID.randomUUID());
        rawMember2.setName("Member 2");
        rawMember2.setActivated(true);
        rawMember2.setSessionId(UUID.randomUUID().toString());
        User member2 = users.saveAndFlush(rawMember2);

        ServerMember sm1 = new ServerMember();
        sm1.setServerId(server.getId());
        sm1.setUserId(member1.getId());
        sm1.setNickname("Nick 1");
        members.saveAndFlush(sm1);

        ServerMember sm2 = new ServerMember();
        sm2.setServerId(server.getId());
        sm2.setUserId(member2.getId());
        sm2.setNickname("Nick 2");
        members.saveAndFlush(sm2);

        problem(mvc.perform(delete("/api/v1/servers/" + server.getId() + "/members/" + member2.getId())
                .header(HttpHeaders.AUTHORIZATION, bearerFor(member1))), 403);

        assertTrue(members.existsByServerIdAndUserId(server.getId(), member2.getId()));
    }

    @Test
    void outsiderCannotRemoveMember_Returns403() throws Exception {
        User rawTarget = new User();
        rawTarget.setEmail("target-" + UUID.randomUUID().toString().substring(0, 8) + "@example.test");
        rawTarget.setPassword("fixture-" + UUID.randomUUID());
        rawTarget.setName("Target Member");
        rawTarget.setActivated(true);
        rawTarget.setSessionId(UUID.randomUUID().toString());
        User target = users.saveAndFlush(rawTarget);

        ServerMember sm = new ServerMember();
        sm.setServerId(server.getId());
        sm.setUserId(target.getId());
        sm.setNickname("Target Nick");
        members.saveAndFlush(sm);

        User rawOutsider = new User();
        rawOutsider.setEmail("outsider-" + UUID.randomUUID().toString().substring(0, 8) + "@example.test");
        rawOutsider.setPassword("fixture-" + UUID.randomUUID());
        rawOutsider.setName("Outsider User");
        rawOutsider.setActivated(true);
        rawOutsider.setSessionId(UUID.randomUUID().toString());
        User outsider = users.saveAndFlush(rawOutsider);

        problem(mvc.perform(delete("/api/v1/servers/" + server.getId() + "/members/" + target.getId())
                .header(HttpHeaders.AUTHORIZATION, bearerFor(outsider))), 403);

        assertTrue(members.existsByServerIdAndUserId(server.getId(), target.getId()));
    }

    @Test
    void ownerCannotRemoveSelf_Returns400() throws Exception {
        addMembership(server.getId());

        problem(mvc.perform(delete("/api/v1/servers/" + server.getId() + "/members/" + reader.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer())), 400);

        assertTrue(members.existsByServerIdAndUserId(server.getId(), reader.getId()));
    }

    @Test
    void targetNotMember_RemoveMemberReturns404() throws Exception {
        User rawNonMember = new User();
        rawNonMember.setEmail("nonmember-" + UUID.randomUUID().toString().substring(0, 8) + "@example.test");
        rawNonMember.setPassword("fixture-" + UUID.randomUUID());
        rawNonMember.setName("Non Member");
        rawNonMember.setActivated(true);
        rawNonMember.setSessionId(UUID.randomUUID().toString());
        User nonMember = users.saveAndFlush(rawNonMember);

        problem(mvc.perform(delete("/api/v1/servers/" + server.getId() + "/members/" + nonMember.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer())), 404);
    }

    @Test
    void missingServer_RemoveMemberReturns404() throws Exception {
        problem(mvc.perform(delete("/api/v1/servers/999999/members/10")
                .header(HttpHeaders.AUTHORIZATION, bearer())), 404);
    }

    @Test
    void missingJwt_RemoveMemberReturns401() throws Exception {
        problem(mvc.perform(delete("/api/v1/servers/" + server.getId() + "/members/10")), 401);
    }

    @Test
    void joinServer_Direct_Success_ThenSecondJoinReturns400_NoDuplicateRows() throws Exception {
        User rawJoiner = new User();
        rawJoiner.setEmail("directjoin-" + UUID.randomUUID().toString().substring(0, 8) + "@example.test");
        rawJoiner.setPassword("fixture-" + UUID.randomUUID());
        rawJoiner.setName("Direct Joiner");
        rawJoiner.setActivated(true);
        rawJoiner.setSessionId(UUID.randomUUID().toString());
        User joiner = users.saveAndFlush(rawJoiner);

        // First join succeeds
        mvc.perform(post("/api/v1/servers/" + server.getId() + "/join")
                .header(HttpHeaders.AUTHORIZATION, bearerFor(joiner)))
                .andExpect(status().isNoContent());

        long count = members.findAllByServerId(server.getId()).stream()
                .filter(m -> m.getUserId().equals(joiner.getId()))
                .count();
        assertEquals(1, count);

        // Second join fails with 400
        problem(mvc.perform(post("/api/v1/servers/" + server.getId() + "/join")
                .header(HttpHeaders.AUTHORIZATION, bearerFor(joiner))), 400);

        long countAfter = members.findAllByServerId(server.getId()).stream()
                .filter(m -> m.getUserId().equals(joiner.getId()))
                .count();
        assertEquals(1, countAfter);
    }

    @Test
    void joinServerByInviteCode_SecondJoinReturns400_NoDuplicateRows() throws Exception {
        com.denhub.domain.Invite inv = new com.denhub.domain.Invite();
        inv.setServerId(server.getId());
        inv.setCode("repeatInvite1");
        inv.setCreatedBy(reader.getId());
        inv.setStatus("ACTIVE");
        invites.saveAndFlush(inv);

        User rawJoiner = new User();
        rawJoiner.setEmail("repeatjoin-" + UUID.randomUUID().toString().substring(0, 8) + "@example.test");
        rawJoiner.setPassword("fixture-" + UUID.randomUUID());
        rawJoiner.setName("Repeat Joiner");
        rawJoiner.setActivated(true);
        rawJoiner.setSessionId(UUID.randomUUID().toString());
        User joiner = users.saveAndFlush(rawJoiner);

        // First join succeeds
        mvc.perform(post("/api/v1/servers/join-invite/repeatInvite1")
                .header(HttpHeaders.AUTHORIZATION, bearerFor(joiner)))
                .andExpect(status().isOk());

        long count = members.findAllByServerId(server.getId()).stream()
                .filter(m -> m.getUserId().equals(joiner.getId()))
                .count();
        assertEquals(1, count);

        // Second join fails with 400
        problem(mvc.perform(post("/api/v1/servers/join-invite/repeatInvite1")
                .header(HttpHeaders.AUTHORIZATION, bearerFor(joiner))), 400);

        long countAfter = members.findAllByServerId(server.getId()).stream()
                .filter(m -> m.getUserId().equals(joiner.getId()))
                .count();
        assertEquals(1, countAfter);
    }

    @Test
    void rejoiningAfterLeave_SucceedsWithSingleRow() throws Exception {
        com.denhub.domain.Invite inv = new com.denhub.domain.Invite();
        inv.setServerId(server.getId());
        inv.setCode("rejoinLeave1");
        inv.setCreatedBy(reader.getId());
        inv.setStatus("ACTIVE");
        invites.saveAndFlush(inv);

        User rawJoiner = new User();
        rawJoiner.setEmail("rejoinleave-" + UUID.randomUUID().toString().substring(0, 8) + "@example.test");
        rawJoiner.setPassword("fixture-" + UUID.randomUUID());
        rawJoiner.setName("Rejoin Leave");
        rawJoiner.setActivated(true);
        rawJoiner.setSessionId(UUID.randomUUID().toString());
        User joiner = users.saveAndFlush(rawJoiner);

        // Join
        mvc.perform(post("/api/v1/servers/join-invite/rejoinLeave1")
                .header(HttpHeaders.AUTHORIZATION, bearerFor(joiner)))
                .andExpect(status().isOk());
        assertTrue(members.existsByServerIdAndUserId(server.getId(), joiner.getId()));

        // Leave
        mvc.perform(post("/api/v1/servers/" + server.getId() + "/leave")
                .header(HttpHeaders.AUTHORIZATION, bearerFor(joiner)))
                .andExpect(status().isNoContent());
        assertFalse(members.existsByServerIdAndUserId(server.getId(), joiner.getId()));

        // Rejoin
        mvc.perform(post("/api/v1/servers/join-invite/rejoinLeave1")
                .header(HttpHeaders.AUTHORIZATION, bearerFor(joiner)))
                .andExpect(status().isOk());

        long count = members.findAllByServerId(server.getId()).stream()
                .filter(m -> m.getUserId().equals(joiner.getId()))
                .count();
        assertEquals(1, count);
    }

    @Test
    void rejoiningAfterKick_SucceedsWithSingleRow() throws Exception {
        com.denhub.domain.Invite inv = new com.denhub.domain.Invite();
        inv.setServerId(server.getId());
        inv.setCode("rejoinKick1");
        inv.setCreatedBy(reader.getId());
        inv.setStatus("ACTIVE");
        invites.saveAndFlush(inv);

        User rawJoiner = new User();
        rawJoiner.setEmail("rejoinkick-" + UUID.randomUUID().toString().substring(0, 8) + "@example.test");
        rawJoiner.setPassword("fixture-" + UUID.randomUUID());
        rawJoiner.setName("Rejoin Kick");
        rawJoiner.setActivated(true);
        rawJoiner.setSessionId(UUID.randomUUID().toString());
        User joiner = users.saveAndFlush(rawJoiner);

        // Join
        mvc.perform(post("/api/v1/servers/join-invite/rejoinKick1")
                .header(HttpHeaders.AUTHORIZATION, bearerFor(joiner)))
                .andExpect(status().isOk());
        assertTrue(members.existsByServerIdAndUserId(server.getId(), joiner.getId()));

        // Owner removes joiner
        mvc.perform(delete("/api/v1/servers/" + server.getId() + "/members/" + joiner.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isNoContent());
        assertFalse(members.existsByServerIdAndUserId(server.getId(), joiner.getId()));

        // Rejoin
        mvc.perform(post("/api/v1/servers/join-invite/rejoinKick1")
                .header(HttpHeaders.AUTHORIZATION, bearerFor(joiner)))
                .andExpect(status().isOk());

        long count = members.findAllByServerId(server.getId()).stream()
                .filter(m -> m.getUserId().equals(joiner.getId()))
                .count();
        assertEquals(1, count);
    }

    @Test
    void deactivatedUser_CannotRemoveMember_Returns401() throws Exception {
        User rawTarget = new User();
        rawTarget.setEmail("target-deact-" + UUID.randomUUID().toString().substring(0, 8) + "@example.test");
        rawTarget.setPassword("fixture-" + UUID.randomUUID());
        rawTarget.setName("Target Deact");
        rawTarget.setActivated(true);
        rawTarget.setSessionId(UUID.randomUUID().toString());
        User target = users.saveAndFlush(rawTarget);

        ServerMember sm = new ServerMember();
        sm.setServerId(server.getId());
        sm.setUserId(target.getId());
        sm.setNickname("Target Deact");
        members.saveAndFlush(sm);
        assertTrue(members.existsByServerIdAndUserId(server.getId(), target.getId()));

        String ownerToken = bearer();
        reader.setActivated(false);
        users.saveAndFlush(reader);

        problem(mvc.perform(delete("/api/v1/servers/" + server.getId() + "/members/" + target.getId())
                .header(HttpHeaders.AUTHORIZATION, ownerToken)), 401);

        assertTrue(members.existsByServerIdAndUserId(server.getId(), target.getId()));
    }

    @Test
    void globalAdminAuthority_CannotRemoveMemberWhenNotServerOwner_Returns403() throws Exception {
        User rawTarget = new User();
        rawTarget.setEmail("target-adminauth-" + UUID.randomUUID().toString().substring(0, 8) + "@example.test");
        rawTarget.setPassword("fixture-" + UUID.randomUUID());
        rawTarget.setName("Target AdminAuth");
        rawTarget.setActivated(true);
        rawTarget.setSessionId(UUID.randomUUID().toString());
        User target = users.saveAndFlush(rawTarget);

        ServerMember sm = new ServerMember();
        sm.setServerId(server.getId());
        sm.setUserId(target.getId());
        sm.setNickname("Target AdminAuth");
        members.saveAndFlush(sm);

        User rawAdmin = new User();
        rawAdmin.setEmail("admin-outsider-" + UUID.randomUUID().toString().substring(0, 8) + "@example.test");
        rawAdmin.setPassword("fixture-" + UUID.randomUUID());
        rawAdmin.setName("Global Admin");
        rawAdmin.setActivated(true);
        rawAdmin.setSessionId(UUID.randomUUID().toString());
        User adminUser = users.saveAndFlush(rawAdmin);

        String adminToken = bearerFor(adminUser, List.of("ROLE_ADMIN"));

        problem(mvc.perform(delete("/api/v1/servers/" + server.getId() + "/members/" + target.getId())
                .header(HttpHeaders.AUTHORIZATION, adminToken)), 403);

        assertTrue(members.existsByServerIdAndUserId(server.getId(), target.getId()));
    }

    @Test
    void tamperedCallerPayload_DoesNotBypassAuthorization() throws Exception {
        User rawMember = new User();
        rawMember.setEmail("tamper-mem-" + UUID.randomUUID().toString().substring(0, 8) + "@example.test");
        rawMember.setPassword("fixture-" + UUID.randomUUID());
        rawMember.setName("Tamper Member");
        rawMember.setActivated(true);
        rawMember.setSessionId(UUID.randomUUID().toString());
        User memberUser = users.saveAndFlush(rawMember);

        User rawTarget = new User();
        rawTarget.setEmail("tamper-target-" + UUID.randomUUID().toString().substring(0, 8) + "@example.test");
        rawTarget.setPassword("fixture-" + UUID.randomUUID());
        rawTarget.setName("Tamper Target");
        rawTarget.setActivated(true);
        rawTarget.setSessionId(UUID.randomUUID().toString());
        User targetUser = users.saveAndFlush(rawTarget);

        ServerMember sm1 = new ServerMember();
        sm1.setServerId(server.getId());
        sm1.setUserId(memberUser.getId());
        sm1.setNickname("Tamper Member");
        members.saveAndFlush(sm1);

        ServerMember sm2 = new ServerMember();
        sm2.setServerId(server.getId());
        sm2.setUserId(targetUser.getId());
        sm2.setNickname("Tamper Target");
        members.saveAndFlush(sm2);

        // Attempt removal passing fake query params and JSON body claiming owner privilege
        problem(mvc.perform(delete("/api/v1/servers/" + server.getId() + "/members/" + targetUser.getId())
                .param("isOwner", "true")
                .param("role", "OWNER")
                .param("callerId", String.valueOf(reader.getId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ownerId\":" + reader.getId() + ",\"role\":\"OWNER\"}")
                .header(HttpHeaders.AUTHORIZATION, bearerFor(memberUser))), 403);

        assertTrue(members.existsByServerIdAndUserId(server.getId(), targetUser.getId()));
    }
    @Test
    void joinServer_Directly_NewMemberAppearsInMembersList() throws Exception {
        addMembership(server.getId());

        User joiner = new User();
        joiner.setEmail("joiner-" + UUID.randomUUID().toString().substring(0, 8) + "@example.test");
        joiner.setPassword("fixture-" + UUID.randomUUID());
        joiner.setName("Direct Joiner");
        joiner.setUsername("directjoiner");
        joiner.setAvatar("https://example.test/direct.png");
        joiner.setActivated(true);
        joiner.setSessionId(UUID.randomUUID().toString());
        joiner = users.saveAndFlush(joiner);
        String joinerToken = bearerFor(joiner);

        // Before joining: outsider cannot read members list (403 Forbidden)
        problem(mvc.perform(get(membersUrl()).header(HttpHeaders.AUTHORIZATION, joinerToken)), 403);

        // Joiner performs direct join
        mvc.perform(post("/api/v1/servers/" + server.getId() + "/join")
                .header(HttpHeaders.AUTHORIZATION, joinerToken))
                .andExpect(status().isNoContent());

        // After joining: joiner can read members list and appears as MEMBER
        mvc.perform(get(membersUrl()).header(HttpHeaders.AUTHORIZATION, joinerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].userId").value(reader.getId()))
                .andExpect(jsonPath("$.data[0].role").value("OWNER"))
                .andExpect(jsonPath("$.data[1].userId").value(joiner.getId()))
                .andExpect(jsonPath("$.data[1].username").value("directjoiner"))
                .andExpect(jsonPath("$.data[1].name").value("Direct Joiner"))
                .andExpect(jsonPath("$.data[1].avatar").value("https://example.test/direct.png"))
                .andExpect(jsonPath("$.data[1].role").value("MEMBER"));

        // Owner also sees the newly joined member
        mvc.perform(get(membersUrl()).header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[1].userId").value(joiner.getId()))
                .andExpect(jsonPath("$.data[1].role").value("MEMBER"));
    }
    @Test
    void joinServer_ByInviteCode_NewMemberAppearsInMembersList() throws Exception {
        addMembership(server.getId());

        String code = "INV_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        com.denhub.domain.Invite invite = new com.denhub.domain.Invite();
        invite.setServerId(server.getId());
        invite.setCode(code);
        invite.setStatus("ACTIVE");
        invite.setCreatedBy(reader.getId());
        invite.setCreatedAt(Instant.now());
        invite.setExpiresAt(Instant.now().plusSeconds(3600));
        invites.saveAndFlush(invite);

        User inviteJoiner = new User();
        inviteJoiner.setEmail("invjoiner-" + UUID.randomUUID().toString().substring(0, 8) + "@example.test");
        inviteJoiner.setPassword("fixture-" + UUID.randomUUID());
        inviteJoiner.setName("Invite Joiner");
        inviteJoiner.setUsername("invitejoiner");
        inviteJoiner.setAvatar("https://example.test/invitejoiner.png");
        inviteJoiner.setActivated(true);
        inviteJoiner.setSessionId(UUID.randomUUID().toString());
        inviteJoiner = users.saveAndFlush(inviteJoiner);
        String joinerToken = bearerFor(inviteJoiner);

        // Before joining: cannot access members list (403 Forbidden)
        problem(mvc.perform(get(membersUrl()).header(HttpHeaders.AUTHORIZATION, joinerToken)), 403);

        // Join via invite code endpoint
        mvc.perform(post("/api/v1/servers/join-invite/" + code)
                .header(HttpHeaders.AUTHORIZATION, joinerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.data.id").value(server.getId()));

        // After joining: joiner can read members list and appears in it
        mvc.perform(get(membersUrl()).header(HttpHeaders.AUTHORIZATION, joinerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[1].userId").value(inviteJoiner.getId()))
                .andExpect(jsonPath("$.data[1].username").value("invitejoiner"))
                .andExpect(jsonPath("$.data[1].name").value("Invite Joiner"))
                .andExpect(jsonPath("$.data[1].avatar").value("https://example.test/invitejoiner.png"))
                .andExpect(jsonPath("$.data[1].role").value("MEMBER"));

        // Owner also sees the invite joiner
        mvc.perform(get(membersUrl()).header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[1].userId").value(inviteJoiner.getId()))
                .andExpect(jsonPath("$.data[1].role").value("MEMBER"));
    }
    @Test
    void leaveServer_ThenServerDisappearsFromUserServerList() throws Exception {
        addMembership(server.getId());

        User memberUser = new User();
        memberUser.setEmail("leaver-" + UUID.randomUUID().toString().substring(0, 8) + "@example.test");
        memberUser.setPassword("fixture-" + UUID.randomUUID());
        memberUser.setName("Leaver Member");
        memberUser.setActivated(true);
        memberUser.setSessionId(UUID.randomUUID().toString());
        memberUser = users.saveAndFlush(memberUser);
        String memberToken = bearerFor(memberUser);

        // Member joins the server
        mvc.perform(post("/api/v1/servers/" + server.getId() + "/join")
                .header(HttpHeaders.AUTHORIZATION, memberToken))
                .andExpect(status().isNoContent());

        // 1. Verify server appears in member's server list
        mvc.perform(get("/api/v1/servers").header(HttpHeaders.AUTHORIZATION, memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(server.getId()))
                .andExpect(jsonPath("$.data[0].name").value("Detail server"));

        // 2. Member calls leave endpoint
        mvc.perform(post("/api/v1/servers/" + server.getId() + "/leave")
                .header(HttpHeaders.AUTHORIZATION, memberToken))
                .andExpect(status().isNoContent());

        // 3. Verify server disappears from member's server list
        mvc.perform(get("/api/v1/servers").header(HttpHeaders.AUTHORIZATION, memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));

        // 4. Verify member is now forbidden from accessing server detail
        problem(mvc.perform(get(detailUrl()).header(HttpHeaders.AUTHORIZATION, memberToken)), 403);

        // 5. Verify server still exists and appears in owner's server list
        mvc.perform(get("/api/v1/servers").header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(server.getId()));
    }
    @Test
    void leaveServer_ByMemberMeAlias_ThenServerDisappearsFromUserServerList() throws Exception {
        addMembership(server.getId());

        User memberUser = new User();
        memberUser.setEmail("alias-leaver-" + UUID.randomUUID().toString().substring(0, 8) + "@example.test");
        memberUser.setPassword("fixture-" + UUID.randomUUID());
        memberUser.setName("Alias Leaver Member");
        memberUser.setActivated(true);
        memberUser.setSessionId(UUID.randomUUID().toString());
        memberUser = users.saveAndFlush(memberUser);
        String memberToken = bearerFor(memberUser);

        // Member joins the server
        mvc.perform(post("/api/v1/servers/" + server.getId() + "/join")
                .header(HttpHeaders.AUTHORIZATION, memberToken))
                .andExpect(status().isNoContent());

        // Verify server is present in member's server list
        mvc.perform(get("/api/v1/servers").header(HttpHeaders.AUTHORIZATION, memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));

        // Member leaves using alias endpoint DELETE /api/v1/servers/{serverId}/members/me
        mvc.perform(delete("/api/v1/servers/" + server.getId() + "/members/me")
                .header(HttpHeaders.AUTHORIZATION, memberToken))
                .andExpect(status().isNoContent());

        // Verify server disappears from member's server list
        mvc.perform(get("/api/v1/servers").header(HttpHeaders.AUTHORIZATION, memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        // Member is now forbidden from accessing server detail
        problem(mvc.perform(get(detailUrl()).header(HttpHeaders.AUTHORIZATION, memberToken)), 403);
    }
}
