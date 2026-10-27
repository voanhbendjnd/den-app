package com.denhub.web.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.denhub.service.UserService;
import com.denhub.service.dto.UpdateUserProfileDTO;
import com.denhub.service.dto.UserProfileResponseDTO;
import com.denhub.util.ResFormatResponse;
import com.denhub.web.rest.errors.BadRequestAlertException;
import com.denhub.web.rest.errors.ExceptionTranslator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.Set;

@ExtendWith(MockitoExtension.class)
class UserProfileResourceTest {

    private MockMvc mockMvc;

    @Mock
    private UserService userService;

    @InjectMocks
    private UserProfileResource userProfileResource;

    private ObjectMapper objectMapper;
    private UserProfileResponseDTO mockProfile;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        mockMvc = MockMvcBuilders.standaloneSetup(userProfileResource)
                .setControllerAdvice(new ResFormatResponse(), new ExceptionTranslator())
                .build();

        mockProfile = UserProfileResponseDTO.builder()
                .id(1L)
                .email("kyanh@denhub.com")
                .name("Kỳ Anh")
                .username("kyanh_ce")
                .avatar("https://denhub.com/avatar.png")
                .activated(true)
                .createdDate(Instant.parse("2026-10-10T10:00:00Z"))
                .authorities(Set.of("ROLE_USER"))
                .build();
    }

    @Test
    void getUserProfile_Success() throws Exception {
        when(userService.getUserProfile()).thenReturn(mockProfile);

        mockMvc.perform(get("/api/v1/users/profile")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.email").value("kyanh@denhub.com"))
                .andExpect(jsonPath("$.data.name").value("Kỳ Anh"))
                .andExpect(jsonPath("$.data.username").value("kyanh_ce"))
                .andExpect(jsonPath("$.data.avatar").value("https://denhub.com/avatar.png"))
                .andExpect(jsonPath("$.data.activated").value(true))
                .andExpect(jsonPath("$.data.authorities[0]").value("ROLE_USER"));
    }

    @Test
    void getUserProfile_AliasMe_Success() throws Exception {
        when(userService.getUserProfile()).thenReturn(mockProfile);

        mockMvc.perform(get("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.email").value("kyanh@denhub.com"))
                .andExpect(jsonPath("$.data.name").value("Kỳ Anh"));
    }

    @Test
    void getUserProfile_Fail_WhenNotLoggedIn() throws Exception {
        when(userService.getUserProfile()).thenThrow(
                new BadRequestAlertException("User not found in security context", "userProfile", "notloggedin")
        );

        mockMvc.perform(get("/api/v1/users/profile")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.entityName").value("userProfile"))
                .andExpect(jsonPath("$.errorKey").value("notloggedin"));
    }

    @Test
    void getUserProfile_Fail_WhenUserNotFound() throws Exception {
        when(userService.getUserProfile()).thenThrow(
                new BadRequestAlertException("User not found in database", "userProfile", "usernotfound")
        );

        mockMvc.perform(get("/api/v1/users/profile")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.entityName").value("userProfile"))
                .andExpect(jsonPath("$.errorKey").value("usernotfound"));
    }

    @Test
    void updateUserProfile_Success() throws Exception {
        UpdateUserProfileDTO requestDto = UpdateUserProfileDTO.builder()
                .name("Kỳ Anh Updated")
                .username("kyanh_new")
                .avatar("https://denhub.com/new-avatar.png")
                .build();

        UserProfileResponseDTO updatedProfile = UserProfileResponseDTO.builder()
                .id(1L)
                .email("kyanh@denhub.com")
                .name("Kỳ Anh Updated")
                .username("kyanh_new")
                .avatar("https://denhub.com/new-avatar.png")
                .activated(true)
                .build();

        when(userService.updateUserProfile(any(UpdateUserProfileDTO.class))).thenReturn(updatedProfile);

        mockMvc.perform(put("/api/v1/users/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.data.name").value("Kỳ Anh Updated"))
                .andExpect(jsonPath("$.data.username").value("kyanh_new"))
                .andExpect(jsonPath("$.data.avatar").value("https://denhub.com/new-avatar.png"));
    }

    @Test
    void updateUserProfile_AliasMe_Success() throws Exception {
        UpdateUserProfileDTO requestDto = UpdateUserProfileDTO.builder()
                .name("Kỳ Anh Updated")
                .username("kyanh_new")
                .build();

        UserProfileResponseDTO updatedProfile = UserProfileResponseDTO.builder()
                .id(1L)
                .email("kyanh@denhub.com")
                .name("Kỳ Anh Updated")
                .username("kyanh_new")
                .avatar("https://denhub.com/avatar.png")
                .activated(true)
                .build();

        when(userService.updateUserProfile(any(UpdateUserProfileDTO.class))).thenReturn(updatedProfile);

        mockMvc.perform(put("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.data.name").value("Kỳ Anh Updated"));
    }

    @Test
    void updateUserProfile_Fail_WhenValidationFails() throws Exception {
        // Username with invalid length (< 3 characters)
        UpdateUserProfileDTO invalidDto = UpdateUserProfileDTO.builder()
                .username("ab")
                .build();

        mockMvc.perform(put("/api/v1/users/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidDto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateUserProfile_Fail_WhenUsernameAlreadyExists() throws Exception {
        UpdateUserProfileDTO requestDto = UpdateUserProfileDTO.builder()
                .username("taken_username")
                .build();

        when(userService.updateUserProfile(any(UpdateUserProfileDTO.class))).thenThrow(
                new BadRequestAlertException("Username already in use", "userProfile", "usernamealreadyexists")
        );

        mockMvc.perform(put("/api/v1/users/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.entityName").value("userProfile"))
                .andExpect(jsonPath("$.errorKey").value("usernamealreadyexists"));
    }

    @Test
    void updateUserProfile_Fail_WhenNotLoggedIn() throws Exception {
        UpdateUserProfileDTO requestDto = UpdateUserProfileDTO.builder()
                .name("New Name")
                .build();

        when(userService.updateUserProfile(any(UpdateUserProfileDTO.class))).thenThrow(
                new BadRequestAlertException("User not found in security context", "userProfile", "notloggedin")
        );

        mockMvc.perform(put("/api/v1/users/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.entityName").value("userProfile"))
                .andExpect(jsonPath("$.errorKey").value("notloggedin"));
    }
}

