package com.denhub.web.rest;

import com.denhub.domain.Authority;
import com.denhub.domain.User;
import com.denhub.repository.AuthorityRepository;
import com.denhub.repository.UserRepository;
import com.denhub.security.SecurityUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
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
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real signed JWT/filter/controller/login/JPA with the runtime User cache enabled. H2 fixtures roll back. */
@SpringBootTest(properties = {
        "spring.cache.type=simple",
        "spring.jpa.properties.hibernate.cache.use_second_level_cache=true"
})
@AutoConfigureMockMvc
@Transactional
class SessionSecurityIT {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired AuthorityRepository authorities;
    @Autowired PasswordEncoder passwords;
    @Autowired CacheManager caches;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtEncoder encoder;
    private User user;
    private String password;

    @BeforeEach
    void setUp() {
        clearCache();
        password = "fixture-" + UUID.randomUUID();
        user = new User();
        user.setEmail("session-" + UUID.randomUUID() + "@example.test");
        user.setPassword(passwords.encode(password));
        user.setName("Session QA");
        user.setActivated(true);
        Authority role = authorities.findById("ROLE_USER").orElseThrow();
        user.setAuthorities(Set.of(role));
        user = users.saveAndFlush(user);
        // Exactly the runtime failure: login loads a cache entry before the bulk session update.
        assertNull(users.findOneWithAuthoritiesByEmail(user.getEmail()).orElseThrow().getSessionId());
        assertNotNull(caches.getCache(UserRepository.USERS_BY_EMAIL_CACHE).get(user.getEmail()));
    }

    @AfterEach
    void cleanUp() {
        clearCache();
        SecurityContextHolder.clearContext();
    }

    @Test
    void loginWithWarmedNullSessionCacheCanReadAccountAndProfile() throws Exception {
        String token = login(null);
        assertNotNull(jdbc.queryForObject("SELECT session_id FROM users WHERE id = ?", String.class, user.getId()));
        assertNull(users.findOneWithAuthoritiesByEmail(user.getEmail()).orElseThrow().getSessionId(),
                "The regression must exercise the stale cached User instead of hiding it by evicting the cache");
        account(token).andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(user.getId()));
        mvc.perform(get("/api/v1/users/profile").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.email").value(user.getEmail()));
    }

    @Test
    void nextLoginImmediatelyRejectsPreviousTokenWithCacheStillWarm() throws Exception {
        String first = login(null);
        account(first).andExpect(status().isOk());
        String second = login(null);
        assertNotEquals(first, second);
        unauthorized(account(first));
        account(second).andExpect(status().isOk());
    }

    @Test
    void databaseNullSessionReturnsSafeUnauthorizedRatherThanAnException() throws Exception {
        unauthorized(account(signedToken("not-a-stored-session", true)));
    }

    @Test
    void databaseRevocationIsSeenEvenWithAnOlderSessionInTheUserCache() throws Exception {
        String token = login(null);
        account(token).andExpect(status().isOk());
        assertEquals(1, jdbc.update("UPDATE users SET session_id = NULL WHERE id = ?", user.getId()));
        unauthorized(account(token));
    }

    @Test
    void inactiveDatabaseAccountCannotKeepUsingAnActiveCachedUser() throws Exception {
        String token = login(null);
        account(token).andExpect(status().isOk());
        assertTrue(users.findOneWithAuthoritiesByEmail(user.getEmail()).orElseThrow().getActivated());
        assertEquals(1, jdbc.update("UPDATE users SET activated = FALSE WHERE id = ?", user.getId()));
        unauthorized(account(token));
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing-session", "blank-session", "wrong-session", "missing-subject"})
    void incompleteOrInvalidSignedAccessTokenIsUnauthorized(String scenario) throws Exception {
        login(null);
        String session = jdbc.queryForObject("SELECT session_id FROM users WHERE id = ?", String.class, user.getId());
        String claim = switch (scenario) {
            case "missing-session" -> null;
            case "blank-session" -> " ";
            case "wrong-session" -> "wrong-session";
            default -> session;
        };
        unauthorized(account(signedToken(claim, !scenario.equals("missing-subject"))));
    }

    @Test
    void malformedBearerStillAllowsPublicLoginButCannotReadProtectedResources() throws Exception {
        String token = login("Bearer not-a-signed-jwt");
        account(token).andExpect(status().isOk());
        unauthorized(account("not-a-signed-jwt"));
    }

    @Test
    void wrongPasswordRemainsUnauthorizedWithoutIssuingAToken() throws Exception {
        mvc.perform(post("/api/login").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("username", user.getEmail(), "password", "wrong-fixture-password"))))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.data.accessToken").doesNotExist());
    }

    private String login(String authorization) throws Exception {
        MockHttpServletRequestBuilder request = post("/api/login").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("username", user.getEmail(), "password", password)));
        if (authorization != null) request.header(HttpHeaders.AUTHORIZATION, authorization);
        String body = mvc.perform(request).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).path("data").path("accessToken").asText();
    }

    private ResultActions account(String token) throws Exception {
        return mvc.perform(get("/api/account").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private void unauthorized(ResultActions response) throws Exception {
        String body = response.andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andReturn().getResponse().getContentAsString();
        assertFalse(body.contains("NullPointerException"));
        assertFalse(body.contains("getSessionId"));
        assertFalse(body.contains("String.equals"));
    }

    private String signedToken(String session, boolean subject) {
        var claims = JwtClaimsSet.builder().issuedAt(Instant.now().minusSeconds(10))
                .expiresAt(Instant.now().plusSeconds(600)).claim("authorities", List.of("ROLE_USER"));
        if (subject) claims.subject(user.getEmail());
        if (session != null) claims.claim("sessionId", session);
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SecurityUtils.JWT_ALGORITHM).build(), claims.build())).getTokenValue();
    }

    private void clearCache() {
        var cache = caches.getCache(UserRepository.USERS_BY_EMAIL_CACHE);
        if (cache != null) cache.clear();
    }
}
