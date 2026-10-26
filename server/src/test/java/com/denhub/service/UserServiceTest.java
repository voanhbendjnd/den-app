package com.denhub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.denhub.domain.Authority;
import com.denhub.domain.User;
import com.denhub.repository.AuthorityRepository;
import com.denhub.repository.UserRepository;
import com.denhub.security.AuthoritiesConstants;
import com.denhub.service.dto.ResLoginDTO;
import com.denhub.service.dto.UserProfileResponseDTO;
import com.denhub.service.errors.BadRequestResourceException;
import com.denhub.web.rest.errors.BadRequestAlertException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
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

    @Mock
    private AuthService authService;

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

    @Test
    void processLogin_NewUser_GeneratesPasswordAndSavesUser() {
        String email = "newuser@example.com";
        when(userRepository.findOneWithAuthoritiesByEmail("newuser@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("hashed_random_pwd");

        Authority userAuth = new Authority();
        userAuth.setName(com.denhub.security.AuthoritiesConstants.USER);
        when(authorityRepository.findById(com.denhub.security.AuthoritiesConstants.USER)).thenReturn(Optional.of(userAuth));

        User savedUser = new User();
        savedUser.setId(201L);
        savedUser.setEmail(email);
        savedUser.setName("New User");
        savedUser.setPassword("hashed_random_pwd");
        savedUser.setActivated(true);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        ResLoginDTO expectedRes = new ResLoginDTO();
        expectedRes.setAccessToken("dummy-jwt-access-token");
        when(authService.generateResLoginDTO(savedUser)).thenReturn(expectedRes);

        ResLoginDTO res = userService.processLogin(email, "https://avatar.png", "New User", "newuser", "GITHUB");

        assertThat(res).isNotNull();
        assertThat(res.getAccessToken()).isEqualTo("dummy-jwt-access-token");
        verify(passwordEncoder).encode(any());
        verify(userRepository).save(any(User.class));
    }

    @Test
    void processLogin_ExistingUser_ReturnsExistingUserToken() {
        String email = TEST_EMAIL;
        when(userRepository.findOneWithAuthoritiesByEmail(email)).thenReturn(Optional.of(mockUser));

        ResLoginDTO expectedRes = new ResLoginDTO();
        expectedRes.setAccessToken("existing-user-jwt");
        when(authService.generateResLoginDTO(mockUser)).thenReturn(expectedRes);

        ResLoginDTO res = userService.processLogin(email, null, null, null, "GOOGLE");

        assertThat(res).isNotNull();
        assertThat(res.getAccessToken()).isEqualTo("existing-user-jwt");
    }

    @Test
    void loginWithSocialMedia_UnsupportedType_ThrowsBadRequest() {
        assertThrows(BadRequestResourceException.class, () ->
                userService.loginWithSocialMedia("dummy-token", "FACEBOOK")
        );
    }
}
