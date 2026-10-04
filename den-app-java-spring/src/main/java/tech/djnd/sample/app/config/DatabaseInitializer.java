package tech.djnd.sample.app.config;


import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import tech.djnd.sample.app.domain.Authority;
import tech.djnd.sample.app.domain.User;
import tech.djnd.sample.app.repository.AuthorityRepository;
import tech.djnd.sample.app.repository.UserRepository;
import tech.djnd.sample.app.security.AuthoritiesConstants;

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
            userAuthority.setName(AuthoritiesConstants.STUDENT);
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
            admin.setEmail("admin@gmail.com");
            admin.setPassword(passwordEncoder.encode("123123"));
            admin.setAuthorities(authorities);
            userRepository.save(admin);
        }
        if(totalUsers > 0 || totalAuthority > 0){
            log.info("Skip processing initialize...");
        }
        else{
            log.info("End init data and init data success");
        }
    }
}