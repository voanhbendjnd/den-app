package com.denhub.service;

import com.denhub.service.dto.ResLoginDTO;
import com.denhub.service.errors.BadRequestResourceException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.denhub.domain.Authority;
import com.denhub.domain.User;
import com.denhub.repository.AuthorityRepository;
import com.denhub.repository.UserRepository;
import com.denhub.security.AuthoritiesConstants;
import com.denhub.security.SecurityUtils;
import com.denhub.service.dto.RegisterReqDTO;
import com.denhub.service.dto.UserDTO;
import com.denhub.service.dto.UserProfileResponseDTO;
import com.denhub.web.rest.errors.BadRequestAlertException;
import com.denhub.web.rest.errors.LoginAlreadyUsedException;
import org.springframework.web.client.RestTemplate;
import tech.jhipster.security.RandomUtil;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor
@Transactional
@Slf4j
public class UserService {
    private static final long ACTIVATION_KEY_VALIDITY_DAYS = 3;
    UserRepository userRepository;
    CacheManager cacheManager;
    PasswordEncoder passwordEncoder;
    AuthorityRepository authorityRepository;
    AuthService authService;
    private static final String LINK_GITHUB_USER = "https://api.github.com/user";
    private static final String LINK_GOOGLE_USER = "https://www.googleapis.com/oauth2/v3/userinfo";

    @org.springframework.beans.factory.annotation.Value("${github.client-id:}")
    @lombok.experimental.NonFinal
    String githubClientId;

    @org.springframework.beans.factory.annotation.Value("${github.client-secret:}")
    @lombok.experimental.NonFinal
    String githubClientSecret;

    @Transactional(readOnly = true)
    public Optional<User> getUserWithAuthorities() {
        return com.denhub.security.SecurityUtils.getCurrentUserLogin()
                .flatMap(userRepository::findOneWithAuthoritiesByEmail);
    }

    /**
     * Lấy thông tin hồ sơ của người dùng hiện tại đang đăng nhập.
     *
     * @return UserProfileResponseDTO thông tin hồ sơ người dùng
     * @throws BadRequestAlertException nếu chưa đăng nhập hoặc không tìm thấy người dùng
     */
    @Transactional(readOnly = true)
    public UserProfileResponseDTO getUserProfile() {
        String currentUserEmail = SecurityUtils.getCurrentUserLogin()
                .orElseThrow(() -> new BadRequestAlertException("User not found in security context", "userProfile", "notloggedin"));

        User user = userRepository.findOneWithAuthoritiesByEmail(currentUserEmail)
                .orElseThrow(() -> new BadRequestAlertException("User not found in database", "userProfile", "usernotfound"));

        return UserProfileResponseDTO.fromUser(user);
    }

    public User registerUser(RegisterReqDTO req) {
        String normalizedEmail = req.getEmail().trim().toLowerCase(Locale.ENGLISH);
        userRepository.findOneByEmail(normalizedEmail).ifPresent(existingUser -> {
            boolean removed = this.removeNoneActivatedUser(existingUser);
            if (!removed) {
                throw new LoginAlreadyUsedException();
            }
        });
        User newUser = new User();
        newUser.setEmail(normalizedEmail);
        String encryptedPassword = passwordEncoder.encode(req.getPassword());
        newUser.setPassword(encryptedPassword);
        if (req.getName() != null && !req.getName().isBlank()) {
            newUser.setName(req.getName().trim());
        } else {
            newUser.setName(normalizedEmail.split("@")[0]);
        }
        if (req.getUsername() != null && !req.getUsername().isBlank()) {
            newUser.setUsername(req.getUsername().trim());
        }
        newUser.setType("SYSTEM");
        newUser.setActivated(true);
        Set<Authority> authorities = new HashSet<>();
        authorityRepository.findById(AuthoritiesConstants.USER).ifPresent(authorities::add);
        newUser.setAuthorities(authorities);
        newUser = userRepository.save(newUser);
        this.clearUserCaches(newUser);
        return newUser;
    }

