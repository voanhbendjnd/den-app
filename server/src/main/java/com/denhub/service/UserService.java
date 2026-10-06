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
    public User registerUser(UserDTO dto, String password) {
        String normalizedEmail = dto.getEmail().trim().toLowerCase(Locale.ENGLISH);
        userRepository.findOneByEmail(normalizedEmail).ifPresent(existingUser -> {
            boolean removed = this.removeNoneActivatedUser(existingUser);
            if(!removed){
                throw new LoginAlreadyUsedException();
            }
        });
        User newUser = new User();
        newUser.setEmail(normalizedEmail);
        String encryptedPassword = passwordEncoder.encode(password);
        newUser.setPassword(encryptedPassword);
        newUser.setActivated(false);
        Set<Authority> authorities = new HashSet<>();
        authorityRepository.findById(AuthoritiesConstants.USER).ifPresent(authorities::add);
        newUser.setAuthorities(authorities);
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
