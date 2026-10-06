package com.denhub.service;

import org.springframework.stereotype.Service;
import com.denhub.repository.ServerRepository;

@Service
public class ServerService {
    private final ServerRepository serverRepository;
    public ServerService(ServerRepository serverRepository) {
        this.serverRepository = serverRepository;
    }
}
