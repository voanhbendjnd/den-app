package com.denhub.service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.denhub.domain.Channel;
import com.denhub.domain.Server;
import com.denhub.domain.ServerMember;
import com.denhub.domain.User;
import com.denhub.domain.Invite;
import com.denhub.domain.enums.InviteStatus;
import com.denhub.repository.ChannelRepository;
import com.denhub.repository.InviteRepository;
import com.denhub.repository.MessageRepository;
import com.denhub.repository.ServerMemberRepository;
import com.denhub.repository.ServerRepository;
import com.denhub.repository.UserRepository;
import com.denhub.security.SecurityUtils;
import com.denhub.service.dto.CreateServerDTO;
import com.denhub.service.dto.CreateServerInviteRequestDTO;
import com.denhub.service.dto.ServerInviteResponseDTO;
import com.denhub.service.dto.ServerMemberResponseDTO;
import com.denhub.service.dto.ServerResponseDTO;
import com.denhub.service.dto.UpdateServerDTO;
import com.denhub.web.rest.errors.BadRequestAlertException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor
@Transactional
public class ServerService {

    static String BASE62_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    static int CODE_LENGTH = 8;
    static SecureRandom SECURE_RANDOM = new SecureRandom();

    ServerRepository serverRepository;
    ServerMemberRepository serverMemberRepository;
    UserRepository userRepository;
    InviteRepository inviteRepository;
    ChannelRepository channelRepository;
    MessageRepository messageRepository;

