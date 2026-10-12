package com.denhub.service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.denhub.domain.Server;
import com.denhub.domain.ServerMember;
import com.denhub.domain.User;
import com.denhub.repository.ServerMemberRepository;
import com.denhub.repository.ServerRepository;
import com.denhub.repository.UserRepository;
import com.denhub.security.SecurityUtils;
import com.denhub.service.dto.CreateServerDTO;
import com.denhub.service.dto.ServerResponseDTO;
import com.denhub.web.rest.errors.BadRequestAlertException;

@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor
@Transactional
public class ServerService {
    
    ServerRepository serverRepository;
    ServerMemberRepository serverMemberRepository;
    UserRepository userRepository;

    public ServerResponseDTO createServer(CreateServerDTO dto) {
        // 1. Lấy thông tin user đăng nhập hiện tại
        String currentUserEmail = SecurityUtils.getCurrentUserLogin()
                .orElseThrow(() -> new BadRequestAlertException("User not found in security context", "server", "notloggedin"));

        User currentUser = userRepository.findOneByEmail(currentUserEmail)
                .orElseThrow(() -> new BadRequestAlertException("User not found in database", "server", "usernotfound"));

        // 2. Tạo bản ghi Server mới
        Server server = new Server();
        server.setName(dto.getName());
        server.setIconUrl(dto.getIconUrl());
        server.setOwnerId(currentUser.getId());
        
        server = serverRepository.save(server);

        // 3. Tự động thêm Owner vào bảng ServerMember
        ServerMember member = new ServerMember();
        member.setServerId(server.getId());
        member.setUserId(currentUser.getId());
        // Mặc định dùng tên hiển thị của user làm nickname trong server
        member.setNickname(currentUser.getName() != null ? currentUser.getName() : "Owner");
        serverMemberRepository.save(member);

        // 4. Trả về DTO
        ServerResponseDTO response = new ServerResponseDTO();
        response.setId(server.getId());
        response.setName(server.getName());
        response.setIconUrl(server.getIconUrl());
        response.setOwnerId(server.getOwnerId());
        response.setCreatedDate(server.getCreatedDate());

        return response;
    }

    @Transactional(readOnly = true)
    public java.util.List<ServerResponseDTO> getUserServers() {
        String currentUserEmail = SecurityUtils.getCurrentUserLogin()
                .orElseThrow(() -> new BadRequestAlertException("User not found in security context", "server", "notloggedin"));

        User currentUser = userRepository.findOneByEmail(currentUserEmail)
                .orElseThrow(() -> new BadRequestAlertException("User not found in database", "server", "usernotfound"));

        java.util.List<Server> servers = serverRepository.findAllByUserIdOrOwnerId(currentUser.getId());

        return servers.stream().map(server -> {
            ServerResponseDTO response = new ServerResponseDTO();
            response.setId(server.getId());
            response.setName(server.getName());
            response.setIconUrl(server.getIconUrl());
            response.setOwnerId(server.getOwnerId());
            response.setCreatedDate(server.getCreatedDate());
            return response;
        }).toList();
    }

    public void joinServer(Long serverId) {
        String currentUserEmail = SecurityUtils.getCurrentUserLogin()
                .orElseThrow(() -> new BadRequestAlertException("User not found in security context", "server", "notloggedin"));

        User currentUser = userRepository.findOneByEmail(currentUserEmail)
                .orElseThrow(() -> new BadRequestAlertException("User not found in database", "server", "usernotfound"));

        Server server = serverRepository.findById(serverId)
                .orElseThrow(() -> new BadRequestAlertException("Server not found", "server", "servernotfound"));

        if (serverMemberRepository.existsByServerIdAndUserId(serverId, currentUser.getId())) {
            throw new BadRequestAlertException("User is already a member of this server", "server", "alreadymember");
        }

        ServerMember member = new ServerMember();
        member.setServerId(server.getId());
        member.setUserId(currentUser.getId());
        member.setNickname(currentUser.getName() != null ? currentUser.getName() : "Member");
        serverMemberRepository.save(member);
    }
}
