package com.denhub.web.rest;

import com.denhub.domain.Authority;
import com.denhub.domain.User;
import com.denhub.security.AuthoritiesConstants;
import com.denhub.service.AuthService;
import com.denhub.service.MailService;
import com.denhub.service.UserService;
import com.denhub.service.dto.GoogleLoginReqDTO;
import com.denhub.service.dto.RegisterReqDTO;
import com.denhub.service.dto.ResLoginDTO;
import com.denhub.util.ResFormatResponse;
import com.denhub.web.rest.errors.ExceptionTranslator;
import com.denhub.web.rest.errors.LoginAlreadyUsedException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.zalando.problem.jackson.ProblemModule;
import org.zalando.problem.violations.ConstraintViolationProblemModule;

import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AccountResourceIT {

    @Mock
    private UserService userService;

    @Mock
    private AuthenticationManagerBuilder authenticationManagerBuilder;

    @Mock
    private AuthService authService;

    @Mock
    private MailService mailService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AccountResource accountResource;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(accountResource, "refreshTokenExpiration", 604800L);

        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .registerModule(new ProblemModule())
                .registerModule(new ConstraintViolationProblemModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(objectMapper);

        mockMvc = MockMvcBuilders.standaloneSetup(accountResource)
                .setMessageConverters(converter)
                .setControllerAdvice(new ResFormatResponse(), new ExceptionTranslator())
                .build();
    }

    @Test
    void registerAccountSuccessReturns201Created() throws Exception {
        RegisterReqDTO req = new RegisterReqDTO();
        req.setEmail("testuser@example.com");
        req.setPassword("Password123");
        req.setName("Test User");
        req.setUsername("testuser");

        User user = new User();
        user.setId(101L);
        user.setEmail("testuser@example.com");
        user.setName("Test User");
        user.setUsername("testuser");
        user.setActivated(true);
        Authority auth = new Authority();
        auth.setName(AuthoritiesConstants.USER);
        user.setAuthorities(Set.of(auth));

        when(userService.registerUser(any(RegisterReqDTO.class))).thenReturn(user);

        mockMvc.perform(post("/api/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.email").value("testuser@example.com"))
                .andExpect(jsonPath("$.data.name").value("Test User"))
                .andExpect(jsonPath("$.message").value("Đăng ký tài khoản thành công"));

        verify(userService, times(1)).registerUser(any(RegisterReqDTO.class));
    }

    @Test
    void registerAccountDuplicateEmailReturns400BadRequest() throws Exception {
        RegisterReqDTO req = new RegisterReqDTO();
        req.setEmail("duplicate@example.com");
        req.setPassword("Password123");

        when(userService.registerUser(any(RegisterReqDTO.class))).thenThrow(new LoginAlreadyUsedException());

        mockMvc.perform(post("/api/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registerAccountInvalidEmailReturns400BadRequest() throws Exception {
        RegisterReqDTO req = new RegisterReqDTO();
        req.setEmail("not-an-email");
        req.setPassword("Password123");

        mockMvc.perform(post("/api/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registerAccountShortPasswordReturns400BadRequest() throws Exception {
        RegisterReqDTO req = new RegisterReqDTO();
        req.setEmail("valid@example.com");
        req.setPassword("12");

        mockMvc.perform(post("/api/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void loginWithGoogleSuccessReturnsResLoginDTO() throws Exception {
        GoogleLoginReqDTO req = new GoogleLoginReqDTO();
        req.setEmail("googleuser@gmail.com");
        req.setName("Google User");
        req.setAvatar("https://example.com/avatar.png");
        req.setGoogleId("google-sub-123");

        User user = new User();
        user.setId(202L);
        user.setEmail("googleuser@gmail.com");
        user.setName("Google User");

        ResLoginDTO loginDTO = new ResLoginDTO();
        loginDTO.setAccessToken("mocked-jwt-token");
        loginDTO.setRefreshToken("mocked-refresh-token");
        ResLoginDTO.UserLogin userLogin = new ResLoginDTO.UserLogin();
        userLogin.setId(202L);
        userLogin.setEmail("googleuser@gmail.com");
        userLogin.setName("Google User");
        loginDTO.setUser(userLogin);

        when(userService.processGoogleUser(eq("googleuser@gmail.com"), eq("Google User"), any(), any())).thenReturn(user);
        when(authService.generateResLoginDTO(user)).thenReturn(loginDTO);

        mockMvc.perform(post("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("mocked-jwt-token"))
                .andExpect(jsonPath("$.data.user.email").value("googleuser@gmail.com"))
                .andExpect(jsonPath("$.message").value("Đăng nhập bằng Google thành công"));
    }

    @Test
    void loginWithGoogleMissingEmailReturnsBadRequest() throws Exception {
        GoogleLoginReqDTO req = new GoogleLoginReqDTO();

        mockMvc.perform(post("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }
}