    public void deleteServer(Long serverId) {
        User currentUser = getActiveCurrentUser();
        if (serverId == null || serverId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Server ID không hợp lệ");
        }
        Server server = serverRepository.findByIdForUpdate(serverId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy Server"));
        if (!currentUser.getId().equals(server.getOwnerId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chỉ chủ sở hữu mới có quyền xóa Server");
        }
        // Refuse inconsistent cross-domain references rather than deleting private conversations.
        if (messageRepository.hasConversationReferencesInServer(serverId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Không thể xóa an toàn vì dữ liệu tin nhắn đang liên kết với hội thoại riêng. Vui lòng liên hệ quản trị viên.");
        }
        messageRepository.deleteChannelMessagesByServerId(serverId);
        channelRepository.deleteAllByServerId(serverId);
        inviteRepository.deleteAllByServerId(serverId);
        serverMemberRepository.deleteAllByServerId(serverId);
        serverRepository.delete(server);
        serverRepository.flush();
    }

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

    public ServerResponseDTO updateServer(Long serverId, UpdateServerDTO dto) {
        User currentUser = getActiveCurrentUser();
        if (serverId == null || serverId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Server ID không hợp lệ");
        }
        Server server = serverRepository.findByIdForUpdate(serverId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy Server"));
        if (!currentUser.getId().equals(server.getOwnerId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chỉ chủ sở hữu mới có quyền chỉnh sửa Server");
        }

        // Validate before mutating the managed entity, including direct service calls.
        if (dto == null || dto.getName() == null || dto.getName().length() > 100
                || !dto.getName().matches("(?sU).*\\S.*")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tên Server phải có từ 1 đến 100 ký tự");
        }
        if (dto.getIconUrl() != null && dto.getIconUrl().length() > 255) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Đường dẫn ảnh không được vượt quá 255 ký tự");
        }

        server.setName(dto.getName().strip());
        String icon = dto.getIconUrl() == null ? null : dto.getIconUrl().strip();
        server.setIconUrl(icon == null || icon.isBlank() ? null : icon);
        // Persist and capture the audit actor before leaving the authenticated request.
        Server saved = serverRepository.saveAndFlush(server);

        ServerResponseDTO response = new ServerResponseDTO();
        response.setId(saved.getId());
        response.setName(saved.getName());
        response.setIconUrl(saved.getIconUrl());
        response.setOwnerId(saved.getOwnerId());
        response.setCreatedDate(saved.getCreatedDate());
        return response;
    }

    @Transactional(readOnly = true)
    public List<ServerMemberResponseDTO> getServerMembers(Long serverId) {
        User currentUser = getActiveCurrentUser();
        Server server = serverRepository.findById(serverId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Không tìm thấy Server"));

        boolean isOwner = currentUser.getId().equals(server.getOwnerId());
        if (!isOwner && !serverMemberRepository.existsByServerIdAndUserId(serverId, currentUser.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Bạn không có quyền xem danh sách thành viên của Server này");
        }

        List<ServerMember> members = serverMemberRepository.findAllByServerId(serverId);
        if (members.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> userIds = members.stream().map(ServerMember::getUserId).toList();
        Map<Long, User> userMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        return members.stream().map(member -> {
            ServerMemberResponseDTO dto = new ServerMemberResponseDTO();
            dto.setId(member.getId());
            dto.setServerId(member.getServerId());
            dto.setUserId(member.getUserId());
            dto.setNickname(member.getNickname());

            User user = userMap.get(member.getUserId());
            if (user != null) {
                dto.setUsername(user.getUsername());
                dto.setName(user.getName());
                dto.setAvatar(user.getAvatar());
            }

            dto.setRole(member.getUserId().equals(server.getOwnerId()) ? "OWNER" : (member.getRole() != null ? member.getRole() : "MEMBER"));
            return dto;
        }).toList();
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
        member.setRole("OWNER");
        member.setJoinedAt(Instant.now());
        // Mặc định dùng tên hiển thị của user làm nickname trong server
        member.setNickname(ownerNickname(currentUser.getName()));
        serverMemberRepository.save(member);

        // 4. Tự động khởi tạo kênh #general mặc định theo BR-SRV-02
        Channel defaultChannel = new Channel();
        defaultChannel.setServerId(server.getId());
        defaultChannel.setName("general");
        defaultChannel.setPosition(0);
        channelRepository.save(defaultChannel);

        // 5. Trả về DTO
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

    private String ownerNickname(String name) {
        String nickname = name == null ? "" : name.strip();
        if (nickname.isBlank()) return "Owner";
        if (nickname.length() <= 20) return nickname;
        int end = 20;
        if (Character.isHighSurrogate(nickname.charAt(end - 1))
                && Character.isLowSurrogate(nickname.charAt(end))) {
            end--;
        }
        return nickname.substring(0, end);
    }

    public void joinServer(Long serverId) {
        String currentUserEmail = SecurityUtils.getCurrentUserLogin()
                .orElseThrow(() -> new BadRequestAlertException("User not found in security context", "server", "notloggedin"));

        User currentUser = userRepository.findOneByEmail(currentUserEmail)
                .orElseThrow(() -> new BadRequestAlertException("User not found in database", "server", "usernotfound"));

        Server server = serverRepository.findByIdForUpdate(serverId)
                .orElseThrow(() -> new BadRequestAlertException("Server not found", "server", "servernotfound"));

        if (serverMemberRepository.existsByServerIdAndUserId(serverId, currentUser.getId())) {
            throw new BadRequestAlertException("User is already a member of this server", "server", "alreadymember");
        }

        ServerMember member = new ServerMember();
        member.setServerId(server.getId());
        member.setUserId(currentUser.getId());
        member.setRole("MEMBER");
        member.setJoinedAt(Instant.now());
        member.setNickname(currentUser.getName() != null ? currentUser.getName() : "Member");
        serverMemberRepository.save(member);
    }

    @Transactional
    public void leaveServer(Long serverId) {
        if (serverId == null || serverId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Server ID không hợp lệ");
        }

        User currentUser = getActiveCurrentUser();

        Server server = serverRepository.findByIdForUpdate(serverId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy Server"));

        if (currentUser.getId().equals(server.getOwnerId())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Chủ sở hữu không thể rời khỏi Server. Bạn chỉ có thể chuyển quyền sở hữu hoặc xóa Server");
        }

        if (!serverMemberRepository.existsByServerIdAndUserId(serverId, currentUser.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bạn không phải là thành viên của Server này");
        }

        serverMemberRepository.deleteByServerIdAndUserId(serverId, currentUser.getId());
    }

    @Transactional
    public void removeMember(Long serverId, Long userId) {
        if (serverId == null || serverId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Server ID không hợp lệ");
        }
        if (userId == null || userId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User ID không hợp lệ");
        }

        User currentUser = getActiveCurrentUser();

        Server server = serverRepository.findByIdForUpdate(serverId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy Server"));

        if (!currentUser.getId().equals(server.getOwnerId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chỉ chủ sở hữu Server mới có quyền loại thành viên");
        }

        if (userId.equals(server.getOwnerId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không thể loại chủ sở hữu khỏi Server");
        }

        if (!serverMemberRepository.existsByServerIdAndUserId(serverId, userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Thành viên không tồn tại trong Server");
        }

        serverMemberRepository.deleteByServerIdAndUserId(serverId, userId);
    }

    public ServerResponseDTO joinServerByInviteCode(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã mời không được để trống");
        }
        String code = rawCode.trim();

        User currentUser = getActiveCurrentUser();

        Invite invite = inviteRepository.findByCode(code)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Mã mời không tồn tại"));

        if (!InviteStatus.ACTIVE.name().equalsIgnoreCase(invite.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Mã mời đã bị vô hiệu hóa hoặc không còn hiệu lực");
        }

        if (invite.getExpiresAt() != null && invite.getExpiresAt().isBefore(Instant.now())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Mã mời đã hết hạn");
        }

        Server server = serverRepository.findByIdForUpdate(invite.getServerId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Không tìm thấy Server liên kết với mã mời"));

        if (serverMemberRepository.existsByServerIdAndUserId(server.getId(), currentUser.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Bạn đã là thành viên của Server này rồi");
        }

        ServerMember member = new ServerMember();
        member.setServerId(server.getId());
        member.setUserId(currentUser.getId());
        member.setRole("MEMBER");
        member.setJoinedAt(Instant.now());
        member.setNickname(ownerNickname(currentUser.getName()));
        serverMemberRepository.save(member);

        ServerResponseDTO response = new ServerResponseDTO();
        response.setId(server.getId());
        response.setName(server.getName());
        response.setIconUrl(server.getIconUrl());
        response.setOwnerId(server.getOwnerId());
        response.setCreatedDate(server.getCreatedDate());
        return response;
    }

    @Transactional
    public ServerInviteResponseDTO createServerInvite(Long serverId, CreateServerInviteRequestDTO dto) {
        if (serverId == null || serverId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Server ID không hợp lệ");
        }

        User currentUser = getActiveCurrentUser();
        Server server = serverRepository.findByIdForUpdate(serverId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Không tìm thấy Server"));

        boolean isOwner = currentUser.getId().equals(server.getOwnerId());
        if (!isOwner) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Chỉ chủ sở hữu Server mới có quyền tạo mã mời");
        }

        Instant expiresAt = null;
        if (dto != null && dto.getExpiresAt() != null) {
            if (dto.getExpiresAt().isBefore(Instant.now())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Thời gian hết hạn không được ở trong quá khứ");
            }
            expiresAt = dto.getExpiresAt();
        }

        String code = null;
        int maxAttempts = 5;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            String candidate = generateSecureCode();
            if (!inviteRepository.existsByCode(candidate)) {
                code = candidate;
                break;
            }
        }
        if (code == null) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Không thể sinh mã mời duy nhất, vui lòng thử lại");
        }

        Invite invite = new Invite();
        invite.setServerId(server.getId());
        invite.setCode(code);
        invite.setCreatedBy(currentUser.getId());
        invite.setExpiresAt(expiresAt);
        invite.setStatus(InviteStatus.ACTIVE.name());
        invite.setCreatedAt(Instant.now());

        invite = inviteRepository.save(invite);

        ServerInviteResponseDTO response = new ServerInviteResponseDTO();
        response.setId(invite.getId());
        response.setServerId(invite.getServerId());
        response.setCode(invite.getCode());
        response.setCreatedBy(invite.getCreatedBy());
        response.setExpiresAt(invite.getExpiresAt());
        response.setStatus(invite.getStatus());
        response.setCreatedAt(invite.getCreatedAt());

        return response;
    }

    private String generateSecureCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(BASE62_CHARS.charAt(SECURE_RANDOM.nextInt(BASE62_CHARS.length())));
        }
        return sb.toString();
    }
}
