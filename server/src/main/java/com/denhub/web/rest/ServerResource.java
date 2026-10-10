package com.denhub.web.rest;

import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.denhub.service.ServerService;
import com.denhub.service.dto.CreateServerDTO;
import com.denhub.service.dto.ServerResponseDTO;

@RestController
@RequestMapping("/api/v1/servers")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor
public class ServerResource {

    ServerService serverService;

    @PostMapping
    public ResponseEntity<ServerResponseDTO> createServer(@Valid @RequestBody CreateServerDTO dto) {
        ServerResponseDTO response = serverService.createServer(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<java.util.List<ServerResponseDTO>> getUserServers() {
        java.util.List<ServerResponseDTO> servers = serverService.getUserServers();
        return ResponseEntity.ok(servers);
    }
}
