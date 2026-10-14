package com.denhub.service;

import com.denhub.repository.UserRepository;
import com.denhub.service.projection.UsernameProjection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SearchUserServiceTest {

    @Mock
    private UserRepository userRepository;

    private SearchUserService searchUserService;

    @BeforeEach
    void setUp() {
        searchUserService = new SearchUserService(userRepository);
        org.springframework.security.oauth2.jwt.Jwt jwt = mock(org.springframework.security.oauth2.jwt.Jwt.class);
        lenient().when(jwt.getClaim("user")).thenReturn(java.util.Map.of("id", 1L));
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
            new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(jwt, "token")
        );
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    @Test
    void searchByUsername_NullOrBlank_ReturnsEmptyList() {
        assertThat(searchUserService.searchByUsername(null)).isEmpty();
        assertThat(searchUserService.searchByUsername("")).isEmpty();
        assertThat(searchUserService.searchByUsername("   ")).isEmpty();
        verifyNoInteractions(userRepository);
    }

    @Test
    void searchByUsername_ValidUsername_ReturnsResults() {
        UsernameProjection projection = mock(UsernameProjection.class);
        lenient().when(projection.getId()).thenReturn(10L);
        lenient().when(projection.getUsername()).thenReturn("john_doe");

        when(userRepository.searchByUsername("john", 1L)).thenReturn(List.of(projection));

        List<UsernameProjection> results = searchUserService.searchByUsername("JOHN");

        assertThat(results).hasSize(1);
        assertThat(results.get(0)).isSameAs(projection);
        verify(userRepository).searchByUsername("john", 1L);
    }
}
