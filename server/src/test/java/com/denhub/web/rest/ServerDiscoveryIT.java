package com.denhub.web.rest;

import com.denhub.domain.Server;
import com.denhub.domain.ServerMember;
import com.denhub.domain.User;
import com.denhub.domain.enums.ServerVisibility;
import com.denhub.repository.ServerMemberRepository;
import com.denhub.repository.ServerRepository;
import com.denhub.repository.UserRepository;
import com.denhub.security.SecurityUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ServerDiscoveryIT {
    @Autowired MockMvc mvc;
    @Autowired JwtEncoder encoder;
    @Autowired UserRepository users;
    @Autowired ServerRepository servers;
    @Autowired ServerMemberRepository members;
    @Autowired ObjectMapper mapper;
    User owner;
    User reader;

    @BeforeEach
    void setup() {
        owner = user();
        reader = user();
    }

    User user() {
        User user = new User();
        user.setEmail(UUID.randomUUID().toString().substring(0, 8) + "@example.test");
        user.setPassword("fixture-" + UUID.randomUUID());
        user.setName("Discovery fixture");
        user.setActivated(true);
        user.setSessionId(UUID.randomUUID().toString());
        return users.saveAndFlush(user);
    }

    Server server(String name, ServerVisibility visibility) {
        Server server = new Server();
        server.setName(name);
        server.setOwnerId(owner.getId());
        server.setVisibility(visibility);
        return servers.saveAndFlush(server);
    }

    String token(User user) {
        var claims = JwtClaimsSet.builder().subject(user.getEmail())
                .issuedAt(Instant.now().minusSeconds(1200)).expiresAt(Instant.now().plusSeconds(600))
                .claim("sessionId", user.getSessionId()).claim("authorities", List.of("ROLE_USER")).build();
        return "Bearer " + encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SecurityUtils.JWT_ALGORITHM).build(), claims)).getTokenValue();
    }

    @Test
    void discoveryOnlyReturnsPublicMetadataAndRanksExactPrefixThenContains() throws Exception {
        Server exact = server("Java", ServerVisibility.PUBLIC);
        Server prefix = server("Java Spring", ServerVisibility.PUBLIC);
        Server contains = server("Học Java", ServerVisibility.PUBLIC);
        server("Java secret", ServerVisibility.PRIVATE);
        var response = mvc.perform(get("/api/v1/servers/discover").param("q", "  JAVA  ")
                .header(HttpHeaders.AUTHORIZATION, token(reader)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.items[0].id").value(exact.getId()))
                .andExpect(jsonPath("$.data.items[1].id").value(prefix.getId()))
                .andExpect(jsonPath("$.data.items[2].id").value(contains.getId()))
                .andExpect(jsonPath("$.data.items[0].joined").value(false))
                .andReturn();
        var item = mapper.readTree(response.getResponse().getContentAsString()).path("data").path("items").get(0);
        assertEquals(4, item.size());
        assertFalse(item.has("ownerId"));
        mvc.perform(get("/api/v1/servers/" + exact.getId()).header(HttpHeaders.AUTHORIZATION, token(reader)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/servers/" + exact.getId() + "/members").header(HttpHeaders.AUTHORIZATION, token(reader)))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"", "Discover"})
    void paginationAndCountExcludeOwnedAndJoinedServersForTheAuthenticatedUser(String query) throws Exception {
        Server first = server("Discover One", ServerVisibility.PUBLIC);
        Server second = server("Discover Two", ServerVisibility.PUBLIC);
        Server joined = server("Discover Joined", ServerVisibility.PUBLIC);
        Server owned = server("Discover Owned", ServerVisibility.PUBLIC);
        owned.setOwnerId(reader.getId());
        servers.saveAndFlush(owned);
        server("Discover Secret", ServerVisibility.PRIVATE);
        ServerMember member = new ServerMember();
        member.setServerId(joined.getId());
        member.setUserId(reader.getId());
        member.setNickname("Reader");
        members.saveAndFlush(member);
        // Someone else's membership must not hide an eligible server from this reader.
        ServerMember otherMember = new ServerMember();
        otherMember.setServerId(second.getId());
        otherMember.setUserId(user().getId());
        otherMember.setNickname("Other");
        members.saveAndFlush(otherMember);
        mvc.perform(get("/api/v1/servers/discover").param("q", query).param("size", "1")
                .param("userId", owner.getId().toString()).header(HttpHeaders.AUTHORIZATION, token(reader)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.items[0].id").value(second.getId()))
                .andExpect(jsonPath("$.data.items[0].joined").value(false));
        mvc.perform(get("/api/v1/servers/discover").param("q", query).param("size", "1").param("page", "1")
                .header(HttpHeaders.AUTHORIZATION, token(reader)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].id").value(first.getId()))
                .andExpect(jsonPath("$.data.totalElements").value(2));
        // The owner of the other servers sees only the one owned by the reader.
        mvc.perform(get("/api/v1/servers/discover").param("q", query).header(HttpHeaders.AUTHORIZATION, token(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(owned.getId()));
        mvc.perform(get("/api/v1/servers/discover").param("page", "20").header(HttpHeaders.AUTHORIZATION, token(reader)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty());
    }

    @Test
    void joinedServerDisappearsAndBecomesDiscoverableAgainAfterLeaving() throws Exception {
        Server target = server("Lifecycle", ServerVisibility.PUBLIC);
        mvc.perform(get("/api/v1/servers/discover").param("q", "Lifecycle").header(HttpHeaders.AUTHORIZATION, token(reader)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1));
        mvc.perform(post("/api/v1/servers/" + target.getId() + "/join").header(HttpHeaders.AUTHORIZATION, token(reader)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/servers/discover").param("q", "Lifecycle").header(HttpHeaders.AUTHORIZATION, token(reader)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0))
                .andExpect(jsonPath("$.data.items").isEmpty());
        mvc.perform(post("/api/v1/servers/" + target.getId() + "/leave").header(HttpHeaders.AUTHORIZATION, token(reader)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/servers/discover").param("q", "Lifecycle").header(HttpHeaders.AUTHORIZATION, token(reader)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(target.getId()));
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"%", "_", "!", "[", "Nhóm"})
    void wildcardCharactersAreLiteralAndVietnameseNamesWork(String keyword) throws Exception {
        server("Other", ServerVisibility.PUBLIC);
        Server match = server("Server " + keyword, ServerVisibility.PUBLIC);
        mvc.perform(get("/api/v1/servers/discover").param("q", keyword).header(HttpHeaders.AUTHORIZATION, token(reader)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(match.getId()));
    }

    @ParameterizedTest
    @CsvSource({"-1,20", "0,0", "0,51"})
    void invalidPaginationIsRejected(int page, int size) throws Exception {
        mvc.perform(get("/api/v1/servers/discover").param("page", "" + page).param("size", "" + size)
                .header(HttpHeaders.AUTHORIZATION, token(reader))).andExpect(status().isBadRequest());
    }

    @Test
    void searchRequiresActiveAuthenticationAndValidQuery() throws Exception {
        mvc.perform(get("/api/v1/servers/discover")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/servers/discover").param("q", "x".repeat(101))
                .header(HttpHeaders.AUTHORIZATION, token(reader))).andExpect(status().isBadRequest());
        String jwt = token(reader);
        reader.setActivated(false);
        users.saveAndFlush(reader);
        mvc.perform(get("/api/v1/servers/discover").header(HttpHeaders.AUTHORIZATION, jwt))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void newServerDefaultsPrivateAndOwnerCanChoosePublic() throws Exception {
        mvc.perform(post("/api/v1/servers").header(HttpHeaders.AUTHORIZATION, token(owner))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Default private\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.visibility").value("PRIVATE"));
        mvc.perform(post("/api/v1/servers").header(HttpHeaders.AUTHORIZATION, token(owner))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Chosen public\",\"visibility\":\"PUBLIC\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.visibility").value("PUBLIC"));
        for (String value : List.of("null", "\"INVALID\"")) {
            mvc.perform(post("/api/v1/servers").header(HttpHeaders.AUTHORIZATION, token(owner))
                    .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Invalid\",\"visibility\":" + value + "}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void privateServerRejectsIdJoinButAcceptsInvite() throws Exception {
        Server target = server("Private", ServerVisibility.PRIVATE);
        long count = members.count();
        mvc.perform(post("/api/v1/servers/" + target.getId() + "/join").header(HttpHeaders.AUTHORIZATION, token(reader)))
                .andExpect(status().isForbidden());
        assertEquals(count, members.count());
        var invite = mvc.perform(post("/api/v1/servers/" + target.getId() + "/invites")
                .header(HttpHeaders.AUTHORIZATION, token(owner))).andExpect(status().isCreated()).andReturn();
        String code = mapper.readTree(invite.getResponse().getContentAsString()).path("data").path("code").asText();
        mvc.perform(post("/api/v1/servers/join-invite/" + code).header(HttpHeaders.AUTHORIZATION, token(reader)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.visibility").value("PRIVATE"));
        assertTrue(members.existsByServerIdAndUserId(target.getId(), reader.getId()));
    }

    @Test
    void ownerTransitionToPrivatePreservesMembersAndInvitesAndBlocksStaleJoin() throws Exception {
        Server target = server("Transition", ServerVisibility.PUBLIC);
        mvc.perform(post("/api/v1/servers/" + target.getId() + "/join").header(HttpHeaders.AUTHORIZATION, token(reader)))
                .andExpect(status().isNoContent());
        var invite = mvc.perform(post("/api/v1/servers/" + target.getId() + "/invites")
                .header(HttpHeaders.AUTHORIZATION, token(owner))).andExpect(status().isCreated()).andReturn();
        String code = mapper.readTree(invite.getResponse().getContentAsString()).path("data").path("code").asText();
        mvc.perform(put("/api/v1/servers/" + target.getId()).header(HttpHeaders.AUTHORIZATION, token(owner))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Transition\",\"visibility\":\"PRIVATE\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.visibility").value("PRIVATE"));
        mvc.perform(get("/api/v1/servers/discover").param("q", "Transition").header(HttpHeaders.AUTHORIZATION, token(reader)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
        mvc.perform(get("/api/v1/servers/" + target.getId()).header(HttpHeaders.AUTHORIZATION, token(reader)))
                .andExpect(status().isOk());
        User newcomer = user();
        mvc.perform(post("/api/v1/servers/" + target.getId() + "/join").header(HttpHeaders.AUTHORIZATION, token(newcomer)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/servers/join-invite/" + code).header(HttpHeaders.AUTHORIZATION, token(newcomer)))
                .andExpect(status().isOk());
    }

    @Test
    void onlyOwnerCanChangeVisibilityAndOmittedUpdatePreservesMode() throws Exception {
        Server target = server("Owned", ServerVisibility.PUBLIC);
        mvc.perform(put("/api/v1/servers/" + target.getId()).header(HttpHeaders.AUTHORIZATION, token(reader))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Owned\",\"visibility\":\"PRIVATE\"}"))
                .andExpect(status().isForbidden());
        assertEquals(ServerVisibility.PUBLIC, servers.findById(target.getId()).orElseThrow().getVisibility());
        mvc.perform(put("/api/v1/servers/" + target.getId()).header(HttpHeaders.AUTHORIZATION, token(owner))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Renamed\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.visibility").value("PUBLIC"));
    }
}
