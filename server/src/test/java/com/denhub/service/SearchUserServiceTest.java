package com.denhub.service;

import com.denhub.repository.UserRepository;
import com.denhub.service.projection.UsernameProjection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SearchUserServiceTest {

    @Mock
    private UserRepository userRepository;

    private SearchUserService searchUserService;
    private MockedStatic<com.denhub.security.SecurityUtils> securityUtilsMockedStatic;

    @BeforeEach
    void setUp() {
        searchUserService = new SearchUserService(userRepository);
        securityUtilsMockedStatic = mockStatic(com.denhub.security.SecurityUtils.class);
    }

    @AfterEach
    void tearDown() {
        if (securityUtilsMockedStatic != null) {
            securityUtilsMockedStatic.close();
        }
    }

    @Test
    void searchByUsername_NullOrBlank_ReturnsEmptyList() {
        securityUtilsMockedStatic.when(com.denhub.security.SecurityUtils::getCurrentUserIdAtHolder).thenReturn(1L);

        assertThat(searchUserService.searchByUsername(null)).isEmpty();
        assertThat(searchUserService.searchByUsername("")).isEmpty();
        assertThat(searchUserService.searchByUsername("   ")).isEmpty();
        verifyNoInteractions(userRepository);
    }

    @Test
    void searchByUsername_ValidUsername_ReturnsResults() {
        securityUtilsMockedStatic.when(com.denhub.security.SecurityUtils::getCurrentUserIdAtHolder).thenReturn(1L);

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