    public User registerUser(UserDTO dto, String password) {
        String normalizedEmail = dto.getEmail().trim().toLowerCase(Locale.ENGLISH);
        userRepository.findOneByEmail(normalizedEmail).ifPresent(existingUser -> {
            boolean removed = this.removeNoneActivatedUser(existingUser);
            if (!removed) {
                throw new LoginAlreadyUsedException();
            }
        });
        User newUser = new User();
        newUser.setEmail(normalizedEmail);
        String encryptedPassword = passwordEncoder.encode(password);
        newUser.setPassword(encryptedPassword);
        if (dto.getName() != null && !dto.getName().isBlank()) {
            newUser.setName(dto.getName().trim());
        } else {
            newUser.setName(normalizedEmail.split("@")[0]);
        }
        newUser.setActivated(true);
        Set<Authority> authorities = new HashSet<>();
        authorityRepository.findById(AuthoritiesConstants.USER).ifPresent(authorities::add);
        newUser.setAuthorities(authorities);
        newUser = userRepository.save(newUser);
        this.clearUserCaches(newUser);
        return newUser;
    }

    public ResLoginDTO processLogin(String email, String avatar, String name, String username, String type) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ENGLISH);
        Optional<User> userOp = userRepository.findOneWithAuthoritiesByEmail(normalizedEmail);
        if (userOp.isPresent()) {
            User existingUser = userOp.get();
            boolean changed = false;
            if ((existingUser.getName() == null || existingUser.getName().isBlank()) && name != null && !name.isBlank()) {
                existingUser.setName(name.trim());
                changed = true;
            }
            if ((existingUser.getAvatar() == null || existingUser.getAvatar().isBlank()) && avatar != null && !avatar.isBlank()) {
                existingUser.setAvatar(avatar);
                changed = true;
            }
            if (!Boolean.TRUE.equals(existingUser.getActivated())) {
                existingUser.setActivated(true);
                changed = true;
            }
            if (changed) {
                userRepository.save(existingUser);
                this.clearUserCaches(existingUser);
            }
            return authService.generateResLoginDTO(existingUser);
        } else {
            User newUser = new User();
            Set<Authority> authorities = new HashSet<>();
            authorityRepository.findById(AuthoritiesConstants.USER).ifPresent(authorities::add);
            newUser.setAuthorities(authorities);
            if (username != null && !username.isBlank()) {
                newUser.setUsername(username.trim());
            }
            newUser.setAvatar(avatar);
            if (name != null && !name.isBlank()) {
                newUser.setName(name.trim());
            } else {
                newUser.setName(username != null && !username.isBlank() ? username.trim() : normalizedEmail.split("@")[0]);
            }
            newUser.setType(type != null ? type.toUpperCase(Locale.ENGLISH) : "SOCIAL");
            newUser.setEmail(normalizedEmail);
            newUser.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
            newUser.setActivated(true);
            User saveUser = userRepository.save(newUser);
            this.clearUserCaches(saveUser);
            return authService.generateResLoginDTO(saveUser);
        }
    }

    private Map<String, Object> getUserInfo(String accessToken, String link) {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        headers.set("Accept", "application/json");
        HttpEntity<String> entity = new HttpEntity<>(headers);
        ResponseEntity<Map> response = restTemplate.exchange(link,
                HttpMethod.GET,
                entity,
                Map.class);
        return response.getBody();
    }

    private List<Map<String, Object>> getGithubEmails(String accessToken) {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        headers.set("Accept", "application/json");
        HttpEntity<String> entity = new HttpEntity<>(headers);
        ResponseEntity<List> response = restTemplate.exchange("https://api.github.com/user/emails",
                HttpMethod.GET,
                entity,
                List.class);
        return response.getBody();
    }

    private String getGithubAccessTokenFromCode(String code) {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", "application/json");
        Map<String, String> body = new HashMap<>();
        body.put("client_id", githubClientId);
        body.put("client_secret", githubClientSecret);
        body.put("code", code);
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity("https://github.com/login/oauth/access_token", entity, Map.class);
            if (response.getBody() != null && response.getBody().get("access_token") != null) {
                return (String) response.getBody().get("access_token");
            }
            log.warn("GitHub OAuth token exchange did not return access_token: {}", response.getBody());
        } catch (Exception ex) {
            log.error("Lỗi đổi code lấy GitHub access token: {}", ex.getMessage());
        }
        return null;
    }

    public ResLoginDTO loginWithSocialMedia(String accessToken, String type) {
        return loginWithSocialMedia(accessToken, null, type);
    }

    public ResLoginDTO loginWithSocialMedia(String accessToken, String code, String type) {
        if ((accessToken == null || accessToken.isBlank()) && code != null && !code.isBlank()) {
            if ("GITHUB".equalsIgnoreCase(type)) {
                accessToken = getGithubAccessTokenFromCode(code);
                if (accessToken == null) {
                    throw new BadRequestResourceException("Không thể xác thực mã xác thực từ GitHub", "accountManagement", "invalidgithubcode");
                }
            }
        }
        String username = null, avatar = null, email = null, name = null;
        if ("GITHUB".equalsIgnoreCase(type)) {
            Map<String, Object> userInfo = this.getUserInfo(accessToken, LINK_GITHUB_USER);
            if (userInfo == null) {
                throw new BadRequestResourceException("Không thể lấy thông tin người dùng từ GitHub", "accountManagement", "githubuserinfofailed");
            }
            username = userInfo.get("login") != null ? userInfo.get("login").toString() : null;
            name = userInfo.get("name") != null ? userInfo.get("name").toString() : username;
            avatar = userInfo.get("avatar_url") != null ? userInfo.get("avatar_url").toString() : null;
            Object rawEmail = userInfo.get("email");
            email = rawEmail != null ? rawEmail.toString() : null;

            if (email == null) {
                try {
                    List<Map<String, Object>> mails = this.getGithubEmails(accessToken);
                    if (mails != null) {
                        email = mails.stream()
                                .filter(e -> Boolean.TRUE.equals(e.get("primary")) && Boolean.TRUE.equals(e.get("verified")))
                                .map(e -> (String) e.get("email"))
                                .findFirst()
                                .orElse(null);
                        if (email == null) {
                            email = mails.stream()
                                    .filter(e -> Boolean.TRUE.equals(e.get("verified")))
                                    .map(e -> (String) e.get("email"))
                                    .findFirst()
                                    .orElse(null);
                        }
                    }
                } catch (Exception ex) {
                    log.warn("Không thể lấy email từ GitHub emails endpoint: {}", ex.getMessage());
                }
            }
            if (email == null) {
                throw new BadRequestResourceException("Tài khoản GitHub không cung cấp email hợp lệ", "accountManagement", "emailnull");
            }
        } else if ("GOOGLE".equalsIgnoreCase(type)) {
            Map<String, Object> userInfo = this.getUserInfo(accessToken, LINK_GOOGLE_USER);
            if (userInfo == null) {
                throw new BadRequestResourceException("Không thể lấy thông tin người dùng từ Google", "accountManagement", "googleuserinfofailed");
            }
            email = (String) userInfo.get("email");
            avatar = (String) userInfo.get("picture");
            name = (String) userInfo.get("name");
            username = email != null ? email.split("@")[0] : null;
            Boolean isEmailVerified = userInfo.get("email_verified") instanceof Boolean
                    ? (Boolean) userInfo.get("email_verified")
                    : Boolean.parseBoolean(String.valueOf(userInfo.get("email_verified")));
            if (isEmailVerified != null && !isEmailVerified) {
                throw new BadRequestResourceException("Google mail chưa được xác minh", "accountManagement", "notemailverified");
            }
        } else {
            throw new BadRequestResourceException("Loại đăng nhập mạng xã hội không được hỗ trợ: " + type, "accountManagement", "unsupportedtype");
        }

        if (email == null || email.isBlank()) {
            throw new BadRequestResourceException("Email không được cung cấp", "accountManagement", "emailnull");
        }
        return this.processLogin(email, avatar, name, username, type);
    }

    public User processGoogleUser(String email, String name, String avatar, String googleId) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ENGLISH);
        Optional<User> existingUserOpt = userRepository.findOneWithAuthoritiesByEmail(normalizedEmail);
        if (existingUserOpt.isPresent()) {
            User existingUser = existingUserOpt.get();
            boolean changed = false;
            if ((existingUser.getName() == null || existingUser.getName().isBlank()) && name != null && !name.isBlank()) {
                existingUser.setName(name.trim());
                changed = true;
            }
            if ((existingUser.getAvatar() == null || existingUser.getAvatar().isBlank()) && avatar != null && !avatar.isBlank()) {
                existingUser.setAvatar(avatar);
                changed = true;
            }
            if (!Boolean.TRUE.equals(existingUser.getActivated())) {
                existingUser.setActivated(true);
                changed = true;
            }
            if (changed) {
                userRepository.save(existingUser);
                this.clearUserCaches(existingUser);
            }
            return existingUser;
        }

        User newUser = new User();
        newUser.setEmail(normalizedEmail);
        if (name != null && !name.isBlank()) {
            newUser.setName(name.trim());
        } else {
            newUser.setName(normalizedEmail.split("@")[0]);
        }
        newUser.setAvatar(avatar);
        newUser.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        newUser.setActivated(true);
        Set<Authority> authorities = new HashSet<>();
        authorityRepository.findById(AuthoritiesConstants.USER).ifPresent(authorities::add);
        newUser.setAuthorities(authorities);
        newUser = userRepository.save(newUser);
        this.clearUserCaches(newUser);
        return newUser;
    }
    /*
    * activated account register from user
    * */
    public Optional<User> activatedRegistration(String key) {
        log.debug("Activating user for activation key {}", key);
        return userRepository.findOneByActivationKey(key).map(existingUser -> {
            existingUser.setActivated(true);
            existingUser.setActivationKey(null);
            this.clearUserCaches(existingUser);
            log.debug("Activated key {}", existingUser);
            return existingUser;
        });
    }
    public Optional<User> requestPasswordReset(String email) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ENGLISH);
        Instant now = Instant.now();
        return userRepository.findOneByEmail(normalizedEmail).filter(User::getActivated)
                .filter(user ->{
                    if(user.getResetDate() != null && user.getResetDate().isAfter(now.minus(60, ChronoUnit.SECONDS))){
                        return false;
                    }
                    return true;
                })
                .map(user ->{
            user.setResetDate(now);
            user.setResetKey(RandomUtil.generateResetKey());
            this.clearUserCaches(user);
            return user;
        });

    }
    public Optional<User> completePasswordReset(String newPassword, String resetKey){
        return userRepository.findOneByResetKey(resetKey)
                .filter(existingUser -> existingUser.getResetDate().isAfter(Instant.now().minus(1, ChronoUnit.DAYS)))
                .map(existingUser ->{
                    existingUser.setPassword(passwordEncoder.encode(newPassword));
                    existingUser.setResetKey(null);
                    existingUser.setResetDate(null);
                    this.clearUserCaches(existingUser);
                    return existingUser;
                });
    }

    private boolean removeNoneActivatedUser(User existingUser){
        if(existingUser.getActivated()){
            return false;
        }
        userRepository.delete(existingUser);
        userRepository.flush();
        this.clearUserCaches(existingUser);
        return true;
    }
    private void clearUserCaches(User user){
        var cacheByEmail = cacheManager.getCache(UserRepository.USERS_BY_EMAIL_CACHE);
        if(cacheByEmail != null){
            cacheByEmail.evict(user.getEmail().trim().toLowerCase(Locale.ENGLISH));
        }
    }

    /*
     * check after 3 day
     * and check 0 second - 0 minutes - 1 AM - Every day - Every week
     * */
    @Scheduled(cron = "0 0 1 * * ?")
    public void removeNotActivatedUsers(){
        List<User> currentUsers = userRepository.findAllByActivatedIsFalseAndActivationKeyNotNullAndCreatedDateBefore(Instant.now().minus(3, ChronoUnit.DAYS));
        List<Long> currentUserIds = currentUsers.stream().map(User::getId).toList();
        userRepository.deleteByIdIn(currentUserIds);
        currentUsers.forEach(this::clearUserCaches);
    }
}
