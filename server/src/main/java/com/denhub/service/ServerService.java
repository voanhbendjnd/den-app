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
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor
@Transactional
public class ServerService {
    
    ServerRepository serverRepository;
    ServerMemberRepository serverMemberRepository;
    UserRepository userRepository;

    @Transactional(readOnly = true)
    public ServerResponseDTO getServerDetail(Long serverId) {
        User currentUser = getActiveCurrentUser();
        Server server = serverRepository.findById(serverId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Không tìm thấy Server"));

        boolean isOwner = currentUser.getId().equals(server.getOwnerId());
        if (!isOwner && !serverMemberRepository.existsByServerIdAndUserId(serverId, currentUser.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền xem Server này");
        }

        ServerResponseDTO response = new ServerResponseDTO();
        response.setId(server.getId());
        response.setName(server.getName());
        response.setIconUrl(server.getIconUrl());
        response.setOwnerId(server.getOwnerId());
        response.setCreatedDate(server.getCreatedDate());
        return response;
    }

    private User getActiveCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bạn cần đăng nhập để truy cập Server");
        }

        String email = SecurityUtils.getCurrentUserLogin()
                .filter(login -> !login.isBlank())
                .map(login -> login.trim().toLowerCase(Locale.ENGLISH))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Phiên đăng nhập không hợp lệ"));
        return userRepository.findOneByEmail(email)
                .filter(user -> Boolean.TRUE.equals(user.getActivated()))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Phiên đăng nhập không hợp lệ"));
    }

    public ServerResponseDTO createServer(CreateServerDTO dto) {
        // 1. Lấy thông tin user đăng nhập hiện tại
        User currentUser = getActiveCurrentUser();

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
        User currentUser = getActiveCurrentUser();

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
