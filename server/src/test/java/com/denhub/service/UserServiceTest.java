package com.denhub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.denhub.domain.Authority;
import com.denhub.domain.User;
import com.denhub.repository.AuthorityRepository;
import com.denhub.repository.UserRepository;
import com.denhub.security.AuthoritiesConstants;
import com.denhub.service.dto.ChangePasswordDTO;
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
    void updateUserProfile_Success() {
        authenticateAs(TEST_EMAIL);
        when(userRepository.findOneWithAuthoritiesByEmail(TEST_EMAIL)).thenReturn(Optional.of(mockUser));
        when(userRepository.existsByUsernameIgnoreCaseAndIdNot("kyanh_new", 100L)).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        com.denhub.service.dto.UpdateUserProfileDTO dto = com.denhub.service.dto.UpdateUserProfileDTO.builder()
                .name("Kỳ Anh Updated")
                .username("kyanh_new")
                .avatar("https://denhub.com/new_avatar.png")
                .build();

        UserProfileResponseDTO result = userService.updateUserProfile(dto);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Kỳ Anh Updated");
        assertThat(result.getUsername()).isEqualTo("kyanh_new");
        assertThat(result.getAvatar()).isEqualTo("https://denhub.com/new_avatar.png");
        verify(userRepository).save(mockUser);
    }

    @Test
    void updateUserProfile_Success_WhenUsernameUnchanged() {
        authenticateAs(TEST_EMAIL);
        when(userRepository.findOneWithAuthoritiesByEmail(TEST_EMAIL)).thenReturn(Optional.of(mockUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        com.denhub.service.dto.UpdateUserProfileDTO dto = com.denhub.service.dto.UpdateUserProfileDTO.builder()
                .name("Kỳ Anh Renamed")
                .username("kyanh_test") // Same username
                .build();

        UserProfileResponseDTO result = userService.updateUserProfile(dto);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Kỳ Anh Renamed");
        assertThat(result.getUsername()).isEqualTo("kyanh_test");
        verify(userRepository).save(mockUser);
    }

    @Test
    void updateUserProfile_Fail_WhenUsernameAlreadyExists() {
        authenticateAs(TEST_EMAIL);
        when(userRepository.findOneWithAuthoritiesByEmail(TEST_EMAIL)).thenReturn(Optional.of(mockUser));
        when(userRepository.existsByUsernameIgnoreCaseAndIdNot("taken_username", 100L)).thenReturn(true);

        com.denhub.service.dto.UpdateUserProfileDTO dto = com.denhub.service.dto.UpdateUserProfileDTO.builder()
                .username("taken_username")
                .build();

        BadRequestAlertException ex = assertThrows(BadRequestAlertException.class, () -> userService.updateUserProfile(dto));

        assertThat(ex.getEntityName()).isEqualTo("userProfile");
        assertThat(ex.getErrorKey()).isEqualTo("usernamealreadyexists");
    }

    @Test
    void updateUserProfile_Fail_WhenNotLoggedIn() {
        SecurityContextHolder.clearContext();

        com.denhub.service.dto.UpdateUserProfileDTO dto = com.denhub.service.dto.UpdateUserProfileDTO.builder()
                .name("New Name")
                .build();

        BadRequestAlertException ex = assertThrows(BadRequestAlertException.class, () -> userService.updateUserProfile(dto));

        assertThat(ex.getEntityName()).isEqualTo("userProfile");
        assertThat(ex.getErrorKey()).isEqualTo("notloggedin");
    }

    @Test
    void updateUserProfile_Fail_WhenUserNotFound() {
        authenticateAs(TEST_EMAIL);
        when(userRepository.findOneWithAuthoritiesByEmail(TEST_EMAIL)).thenReturn(Optional.empty());

        com.denhub.service.dto.UpdateUserProfileDTO dto = com.denhub.service.dto.UpdateUserProfileDTO.builder()
                .name("New Name")
                .build();

        BadRequestAlertException ex = assertThrows(BadRequestAlertException.class, () -> userService.updateUserProfile(dto));

        assertThat(ex.getEntityName()).isEqualTo("userProfile");
        assertThat(ex.getErrorKey()).isEqualTo("usernotfound");
    }

    @Test
    void changePassword_Success() {
        authenticateAs(TEST_EMAIL);
        mockUser.setPassword("hashed_old_password");
        when(userRepository.findOneWithAuthoritiesByEmail(TEST_EMAIL)).thenReturn(Optional.of(mockUser));
        when(passwordEncoder.matches("OldPassword123", "hashed_old_password")).thenReturn(true);
        when(passwordEncoder.matches("NewPassword456", "hashed_old_password")).thenReturn(false);
        when(passwordEncoder.encode("NewPassword456")).thenReturn("hashed_new_password");

        ChangePasswordDTO dto = new ChangePasswordDTO("OldPassword123", "NewPassword456");
        userService.changePassword(dto);

        assertThat(mockUser.getPassword()).isEqualTo("hashed_new_password");
        verify(userRepository).save(mockUser);
    }

    @Test
    void changePassword_Fail_WhenCurrentPasswordIncorrect() {
        authenticateAs(TEST_EMAIL);
        mockUser.setPassword("hashed_old_password");
        when(userRepository.findOneWithAuthoritiesByEmail(TEST_EMAIL)).thenReturn(Optional.of(mockUser));
        when(passwordEncoder.matches("WrongPassword", "hashed_old_password")).thenReturn(false);

        ChangePasswordDTO dto = new ChangePasswordDTO("WrongPassword", "NewPassword456");
        BadRequestAlertException ex = assertThrows(BadRequestAlertException.class, () -> userService.changePassword(dto));

        assertThat(ex.getEntityName()).isEqualTo("userProfile");
        assertThat(ex.getErrorKey()).isEqualTo("invalidcurrentpassword");
    }

    @Test
    void changePassword_Fail_WhenNewPasswordSameAsCurrent() {
        authenticateAs(TEST_EMAIL);
        mockUser.setPassword("hashed_old_password");
        when(userRepository.findOneWithAuthoritiesByEmail(TEST_EMAIL)).thenReturn(Optional.of(mockUser));
        when(passwordEncoder.matches("OldPassword123", "hashed_old_password")).thenReturn(true);
        when(passwordEncoder.matches("OldPassword123", "hashed_old_password")).thenReturn(true);

        ChangePasswordDTO dto = new ChangePasswordDTO("OldPassword123", "OldPassword123");
        BadRequestAlertException ex = assertThrows(BadRequestAlertException.class, () -> userService.changePassword(dto));

        assertThat(ex.getEntityName()).isEqualTo("userProfile");
        assertThat(ex.getErrorKey()).isEqualTo("samepassword");
    }

    @Test
    void changePassword_Fail_WhenNotLoggedIn() {
        SecurityContextHolder.clearContext();

        ChangePasswordDTO dto = new ChangePasswordDTO("OldPassword123", "NewPassword456");
        BadRequestAlertException ex = assertThrows(BadRequestAlertException.class, () -> userService.changePassword(dto));

        assertThat(ex.getEntityName()).isEqualTo("userProfile");
        assertThat(ex.getErrorKey()).isEqualTo("notloggedin");
    }

    @Test
    void changePassword_Fail_WhenUserNotFound() {
        authenticateAs(TEST_EMAIL);
        when(userRepository.findOneWithAuthoritiesByEmail(TEST_EMAIL)).thenReturn(Optional.empty());

        ChangePasswordDTO dto = new ChangePasswordDTO("OldPassword123", "NewPassword456");
        BadRequestAlertException ex = assertThrows(BadRequestAlertException.class, () -> userService.changePassword(dto));

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
