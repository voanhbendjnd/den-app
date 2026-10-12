package com.denhub.config;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import com.denhub.domain.Authority;
import com.denhub.domain.User;
import com.denhub.repository.AuthorityRepository;
import com.denhub.repository.UserRepository;
import com.denhub.security.AuthoritiesConstants;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class DatabaseInitializer implements CommandLineRunner {
    UserRepository userRepository;
    AuthorityRepository authorityRepository;
    PasswordEncoder passwordEncoder;
    String initEmail;
    String initPassword;

    public DatabaseInitializer(
        UserRepository userRepository,
        AuthorityRepository authorityRepository,
        PasswordEncoder passwordEncoder,
        @Value("${denhub.init-admin.email:admin@denhub.local}") String initEmail,
        @Value("${denhub.init-admin.password:}") String initPassword
    ) {
        this.userRepository = userRepository;
        this.authorityRepository = authorityRepository;
        this.passwordEncoder = passwordEncoder;
        this.initEmail = initEmail;
        this.initPassword = initPassword;
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("Database start check initialization...");
        Long totalUsers = userRepository.count();
        Long totalAuthority = authorityRepository.count();
        Set<Authority> authorities = new HashSet<>();

        if (totalAuthority.equals(0L)) {
            log.info("Start create authority...");

            Authority adminAuthority = new Authority();
            adminAuthority.setName(AuthoritiesConstants.ADMIN);
            Authority userAuthority = new Authority();
            userAuthority.setName(AuthoritiesConstants.USER);
            Authority anonymousAuthority = new Authority();
            anonymousAuthority.setName(AuthoritiesConstants.ANONYMOUS);
            authorities.addAll(List.of(adminAuthority, userAuthority, anonymousAuthority));
            authorityRepository.saveAll(authorities);
        }
        if (totalUsers.equals(0L)) {
            log.info("Start create user...");
            String resolvedPassword = (initPassword != null && !initPassword.isBlank())
                ? initPassword
                : UUID.randomUUID().toString();
            User admin = new User();
            admin.setName("Admin");
            admin.setActivated(true);
            admin.setEmail(initEmail);
            admin.setPassword(passwordEncoder.encode(resolvedPassword));
            admin.setAuthorities(authorities);
            userRepository.save(admin);
            log.info("Initial admin user ready with email: {}", initEmail);
        }
        if (totalUsers > 0 || totalAuthority > 0) {
            log.info("Skip processing initialize...");
        } else {
            log.info("End init data and init data success");
        }
    }
}