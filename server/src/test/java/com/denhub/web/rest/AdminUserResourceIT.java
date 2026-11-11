package com.denhub.web.rest;

import com.denhub.domain.*;
import com.denhub.repository.*;
import com.denhub.security.AuthoritiesConstants;
import com.denhub.security.SecurityUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminUserResourceIT {

    @Autowired MockMvc mvc;
    @Autowired JwtEncoder encoder;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository users;
    @Autowired AuthorityRepository authorities;
    @Autowired ServerRepository servers;
    @Autowired ServerMemberRepository members;
    @Autowired ChannelRepository channels;
    @Autowired MessageRepository messages;
    @Autowired EntityManager em;

    User admin;
    User normalUser;

    @BeforeEach
    void setup() {
        Authority adminRole = authorities.findById(AuthoritiesConstants.ADMIN).orElseGet(() -> {
            Authority a = new Authority();
            a.setName(AuthoritiesConstants.ADMIN);
            return authorities.saveAndFlush(a);
        });
        Authority userRole = authorities.findById(AuthoritiesConstants.USER).orElseGet(() -> {
            Authority a = new Authority();
            a.setName(AuthoritiesConstants.USER);
            return authorities.saveAndFlush(a);
        });

        String suffix = UUID.randomUUID().toString().substring(0, 8);
        admin = new User();
        admin.setEmail("adm-" + suffix + "@denhub.test");
        admin.setUsername("adm_" + suffix);
        admin.setName("System Admin");
        admin.setPassword(UUID.randomUUID().toString());
        admin.setActivated(true);
        admin.setSessionId(UUID.randomUUID().toString());
        admin.setAuthorities(new java.util.HashSet<>(Set.of(adminRole, userRole)));
        admin = users.saveAndFlush(admin);

        normalUser = new User();
        normalUser.setEmail("usr-" + suffix + "@denhub.test");
        normalUser.setUsername("usr_" + suffix);
        normalUser.setName("Normal User");
        normalUser.setPassword(UUID.randomUUID().toString());
        normalUser.setActivated(true);
        normalUser.setSessionId(UUID.randomUUID().toString());
        normalUser.setAuthorities(new java.util.HashSet<>(Set.of(userRole)));
        normalUser = users.saveAndFlush(normalUser);
    }

    @Test
    void nonAdminIsForbiddenFromAllAdminEndpoints() throws Exception {
        String userToken = tokenFor(normalUser, List.of(AuthoritiesConstants.USER));

        mvc.perform(get("/api/v1/admin/users").header(HttpHeaders.AUTHORIZATION, userToken))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        mvc.perform(delete("/api/v1/admin/users/" + admin.getId()).header(HttpHeaders.AUTHORIZATION, userToken))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void adminCanCrudAccountAndRestoreSoftDeletedAccount() throws Exception {
        String adminToken = tokenFor(admin, List.of(AuthoritiesConstants.ADMIN, AuthoritiesConstants.USER));

        // 1. Create User
        String uniqueEmail = "created-" + UUID.randomUUID().toString().substring(0, 6) + "@denhub.test";
        String uniqueUsername = "created_" + UUID.randomUUID().toString().substring(0, 6);
        String createBody = objectMapper.writeValueAsString(Map.of(
                "email", uniqueEmail,
                "username", uniqueUsername,
                "name", "Created By Admin",
                "password", "password123",
                "role", "ROLE_USER",
                "activated", true
        ));

        String createResp = mvc.perform(post("/api/v1/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statusCode").value(201))
                .andExpect(jsonPath("$.data.email").value(uniqueEmail))
                .andExpect(jsonPath("$.data.username").value(uniqueUsername))
                .andExpect(jsonPath("$.data.activated").value(true))
                .andReturn().getResponse().getContentAsString();

        Long createdId = objectMapper.readTree(createResp).path("data").path("id").asLong();

        // 2. List & Search Users (also verify default list without query params)
        mvc.perform(get("/api/v1/admin/users")
                        .param("page", "0")
                        .param("size", "10")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.data.items").isArray());

        mvc.perform(get("/api/v1/admin/users")
                        .param("search", uniqueUsername)
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(createdId));

        // 3. Update User
        String updateBody = objectMapper.writeValueAsString(Map.of(
                "name", "Updated Name",
                "role", "ROLE_ADMIN"
        ));
        mvc.perform(put("/api/v1/admin/users/" + createdId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Updated Name"))
                .andExpect(jsonPath("$.data.role").value("ROLE_ADMIN"));

        // 4. Soft Delete User (without owned servers)
        mvc.perform(delete("/api/v1/admin/users/" + createdId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.activated").value(false))
                .andExpect(jsonPath("$.data.transferredServerCount").value(0))
                .andExpect(jsonPath("$.data.deletedEmptyServerCount").value(0));

        // 5. Restore User
        mvc.perform(patch("/api/v1/admin/users/" + createdId + "/restore")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activated").value(true));
    }

    @Test
    void softDeletingServerOwnerAutomaticallyTransfersToEarliestJoinedActiveMemberAndDeletesEmptyServer() throws Exception {
        String adminToken = tokenFor(admin, List.of(AuthoritiesConstants.ADMIN, AuthoritiesConstants.USER));

        User secondMember = new User();
        secondMember.setEmail("second-" + UUID.randomUUID() + "@denhub.test");
        secondMember.setUsername("second_" + UUID.randomUUID().toString().substring(0, 6));
        secondMember.setName("Second Member");
        secondMember.setPassword(UUID.randomUUID().toString());
        secondMember.setActivated(true);
        secondMember = users.saveAndFlush(secondMember);

        User thirdMember = new User();
        thirdMember.setEmail("third-" + UUID.randomUUID() + "@denhub.test");
        thirdMember.setUsername("third_" + UUID.randomUUID().toString().substring(0, 6));
        thirdMember.setName("Third Member");
        thirdMember.setPassword(UUID.randomUUID().toString());
        thirdMember.setActivated(true);
        thirdMember = users.saveAndFlush(thirdMember);

        // Server 1: normalUser is Owner; secondMember joined first, thirdMember joined later
        Server populatedServer = new Server();
        populatedServer.setName("Populated Server");
        populatedServer.setOwnerId(normalUser.getId());
        populatedServer = servers.saveAndFlush(populatedServer);

        Channel popGeneral = new Channel();
        popGeneral.setServerId(populatedServer.getId());
        popGeneral.setName("general");
        popGeneral.setPosition(0);
        channels.saveAndFlush(popGeneral);

        ServerMember m1 = new ServerMember();
        m1.setServerId(populatedServer.getId());
        m1.setUserId(normalUser.getId());
        m1.setNickname("Owner");
        members.saveAndFlush(m1);

        ServerMember m2 = new ServerMember();
        m2.setServerId(populatedServer.getId());
        m2.setUserId(secondMember.getId());
        m2.setNickname("Second");
        members.saveAndFlush(m2);

        ServerMember m3 = new ServerMember();
        m3.setServerId(populatedServer.getId());
        m3.setUserId(thirdMember.getId());
        m3.setNickname("Third");
        members.saveAndFlush(m3);

        // Server 2: normalUser is Owner and the ONLY member -> should be auto-deleted
        Server soloServer = new Server();
        soloServer.setName("Solo Empty Server");
        soloServer.setOwnerId(normalUser.getId());
        soloServer = servers.saveAndFlush(soloServer);

        Channel soloGeneral = new Channel();
        soloGeneral.setServerId(soloServer.getId());
        soloGeneral.setName("general");
        soloGeneral.setPosition(0);
        soloGeneral = channels.saveAndFlush(soloGeneral);

        ServerMember mSolo = new ServerMember();
        mSolo.setServerId(soloServer.getId());
        mSolo.setUserId(normalUser.getId());
        mSolo.setNickname("SoloOwner");
        members.saveAndFlush(mSolo);

        // Preview ownership before soft-delete
        mvc.perform(get("/api/v1/admin/users/" + normalUser.getId() + "/ownership-preview")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalOwnedServers").value(2))
                .andExpect(jsonPath("$.data.transferableCount").value(1))
                .andExpect(jsonPath("$.data.autoDeleteCount").value(1))
                .andExpect(jsonPath("$.data.servers[0].action").value("TRANSFER_OWNERSHIP"))
                .andExpect(jsonPath("$.data.servers[0].nextOwnerId").value(secondMember.getId()))
                .andExpect(jsonPath("$.data.servers[1].action").value("DELETE_EMPTY_SERVER"));

        // Execute soft-delete
        mvc.perform(delete("/api/v1/admin/users/" + normalUser.getId())
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.activated").value(false))
                .andExpect(jsonPath("$.data.transferredServerCount").value(1))
                .andExpect(jsonPath("$.data.deletedEmptyServerCount").value(1));

        em.flush();
        em.clear();

        // Check normalUser is deactivated and session invalidated
        User reloadedUser = users.findById(normalUser.getId()).orElseThrow();
        assertFalse(reloadedUser.getActivated());
        assertNull(reloadedUser.getSessionId());

        // Check populatedServer now belongs to secondMember and normalUser was removed from members
        Server reloadedPopulated = servers.findById(populatedServer.getId()).orElseThrow();
        assertEquals(secondMember.getId(), reloadedPopulated.getOwnerId());
        assertFalse(members.existsByServerIdAndUserId(populatedServer.getId(), normalUser.getId()));
        assertTrue(members.existsByServerIdAndUserId(populatedServer.getId(), secondMember.getId()));

        // Check soloServer and its channels/members were completely deleted
        assertFalse(servers.existsById(soloServer.getId()));
        assertFalse(channels.existsById(soloGeneral.getId()));
        assertTrue(members.findAllByServerId(soloServer.getId()).isEmpty());
    }

    private String tokenFor(User user, List<String> roleList) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(user.getEmail())
                .issuedAt(Instant.now().minusSeconds(60))
                .expiresAt(Instant.now().plusSeconds(600))
                .claim("sessionId", user.getSessionId())
                .claim("authorities", roleList)
                .build();
        return "Bearer " + encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SecurityUtils.JWT_ALGORITHM).build(), claims)).getTokenValue();
    }
}
