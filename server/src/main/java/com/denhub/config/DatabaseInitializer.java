package com.denhub.config;


import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
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

@Component
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
public class DatabaseInitializer implements CommandLineRunner {
    UserRepository userRepository;
    AuthorityRepository authorityRepository;
    PasswordEncoder passwordEncoder;
    @Override
    public void run(String... args) throws Exception {
        log.info("Database start check initialization...");
        Long totalUsers = userRepository.count();
        Long totalAuthority = authorityRepository.count();
        Set<Authority> authorities = new HashSet<>();


        if(totalAuthority.equals(0L)) {
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
        if(totalUsers.equals(0L)){
            log.info("Start create user...");
            User admin = new User();
            admin.setName("VO ANH BEN");
            admin.setActivated(true);
            admin.setUsername("djnd");
            admin.setEmail("benva.ce190709@gmail.com");
            admin.setPassword(passwordEncoder.encode("123123"));
            admin.setAuthorities(authorities);
            userRepository.save(admin);

            User admin2 = new User();
            admin2.setName("Nico");
            admin2.setActivated(true);
            admin2.setUsername("nico");
            admin2.setEmail("voanhbendjnd@gmail.com");
            admin2.setPassword(passwordEncoder.encode("123123"));
            admin2.setAuthorities(authorities);
            userRepository.save(admin2);
        }
        if(totalUsers > 0 || totalAuthority > 0){
            log.info("Skip processing initialize...");
        }
        else{
            log.info("End init data and init data success");
        }
    }
}