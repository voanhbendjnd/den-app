package tech.djnd.sample.app.security;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import tech.djnd.sample.app.repository.UserRepository;
import tech.djnd.sample.app.service.errors.DataResourceNotFoundException;

import java.util.Locale;
import java.util.UUID;

@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor
public class SessionManager {
    UserRepository userRepository;
    public String initSessionId(Long userId){
        String newSessionId = UUID.randomUUID().toString();
        int updated = userRepository.updateSessionIdById(userId, newSessionId);
        if(updated > 0){
            return newSessionId;
        }
        throw new DataResourceNotFoundException(String.format("User with ID %d not found!", userId), "userManagement", "idnotfound");
    }
    public boolean isValidSessionIdByEmail(String email, String sessionId){
        String normalizedEmail = email.trim().toLowerCase(Locale.ENGLISH);
        return userRepository.findOneWithAuthoritiesByEmail(normalizedEmail).map(existingUser -> existingUser.getSessionId().equals(sessionId)).orElse(false);
    }

}
