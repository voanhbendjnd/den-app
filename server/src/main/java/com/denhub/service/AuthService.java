package com.denhub.service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.denhub.domain.Authority;
import com.denhub.domain.User;
import com.denhub.repository.UserRepository;
import com.denhub.security.SecurityUtils;
import com.denhub.security.SessionManager;
import com.denhub.service.dto.ResLoginDTO;
import com.denhub.service.errors.DataResourceNotFoundException;

import java.util.Set;
import java.util.stream.Collectors;

@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor
public class AuthService {
    UserRepository userRepository;
    SessionManager sessionManager;
    SecurityUtils securityUtils;
    @Transactional
    public ResLoginDTO generateResLoginDTO(User user){
        ResLoginDTO res = new ResLoginDTO();
        var userLogin = new ResLoginDTO.UserLogin();
        userLogin.setEmail(user.getEmail());
        userLogin.setId(user.getId());
        userLogin.setName(user.getName());
        Set<String> authorityName = user.getAuthorities().stream().map(Authority::getName).collect(Collectors.toSet());
        userLogin.setAuthorities(authorityName);
        String sessionId = sessionManager.initSessionId(user.getId());
        String newAccessToken = securityUtils.createAccessToken(userLogin, sessionId, authorityName);
        res.setUser(userLogin);
        res.setAccessToken(newAccessToken);

        String newRefreshToken = securityUtils.createRefreshToken(userLogin);
        int totalRowChanged = userRepository.updatedRefreshTokenById(userLogin.getId(), newRefreshToken);
        if(totalRowChanged <= 0){
            throw new DataResourceNotFoundException(
                    String.format("User with ID [%d] not found", userLogin.getId()),
                    "userManagement",
                    "idnotfound"
            );
        }
        res.setRefreshToken(newRefreshToken);
        return res;

    }
}
