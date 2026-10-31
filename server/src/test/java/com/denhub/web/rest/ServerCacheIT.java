package com.denhub.web.rest;

import com.denhub.domain.Channel;
import com.denhub.domain.Server;
import com.denhub.domain.ServerMember;
import com.denhub.domain.User;
import com.denhub.domain.enums.ServerVisibility;
import com.denhub.repository.ChannelRepository;
import com.denhub.repository.ServerMemberRepository;
import com.denhub.repository.ServerRepository;
import com.denhub.repository.UserRepository;
import com.denhub.security.SecurityUtils;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Committed H2 transactions exercise real second-level cache behavior, unlike one rollback-only session. */
@SpringBootTest(properties = {
        "spring.jpa.properties.hibernate.cache.use_second_level_cache=true",
        "spring.jpa.properties.hibernate.generate_statistics=true"
})
@AutoConfigureMockMvc
class ServerCacheIT {
    @Autowired ServerRepository servers;
    @Autowired ServerMemberRepository members;
    @Autowired ChannelRepository channels;
    @Autowired UserRepository users;
    @Autowired EntityManagerFactory entityManagerFactory;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired JwtEncoder encoder;

    final List<Long> userIds = new ArrayList<>();
    TransactionTemplate transaction;
    Long serverId;
    User owner;
    User member;
    User outsider;

    @BeforeEach
    void setup() throws Exception {
        // These tests intentionally mutate isolated fixture data through JDBC, never the local SQL Server.
        try (var connection = jdbc.getDataSource().getConnection()) {
            assertTrue(connection.getMetaData().getURL().startsWith("jdbc:h2:"));
        }
        transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            owner = user();
            member = user();
            outsider = user();
            Server server = new Server();
            server.setName("Cache regression");
            server.setOwnerId(owner.getId());
            server.setVisibility(ServerVisibility.PUBLIC);
            serverId = servers.saveAndFlush(server).getId();
            ServerMember membership = new ServerMember();
            membership.setServerId(serverId);
            membership.setUserId(member.getId());
            membership.setNickname("Member");
            members.saveAndFlush(membership);
            Channel channel = new Channel();
            channel.setServerId(serverId);
            channel.setName("general");
            channel.setPosition(0);
            channels.saveAndFlush(channel);
        });
    }

    User user() {
        User user = new User();
        user.setEmail(UUID.randomUUID().toString().substring(0, 8) + "@cache.test");
        user.setPassword("fixture-" + UUID.randomUUID());
        user.setName("Cache fixture");
        user.setActivated(true);
        user.setSessionId(UUID.randomUUID().toString());
        user = users.saveAndFlush(user);
        userIds.add(user.getId());
        return user;
    }

    Server readInNewTransaction() {
        return transaction.execute(status -> servers.findById(serverId).orElseThrow());
    }

    @AfterEach
    void cleanup() {
        if (transaction == null) return;
        transaction.executeWithoutResult(status -> {
            if (serverId != null) {
                jdbc.update("DELETE FROM server_member WHERE server_id = ?", serverId);
                jdbc.update("DELETE FROM channels WHERE server_id = ?", serverId);
                jdbc.update("DELETE FROM servers WHERE id = ?", serverId);
            }
            for (Long id : userIds) jdbc.update("DELETE FROM users WHERE id = ?", id);
        });
        if (serverId != null) entityManagerFactory.getCache().evict(Server.class, serverId);
        for (Long id : userIds) entityManagerFactory.getCache().evict(User.class, id);
    }

    @ParameterizedTest
    @EnumSource(ServerVisibility.class)
    void separateTransactionsReadCurrentEnumFromDatabaseInsteadOfRetainingCachedState(ServerVisibility initial) {
        transaction.executeWithoutResult(status -> {
            Server server = servers.findById(serverId).orElseThrow();
            server.setVisibility(initial);
            servers.saveAndFlush(server);
        });
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        long statementsBefore = statistics.getPrepareStatementCount();
        long cacheHitsBefore = statistics.getSecondLevelCacheHitCount();
        assertEquals(initial, readInNewTransaction().getVisibility());

        // Bypass the ORM to leave any previously warmed Hibernate cache entry untouched.
        ServerVisibility current = initial == ServerVisibility.PUBLIC ? ServerVisibility.PRIVATE : ServerVisibility.PUBLIC;
        assertEquals(1, jdbc.update("UPDATE servers SET visibility = ? WHERE id = ?", current.name(), serverId));
        assertEquals(current, readInNewTransaction().getVisibility());
        assertTrue(statistics.getPrepareStatementCount() - statementsBefore >= 2,
                "Both independent reads must query the Server row");
        assertEquals(cacheHitsBefore, statistics.getSecondLevelCacheHitCount());
        assertFalse(entityManagerFactory.getCache().contains(Server.class, serverId));
    }

    @ParameterizedTest
    @EnumSource(ServerVisibility.class)
    void repeatedReadsPreserveOwnerMemberAndOutsiderAccessForBothVisibilityModes(ServerVisibility visibility) throws Exception {
        transaction.executeWithoutResult(status -> {
            Server server = servers.findById(serverId).orElseThrow();
            server.setVisibility(visibility);
            servers.saveAndFlush(server);
        });
        for (int repeat = 0; repeat < 2; repeat++) {
            for (User allowed : List.of(owner, member)) {
                mvc.perform(get("/api/v1/servers/" + serverId).header(HttpHeaders.AUTHORIZATION, token(allowed)))
                        .andExpect(status().isOk()).andExpect(jsonPath("$.data.visibility").value(visibility.name()));
                mvc.perform(get("/api/v1/servers/" + serverId + "/channels").header(HttpHeaders.AUTHORIZATION, token(allowed)))
                        .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].name").value("general"));
                mvc.perform(get("/api/v1/servers/" + serverId + "/members").header(HttpHeaders.AUTHORIZATION, token(allowed)))
                        .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].userId").value(member.getId()));
            }
            mvc.perform(get("/api/v1/servers/" + serverId).header(HttpHeaders.AUTHORIZATION, token(outsider)))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/api/v1/servers/" + serverId + "/members").header(HttpHeaders.AUTHORIZATION, token(outsider)))
                    .andExpect(status().isForbidden());
            // Preserve the existing Channel error contract; this fix does not change its HTTP status.
            mvc.perform(get("/api/v1/servers/" + serverId + "/channels").header(HttpHeaders.AUTHORIZATION, token(outsider)))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.forbidden"));
        }
    }

    String token(User user) {
        var claims = JwtClaimsSet.builder().subject(user.getEmail())
                .issuedAt(Instant.now().minusSeconds(1200)).expiresAt(Instant.now().plusSeconds(600))
                .claim("sessionId", user.getSessionId()).claim("authorities", List.of("ROLE_USER")).build();
        return "Bearer " + encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SecurityUtils.JWT_ALGORITHM).build(), claims)).getTokenValue();
    }
}
