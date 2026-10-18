package com.denhub.service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.denhub.domain.Authority;
import com.denhub.domain.User;
import com.denhub.repository.AuthorityRepository;
import com.denhub.repository.UserRepository;
import com.denhub.security.AuthoritiesConstants;
import com.denhub.service.dto.RegisterReqDTO;
import com.denhub.service.dto.UserDTO;
import com.denhub.web.rest.errors.LoginAlreadyUsedException;
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

    @Transactional(readOnly = true)
    public Optional<User> getUserWithAuthorities() {
        return com.denhub.security.SecurityUtils.getCurrentUserLogin()
                .flatMap(userRepository::findOneWithAuthoritiesByEmail);
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
