package com.denhub.security;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.denhub.repository.UserRepository;
import com.denhub.service.errors.DataResourceNotFoundException;

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
    @Transactional(readOnly = true)
    public boolean isValidSessionIdByEmail(String email, String sessionId){
        if (email == null || email.isBlank() || sessionId == null || sessionId.isBlank()) {
            return false;
        }
        String normalizedEmail = email.trim().toLowerCase(Locale.ENGLISH);
        return userRepository.findCurrentSessionIdByEmail(normalizedEmail)
                .map(sessionId::equals)
                .orElse(false);
    }

}
