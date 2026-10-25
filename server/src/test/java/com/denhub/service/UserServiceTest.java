package com.denhub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.denhub.domain.Authority;
import com.denhub.domain.User;
import com.denhub.repository.AuthorityRepository;
import com.denhub.repository.UserRepository;
import com.denhub.security.AuthoritiesConstants;
import com.denhub.service.dto.UserProfileResponseDTO;
import com.denhub.web.rest.errors.BadRequestAlertException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthorityRepository authorityRepository;

    @InjectMocks
    private UserService userService;

    private static final String TEST_EMAIL = "kyanh@denhub.com";
    private User mockUser;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();

        Authority userAuth = new Authority();
        userAuth.setName(AuthoritiesConstants.USER);

        mockUser = new User();
        mockUser.setId(100L);
        mockUser.setEmail(TEST_EMAIL);
        mockUser.setName("Kỳ Anh");
        mockUser.setUsername("kyanh_test");
        mockUser.setAvatar("https://denhub.com/avatar.png");
        mockUser.setActivated(true);
        mockUser.setAuthorities(Set.of(userAuth));
        mockUser.setCreatedDate(Instant.now());
    }

    private void authenticateAs(String email) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, "password")
        );
    }

    @Test
    void getUserProfile_Success() {
        authenticateAs(TEST_EMAIL);
        when(userRepository.findOneWithAuthoritiesByEmail(TEST_EMAIL)).thenReturn(Optional.of(mockUser));

        UserProfileResponseDTO profile = userService.getUserProfile();

        assertThat(profile).isNotNull();
        assertThat(profile.getId()).isEqualTo(100L);
        assertThat(profile.getEmail()).isEqualTo(TEST_EMAIL);
        assertThat(profile.getName()).isEqualTo("Kỳ Anh");
        assertThat(profile.getUsername()).isEqualTo("kyanh_test");
        assertThat(profile.getAvatar()).isEqualTo("https://denhub.com/avatar.png");
        assertThat(profile.getActivated()).isTrue();
        assertThat(profile.getCreatedDate()).isNotNull();
        assertThat(profile.getAuthorities()).contains(AuthoritiesConstants.USER);
    }

    @Test
    void getUserProfile_Fail_WhenNotLoggedIn() {
        SecurityContextHolder.clearContext();

        BadRequestAlertException ex = assertThrows(BadRequestAlertException.class, () -> userService.getUserProfile());

        assertThat(ex.getEntityName()).isEqualTo("userProfile");
        assertThat(ex.getErrorKey()).isEqualTo("notloggedin");
    }

    @Test
    void getUserProfile_Fail_WhenUserNotFound() {
        authenticateAs(TEST_EMAIL);
        when(userRepository.findOneWithAuthoritiesByEmail(TEST_EMAIL)).thenReturn(Optional.empty());

        BadRequestAlertException ex = assertThrows(BadRequestAlertException.class, () -> userService.getUserProfile());

        assertThat(ex.getEntityName()).isEqualTo("userProfile");
        assertThat(ex.getErrorKey()).isEqualTo("usernotfound");
    }
}
