package com.denhub.web.rest;

import com.denhub.config.SmartAuthenticationEntryPoint;
import com.denhub.domain.Channel;
import com.denhub.domain.Message;
import com.denhub.domain.Server;
import com.denhub.domain.ServerMember;
import com.denhub.domain.User;
import com.denhub.repository.ChannelRepository;
import com.denhub.repository.MessageRepository;
import com.denhub.repository.ServerMemberRepository;
import com.denhub.repository.ServerRepository;
import com.denhub.repository.UserRepository;
import com.denhub.security.SecurityUtils;
import com.denhub.security.UserNotActivatedException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real filters, JWT signatures, services and JPA; each H2 fixture is rolled back. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MessageSecurityIT {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JwtEncoder encoder;
    @Autowired private UserRepository users;
    @Autowired private ServerRepository servers;
    @Autowired private ServerMemberRepository members;
    @Autowired private ChannelRepository channels;
    @Autowired private MessageRepository messages;
    @Autowired private EntityManager entityManager;
    @Autowired private SmartAuthenticationEntryPoint entryPoint;

    private User sender;
    private Channel channel;
    private ServerMember membership;
    private long originalCount;

    @BeforeEach
    void setUp() {
        sender = new User();
        sender.setEmail("msg-" + UUID.randomUUID() + "@example.test");
        sender.setPassword("test-fixture-" + UUID.randomUUID());
        sender.setName("Message sender");
        sender.setActivated(true);
        sender.setSessionId(UUID.randomUUID().toString());
        sender = users.saveAndFlush(sender);
        Server server = new Server();
        server.setName("Message test");
        server.setOwnerId(sender.getId());
        server = servers.saveAndFlush(server);
        channel = new Channel();
        channel.setName("general");
        channel.setServerId(server.getId());
        channel = channels.saveAndFlush(channel);
        membership = new ServerMember();
        membership.setServerId(server.getId());
        membership.setUserId(sender.getId());
        membership.setNickname("Sender");
        membership = members.saveAndFlush(membership);
        originalCount = messages.count();
    }

    @ParameterizedTest
    @MethodSource("validContents")
    void savesMessageFromJwtAndReturnsWrappedDto(String content) throws Exception {
        JsonNode body = mapper.readTree(mvc.perform(request(channel.getId(),
                        mapper.writeValueAsString(Map.of("content", content)))
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statusCode").value(201))
                .andExpect(jsonPath("$.error").value(nullValue()))
                .andExpect(jsonPath("$.message").value("Gửi tin nhắn thành công"))
                .andExpect(jsonPath("$.data.senderId").value(sender.getId()))
                .andExpect(jsonPath("$.data.channelId").value(channel.getId()))
                .andExpect(jsonPath("$.data.content").value(content))
                .andReturn().getResponse().getContentAsString());
        entityManager.flush();
        entityManager.clear();
        Message saved = messages.findById(body.path("data").path("id").asLong()).orElseThrow();
        assertEquals(originalCount + 1, messages.count());
        assertEquals(sender.getId(), saved.getAuthorId());
        assertEquals(channel.getId(), saved.getChannelId());
        assertEquals(content, saved.getContent());
        assertEquals(sender.getEmail(), saved.getCreatedBy());
        assertEquals(sender.getEmail(), saved.getLastModifiedBy());
        // Compare timestamps at database precision; H2 may round fractional nanoseconds.
        assertTrue(Math.abs(saved.getCreatedDate().toEpochMilli()
                - Instant.parse(body.path("data").path("createdAt").asText()).toEpochMilli()) <= 1);
        assertEquals(5, body.path("data").size());
    }

    static Stream<String> validContents() {
        return Stream.of("  Hello mọi người!\n😀\u00a0", "a".repeat(4000), "😀".repeat(2000));
    }

    @Test
    void preservesAuditEmailAt254CharacterBoundary() throws Exception {
        String email = UUID.randomUUID().toString().replace("-", "") + "a".repeat(32)
                + "@" + "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(61);
        assertEquals(254, email.length());
        sender.setEmail(email);
        users.saveAndFlush(sender);
        savesMessageFromJwtAndReturnsWrappedDto("Email dài vẫn gửi được");
    }

    @Test
    void clientCannotChooseSender() throws Exception {
        JsonNode body = mapper.readTree(mvc.perform(request(channel.getId(),
                        "{\"content\":\"Hello\",\"senderId\":-999,\"authorId\":-999}")
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.senderId").value(sender.getId()))
                .andReturn().getResponse().getContentAsString());
        entityManager.flush();
        entityManager.clear();
        assertEquals(sender.getId(), messages.findById(body.path("data").path("id").asLong())
                .orElseThrow().getAuthorId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "malformed", "signature", "expired", "session"})
    void rejectsInvalidAuthenticationWithoutSaving(String kind) throws Exception {
        MockHttpServletRequestBuilder request = request(channel.getId(), "{\"content\":\"Hello\"}");
        String token = token(sender.getSessionId(), Instant.now().plusSeconds(600), List.of("ROLE_USER"));
        switch (kind) {
            case "malformed" -> token = "not-a-jwt";
            case "signature" -> {
                int signatureStart = token.lastIndexOf('.') + 1;
                token = token.substring(0, signatureStart)
                        + (token.charAt(signatureStart) == 'A' ? 'B' : 'A') + token.substring(signatureStart + 1);
            }
            case "expired" -> token = token(sender.getSessionId(), Instant.now().minusSeconds(600), List.of());
            case "session" -> token = token("revoked-session", Instant.now().plusSeconds(600), List.of());
            default -> { }
        }
        if (!kind.equals("missing")) request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        ResultActions result = problem(mvc.perform(request), 401);
        // Missing credentials follow the existing Problem security advice;
        // rejected bearer tokens use SmartAuthenticationEntryPoint.
        if (!kind.equals("missing")) {
            result.andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                    .andExpect(jsonPath("$.redirectUrl").value("/login"));
        }
        assertNoMessageSaved();
    }

    @Test
    void rejectsInactiveUserWithoutSaving() throws Exception {
        sender.setActivated(false);
        users.saveAndFlush(sender);
        reject(channel.getId(), "{\"content\":\"Hello\"}", 401);
    }

    @Test
    void rejectsNonMemberEvenWithAdminClaimWithoutSaving() throws Exception {
        members.delete(membership);
        members.flush();
        problem(mvc.perform(request(channel.getId(), "{\"content\":\"Hello\"}")
                .header(HttpHeaders.AUTHORIZATION, "Bearer "
                        + token(sender.getSessionId(), Instant.now().plusSeconds(600), List.of("ROLE_ADMIN")))), 403);
        assertNoMessageSaved();
    }

    @Test
    void membershipInAnotherServerDoesNotGrantAccess() throws Exception {
        Server other = new Server();
        other.setName("Other group");
        other.setOwnerId(sender.getId());
        other = servers.saveAndFlush(other);
        Channel otherChannel = new Channel();
        otherChannel.setServerId(other.getId());
        otherChannel.setName("private");
        otherChannel = channels.saveAndFlush(otherChannel);
        reject(otherChannel.getId(), "{\"content\":\"Hello\"}", 403);
    }

    @Test
    void rejectsMissingChannelWithoutSaving() throws Exception {
        reject(Long.MAX_VALUE, "{\"content\":\"Hello\"}", 404);
    }

    @ParameterizedTest
    @MethodSource("invalidBodies")
    void rejectsInvalidContentWithoutSaving(String body) throws Exception {
        reject(channel.getId(), body, 400);
    }

    static Stream<String> invalidBodies() {
        return Stream.of("{}", "{\"content\":null}", "{\"content\":\"\"}",
                "{\"content\":\" \\t\\n\"}", "{\"content\":\"\u00a0\u2003\u202f\"}",
                "{\"content\":\"" + "a".repeat(4001) + "\"}",
                "{\"content\":\"" + "😀".repeat(2000) + "a\"}", "{", "");
    }

    @Test
    void rejectsInvalidChannelIdWithoutSaving() throws Exception {
        problem(mvc.perform(post("/api/channels/not-a-number/messages")
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"Hello\"}")
                .header(HttpHeaders.AUTHORIZATION, bearer())), 400);
        assertNoMessageSaved();
    }

    @Test
    void sharedEntryPointPreservesLegacyFieldsOnAnotherProtectedApi() throws Exception {
        problem(mvc.perform(get("/api/v1/servers")
                .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt")), 401)
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.publicApi").value(false))
                .andExpect(jsonPath("$.redirect").value(true))
                .andExpect(jsonPath("$.redirectUrl").value("/login"));
    }

    @Test
    void sharedEntryPointSerializesActivationErrorSafely() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        entryPoint.commence(new MockHttpServletRequest(), response,
                new BadCredentialsException("Authentication failed",
                        new UserNotActivatedException("Account \"not activated\"\nPlease activate")));
        assertEquals(401, response.getStatus());
        assertTrue(MediaType.APPLICATION_PROBLEM_JSON.isCompatibleWith(MediaType.parseMediaType(response.getContentType())));
        JsonNode body = mapper.readTree(response.getContentAsString());
        assertEquals(401, body.path("status").asInt());
        assertEquals("Unauthorized", body.path("title").asText());
        assertEquals("USER_NOT_ACTIVATED", body.path("code").asText());
        assertEquals("/verify-account", body.path("redirectUrl").asText());
        assertEquals("Account \"not activated\"\nPlease activate", body.path("message").asText());
        assertEquals(body.path("message"), body.path("detail"));
    }

    @Test
    void getMessagesSuccessReturnsSortedMessagesWhenMember() throws Exception {
        Message m1 = new Message();
        m1.setChannelId(channel.getId());
        m1.setAuthorId(sender.getId());
        m1.setContent("First message");
        m1 = messages.saveAndFlush(m1);

        Message m2 = new Message();
        m2.setChannelId(channel.getId());
        m2.setAuthorId(sender.getId());
        m2.setContent("Second message");
        m2 = messages.saveAndFlush(m2);

        Message m3 = new Message();
        m3.setChannelId(channel.getId());
        m3.setAuthorId(sender.getId());
        m3.setContent("Third message");
        m3 = messages.saveAndFlush(m3);

        mvc.perform(get("/api/channels/{channelId}/messages", channel.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.error").value(nullValue()))
                .andExpect(jsonPath("$.message").value("Lấy lịch sử tin nhắn thành công"))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items[0].id").value(m3.getId()))
                .andExpect(jsonPath("$.data.items[0].content").value("Third message"))
                .andExpect(jsonPath("$.data.items[1].id").value(m2.getId()))
                .andExpect(jsonPath("$.data.items[1].content").value("Second message"))
                .andExpect(jsonPath("$.data.items[2].id").value(m1.getId()))
                .andExpect(jsonPath("$.data.items[2].content").value("First message"))
                .andExpect(jsonPath("$.data.hasMore").value(false))
                .andExpect(jsonPath("$.data.nextCursor").value(nullValue()))
                .andExpect(jsonPath("$.data.limit").value(30));
    }

    @Test
    void getMessagesPaginationWithBeforeAndLimit() throws Exception {
        Message m1 = new Message();
        m1.setChannelId(channel.getId());
        m1.setAuthorId(sender.getId());
        m1.setContent("Message 1");
        m1 = messages.saveAndFlush(m1);

        Message m2 = new Message();
        m2.setChannelId(channel.getId());
        m2.setAuthorId(sender.getId());
        m2.setContent("Message 2");
        m2 = messages.saveAndFlush(m2);

        Message m3 = new Message();
        m3.setChannelId(channel.getId());
        m3.setAuthorId(sender.getId());
        m3.setContent("Message 3");
        m3 = messages.saveAndFlush(m3);

        mvc.perform(get("/api/channels/{channelId}/messages", channel.getId())
                        .param("before", String.valueOf(m3.getId()))
                        .param("limit", "1")
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(m2.getId()))
                .andExpect(jsonPath("$.data.items[0].content").value("Message 2"))
                .andExpect(jsonPath("$.data.hasMore").value(true))
                .andExpect(jsonPath("$.data.nextCursor").value(m2.getId()))
                .andExpect(jsonPath("$.data.limit").value(1));
    }

    @Test
    void getMessagesForbiddenWhenUserNotMember() throws Exception {
        User outsider = new User();
        outsider.setEmail("outsider-" + UUID.randomUUID() + "@example.test");
        outsider.setPassword("password");
        outsider.setName("Outsider");
        outsider.setActivated(true);
        outsider.setSessionId(UUID.randomUUID().toString());
        outsider = users.saveAndFlush(outsider);

        String outsiderToken = "Bearer " + tokenForUser(outsider);

        problem(mvc.perform(get("/api/channels/{channelId}/messages", channel.getId())
                        .header(HttpHeaders.AUTHORIZATION, outsiderToken)), 403);
    }

    @Test
    void getMessagesUnauthorizedWhenNoToken() throws Exception {
        mvc.perform(get("/api/channels/{channelId}/messages", channel.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getMessagesBadRequestWhenLimitOutOfRange() throws Exception {
        problem(mvc.perform(get("/api/channels/{channelId}/messages", channel.getId())
                        .param("limit", "150")
                        .header(HttpHeaders.AUTHORIZATION, bearer())), 400);
    }

    @Test
    void updateMessageSuccessWhenAuthor() throws Exception {
        Message msg = createMessage(sender, "Nội dung ban đầu");
        String updatePayload = mapper.writeValueAsString(Map.of("content", "Nội dung đã sửa"));

        mvc.perform(patch("/api/messages/{messageId}", msg.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.message").value("Chỉnh sửa tin nhắn thành công"))
                .andExpect(jsonPath("$.data.id").value(msg.getId()))
                .andExpect(jsonPath("$.data.content").value("Nội dung đã sửa"))
                .andExpect(jsonPath("$.data.updatedAt").isNotEmpty());

        entityManager.flush();
        entityManager.clear();
        Message updated = messages.findById(msg.getId()).orElseThrow();
        assertEquals("Nội dung đã sửa", updated.getContent());
        assertNotNull(updated.getLastModifiedDate());
    }

    @Test
    void updateMessageSuccessViaPutWhenAuthor() throws Exception {
        Message msg = createMessage(sender, "Nội dung ban đầu");
        String updatePayload = mapper.writeValueAsString(Map.of("content", "Nội dung PUT"));

        mvc.perform(put("/api/messages/{messageId}", msg.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.data.content").value("Nội dung PUT"));
    }

    @Test
    void updateMessageForbiddenWhenNotAuthor() throws Exception {
        Message msg = createMessage(sender, "Tin nhắn của sender");
        User otherMember = createAnotherMember();
        String otherToken = "Bearer " + tokenForUser(otherMember);

        String updatePayload = mapper.writeValueAsString(Map.of("content", "Hacker sửa tin nhắn"));

        problem(mvc.perform(patch("/api/messages/{messageId}", msg.getId())
                        .header(HttpHeaders.AUTHORIZATION, otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload)), 403);

        entityManager.flush();
        entityManager.clear();
        Message unchanged = messages.findById(msg.getId()).orElseThrow();
        assertEquals("Tin nhắn của sender", unchanged.getContent());
    }

    @Test
    void updateMessageForbiddenWhenServerOwnerTriesToEditMemberMessage() throws Exception {
        User member = createAnotherMember();
        Message memberMsg = createMessage(member, "Tin nhắn của member");

        String updatePayload = mapper.writeValueAsString(Map.of("content", "Server owner sửa"));

        problem(mvc.perform(patch("/api/messages/{messageId}", memberMsg.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload)), 403);

        entityManager.flush();
        entityManager.clear();
        Message unchanged = messages.findById(memberMsg.getId()).orElseThrow();
        assertEquals("Tin nhắn của member", unchanged.getContent());
    }

    @Test
    void updateMessageUnauthorizedWhenNoToken() throws Exception {
        Message msg = createMessage(sender, "Tin nhắn");
        String updatePayload = mapper.writeValueAsString(Map.of("content", "Sửa"));

        mvc.perform(patch("/api/messages/{messageId}", msg.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateMessageBadRequestWhenBlankContent() throws Exception {
        Message msg = createMessage(sender, "Tin nhắn gốc");
        String updatePayload = mapper.writeValueAsString(Map.of("content", "   "));

        problem(mvc.perform(patch("/api/messages/{messageId}", msg.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload)), 400);

        entityManager.flush();
        entityManager.clear();
        Message unchanged = messages.findById(msg.getId()).orElseThrow();
        assertEquals("Tin nhắn gốc", unchanged.getContent());
    }

    @Test
    void updateMessageNotFoundWhenMessageDoesNotExist() throws Exception {
        String updatePayload = mapper.writeValueAsString(Map.of("content", "Sửa tin không tồn tại"));

        problem(mvc.perform(patch("/api/messages/{messageId}", 999999L)
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload)), 404);
    }

    private Message createMessage(User author, String content) {
        Message msg = new Message();
        msg.setChannelId(channel.getId());
        msg.setSenderId(author.getId());
        msg.setContent(content);
        return messages.saveAndFlush(msg);
    }

    private User createAnotherMember() {
        User member = new User();
        member.setEmail("member-" + UUID.randomUUID() + "@example.test");
        member.setPassword("secret-" + UUID.randomUUID());
        member.setName("Other Member");
        member.setActivated(true);
        member.setSessionId(UUID.randomUUID().toString());
        member = users.saveAndFlush(member);

        ServerMember sm = new ServerMember();
        sm.setServerId(channel.getServerId());
        sm.setUserId(member.getId());
        sm.setNickname("Member2");
        members.saveAndFlush(sm);
        return member;
    }


    private void reject(Long channelId, String body, int status) throws Exception {
        problem(mvc.perform(request(channelId, body).header(HttpHeaders.AUTHORIZATION, bearer())), status);
        assertNoMessageSaved();
    }

    private ResultActions problem(ResultActions result, int expectedStatus) throws Exception {
        return result.andExpect(status().is(expectedStatus))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(expectedStatus))
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.type").isString())
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }

    private void assertNoMessageSaved() {
        entityManager.flush();
        entityManager.clear();
        assertEquals(originalCount, messages.count());
    }

    private MockHttpServletRequestBuilder request(Long channelId, String body) {
        return post("/api/channels/{channelId}/messages", channelId)
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private String bearer() {
        return "Bearer " + token(sender.getSessionId(), Instant.now().plusSeconds(600), List.of("ROLE_USER"));
    }

    private String token(String sessionId, Instant expiry, List<String> authorities) {
        JwtClaimsSet claims = JwtClaimsSet.builder().subject(sender.getEmail())
                .issuedAt(Instant.now().minusSeconds(1200)).expiresAt(expiry)
                .claim("sessionId", sessionId).claim("authorities", authorities).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SecurityUtils.JWT_ALGORITHM).build(), claims))
                .getTokenValue();
    }

    private String tokenForUser(User user) {
        JwtClaimsSet claims = JwtClaimsSet.builder().subject(user.getEmail())
                .issuedAt(Instant.now().minusSeconds(1200)).expiresAt(Instant.now().plusSeconds(600))
                .claim("sessionId", user.getSessionId()).claim("authorities", List.of("ROLE_USER")).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SecurityUtils.JWT_ALGORITHM).build(), claims))
                .getTokenValue();
    }
}
