package com.denhub.service;

import com.denhub.repository.UserRepository;
import com.denhub.service.dto.ResultPaginationDTO;
import com.denhub.service.projection.UsernameProjection;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class SearchUserService {
    private final UserRepository userRepository;

    public SearchUserService(UserRepository userRepository){
        this.userRepository = userRepository;
    }

    public List<UsernameProjection> searchByUsername(String username){
        if (username == null || username.isBlank()){
            return List.of();
        }
        return userRepository.searchByUsername(username.trim().toLowerCase(Locale.ENGLISH));
    }
}
