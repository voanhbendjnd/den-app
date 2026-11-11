package com.denhub.security;

import com.denhub.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessionManagerTest {
    @Mock UserRepository users;
    private SessionManager sessions;

    @BeforeEach
    void setUp() {
        sessions = new SessionManager(users);
    }

    @Test
    void normalizesEmailAndValidatesTheCurrentScalarSessionInsteadOfCachedUser() {
        when(users.findCurrentSessionIdByEmail("qa@example.test")).thenReturn(Optional.of("current-session"));
        assertTrue(sessions.isValidSessionIdByEmail("  QA@EXAMPLE.TEST  ", "current-session"));
        verify(users).findCurrentSessionIdByEmail("qa@example.test");
        verify(users, never()).findOneWithAuthoritiesByEmail(anyString());
    }

    @ParameterizedTest
    @MethodSource("incompleteIdentity")
    void incompleteIdentityIsRejectedWithoutQueryingDatabase(String email, String session) {
        assertFalse(sessions.isValidSessionIdByEmail(email, session));
        verifyNoInteractions(users);
    }

    static Stream<Arguments> incompleteIdentity() {
        return Stream.of(
                Arguments.of(null, "session"), Arguments.of("", "session"), Arguments.of(" \t", "session"),
                Arguments.of("qa@example.test", null), Arguments.of("qa@example.test", ""),
                Arguments.of("qa@example.test", " \t"), Arguments.of(null, null));
    }

    @Test
    void missingAccountOrNullStoredSessionIsRejectedSafely() {
        when(users.findCurrentSessionIdByEmail("qa@example.test")).thenReturn(Optional.empty());
        assertFalse(sessions.isValidSessionIdByEmail("qa@example.test", "session"));
    }

    @Test
    void staleOrModifiedOpaqueSessionDoesNotMatch() {
        when(users.findCurrentSessionIdByEmail("qa@example.test")).thenReturn(Optional.of("current-session"));
        assertFalse(sessions.isValidSessionIdByEmail("qa@example.test", "old-session"));
        assertFalse(sessions.isValidSessionIdByEmail("qa@example.test", " current-session "));
    }
}
