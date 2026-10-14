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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
}
