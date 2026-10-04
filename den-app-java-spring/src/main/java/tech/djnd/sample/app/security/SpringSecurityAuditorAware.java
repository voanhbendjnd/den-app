package tech.djnd.sample.app.security;

import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Component;
import tech.djnd.sample.app.config.Constants;

import java.util.Optional;
@Component
public class SpringSecurityAuditorAware implements AuditorAware<String> {
    @Override
    public Optional<String> getCurrentAuditor() {
        return Optional.of(SecurityUtils.getCurrentUserLogin().orElse(Constants.SYSTEM_ACCOUNT));
    }
}