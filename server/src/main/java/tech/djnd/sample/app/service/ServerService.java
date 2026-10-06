package tech.djnd.sample.app.service;

import org.springframework.stereotype.Service;
import tech.djnd.sample.app.repository.ServerRepository;

@Service
public class ServerService {
    private final ServerRepository serverRepository;
    public ServerService(ServerRepository serverRepository) {
        this.serverRepository = serverRepository;
    }
}
