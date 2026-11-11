package com.denhub.service;

import com.denhub.domain.Authority;
import com.denhub.domain.Server;
import com.denhub.domain.ServerMember;
import com.denhub.domain.User;
import com.denhub.repository.AuthorityRepository;
import com.denhub.repository.ChannelRepository;
import com.denhub.repository.InviteRepository;
import com.denhub.repository.MessageRepository;
import com.denhub.repository.ServerMemberRepository;
import com.denhub.repository.ServerRepository;
import com.denhub.repository.UserRepository;
import com.denhub.security.AuthoritiesConstants;
import com.denhub.security.SecurityUtils;
import com.denhub.service.dto.AdminCreateUserDTO;
import com.denhub.service.dto.AdminOwnershipPreviewDTO;
import com.denhub.service.dto.AdminSoftDeleteResultDTO;
import com.denhub.service.dto.AdminUpdateUserDTO;
import com.denhub.service.dto.AdminUserPageDTO;
import com.denhub.service.dto.AdminUserResponseDTO;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor
@Transactional
@Slf4j
public class AdminUserService {

    UserRepository userRepository;
    AuthorityRepository authorityRepository;
    ServerRepository serverRepository;
    ServerMemberRepository serverMemberRepository;
    ChannelRepository channelRepository;
    InviteRepository inviteRepository;
    MessageRepository messageRepository;
    PasswordEncoder passwordEncoder;
    CacheManager cacheManager;

    @Transactional(readOnly = true)
    public AdminUserPageDTO getUsers(String search, String role, Boolean activated, int page, int size) {
        requireActiveAdminUser();
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tham số phân trang không hợp lệ (page >= 0, size từ 1 đến 100)");
        }

        String pattern = null;
        if (search != null && !search.isBlank()) {
            String term = search.strip().toLowerCase(Locale.ROOT);
            if (term.length() > 100) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Từ khóa tìm kiếm tối đa 100 ký tự");
            }
            String escaped = term.replace("!", "!!")
                    .replace("%", "!%")
                    .replace("_", "!_")
                    .replace("[", "![");
            pattern = "%" + escaped + "%";
        }

        String normalizedRole = normalizeRoleFilter(role);
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        Page<User> result = userRepository.searchAdminUsers(pattern, normalizedRole, activated, pageable);

        List<User> content = result.getContent();
        Map<Long, Long> ownedServerCounts = new HashMap<>();
        if (!content.isEmpty()) {
            List<Long> userIds = content.stream().map(User::getId).toList();
            for (Object[] row : serverRepository.countByOwnerIds(userIds)) {
                Long ownerId = (Long) row[0];
                Long count = (Long) row[1];
                ownedServerCounts.put(ownerId, count);
            }
        }

        List<AdminUserResponseDTO> items = content.stream()
                .map(u -> AdminUserResponseDTO.fromUser(u, ownedServerCounts.getOrDefault(u.getId(), 0L)))
                .toList();

        return AdminUserPageDTO.builder()
                .items(items)
                .page(page)
                .size(size)
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();
    }

    @Transactional(readOnly = true)
    public AdminUserResponseDTO getUserDetail(Long userId) {
        requireActiveAdminUser();
        validateUserId(userId);
        User user = userRepository.findOneWithAuthoritiesById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy tài khoản người dùng"));
        long ownedCount = serverRepository.countByOwnerId(userId);
        return AdminUserResponseDTO.fromUser(user, ownedCount);
    }

    @Transactional(readOnly = true)
    public AdminOwnershipPreviewDTO getOwnershipPreview(Long userId) {
        requireActiveAdminUser();
        validateUserId(userId);
        User targetUser = userRepository.findOneWithAuthoritiesById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy tài khoản người dùng"));

        List<Server> ownedServers = serverRepository.findAllByOwnerIdOrderByIdAsc(targetUser.getId());
        List<AdminOwnershipPreviewDTO.ServerActionPreview> previews = new ArrayList<>();
        int transferableCount = 0;
        int autoDeleteCount = 0;

        for (Server server : ownedServers) {
            List<ServerMember> members = serverMemberRepository.findAllByServerIdOrderByIdAsc(server.getId());
            List<Long> candidateUserIds = members.stream()
                    .map(ServerMember::getUserId)
                    .filter(uid -> !uid.equals(targetUser.getId()))
                    .distinct()
                    .toList();

            Map<Long, User> candidateMap = candidateUserIds.isEmpty()
                    ? Map.of()
                    : userRepository.findByIdIn(candidateUserIds).stream()
                            .filter(u -> Boolean.TRUE.equals(u.getActivated()))
                            .collect(Collectors.toMap(User::getId, Function.identity()));

            User nextOwner = null;
            for (ServerMember member : members) {
                if (!member.getUserId().equals(targetUser.getId())) {
                    User candidate = candidateMap.get(member.getUserId());
                    if (candidate != null) {
                        nextOwner = candidate;
                        break;
                    }
                }
            }

            long activeOtherCount = candidateMap.size();
            if (nextOwner != null) {
                transferableCount++;
                previews.add(AdminOwnershipPreviewDTO.ServerActionPreview.builder()
                        .serverId(server.getId())
                        .serverName(server.getName())
                        .activeMemberCount(activeOtherCount + 1)
                        .action("TRANSFER_OWNERSHIP")
                        .nextOwnerId(nextOwner.getId())
                        .nextOwnerUsername(nextOwner.getUsername())
                        .nextOwnerName(nextOwner.getName())
                        .nextOwnerEmail(nextOwner.getEmail())
                        .build());
            } else {
                autoDeleteCount++;
                previews.add(AdminOwnershipPreviewDTO.ServerActionPreview.builder()
                        .serverId(server.getId())
                        .serverName(server.getName())
                        .activeMemberCount(1L)
                        .action("DELETE_EMPTY_SERVER")
                        .build());
            }
        }

        return AdminOwnershipPreviewDTO.builder()
                .userId(targetUser.getId())
                .email(targetUser.getEmail())
                .username(targetUser.getUsername())
                .name(targetUser.getName())
                .totalOwnedServers(ownedServers.size())
                .transferableCount(transferableCount)
                .autoDeleteCount(autoDeleteCount)
                .servers(previews)
                .build();
    }

    public AdminUserResponseDTO createUser(AdminCreateUserDTO dto) {
        requireActiveAdminUser();
        if (dto == null || dto.getEmail() == null || dto.getEmail().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email không được để trống");
        }
        if (dto.getPassword() == null || dto.getPassword().length() < 6 || dto.getPassword().length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mật khẩu phải từ 6 đến 100 ký tự");
        }

        String normalizedEmail = dto.getEmail().strip().toLowerCase(Locale.ENGLISH);
        if (userRepository.findOneByEmail(normalizedEmail).isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email này đã được sử dụng");
        }

        String normalizedUsername = null;
        if (dto.getUsername() != null && !dto.getUsername().isBlank()) {
            normalizedUsername = dto.getUsername().strip().toLowerCase(Locale.ENGLISH);
            if (userRepository.findOneByUsernameIgnoreCase(normalizedUsername).isPresent()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username này đã được sử dụng");
            }
        }

        User newUser = new User();
        newUser.setEmail(normalizedEmail);
        newUser.setPassword(passwordEncoder.encode(dto.getPassword()));
        if (dto.getName() != null && !dto.getName().isBlank()) {
            newUser.setName(dto.getName().strip());
        } else {
            newUser.setName(normalizedEmail.split("@")[0]);
        }
        if (normalizedUsername != null) {
            newUser.setUsername(normalizedUsername);
        }
        if (dto.getAvatar() != null && !dto.getAvatar().isBlank()) {
            newUser.setAvatar(dto.getAvatar().strip());
        }
        newUser.setType("SYSTEM");
        newUser.setActivated(dto.getActivated() == null || dto.getActivated());
        newUser.setAuthorities(resolveAuthorities(dto.getRole()));

        User saved = userRepository.saveAndFlush(newUser);
        clearUserCaches(saved.getEmail());
        return AdminUserResponseDTO.fromUser(saved, 0L);
    }

    public AdminUserResponseDTO updateUser(Long userId, AdminUpdateUserDTO dto) {
        User currentAdmin = requireActiveAdminUser();
        validateUserId(userId);
        if (dto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dữ liệu cập nhật không hợp lệ");
        }

        User targetUser = userRepository.findOneWithAuthoritiesById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy tài khoản người dùng"));

        String oldEmail = targetUser.getEmail();
        boolean isSelf = currentAdmin.getId().equals(targetUser.getId());

        if (dto.getEmail() != null && !dto.getEmail().isBlank()) {
            String normalizedEmail = dto.getEmail().strip().toLowerCase(Locale.ENGLISH);
            if (!normalizedEmail.equalsIgnoreCase(targetUser.getEmail())) {
                if (userRepository.existsByEmailIgnoreCaseAndIdNot(normalizedEmail, targetUser.getId())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email này đã được sử dụng");
                }
                targetUser.setEmail(normalizedEmail);
            }
        }

        if (dto.getName() != null) {
            String trimmedName = dto.getName().strip();
            targetUser.setName(trimmedName.isEmpty() ? targetUser.getEmail().split("@")[0] : trimmedName);
        }

        if (dto.getUsername() != null && !dto.getUsername().isBlank()) {
            String normalizedUsername = dto.getUsername().strip().toLowerCase(Locale.ENGLISH);
            if (!normalizedUsername.equalsIgnoreCase(targetUser.getUsername())) {
                if (userRepository.existsByUsernameIgnoreCaseAndIdNot(normalizedUsername, targetUser.getId())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username này đã được sử dụng");
                }
                targetUser.setUsername(normalizedUsername);
            }
        }

        if (dto.getAvatar() != null) {
            String trimmedAvatar = dto.getAvatar().strip();
            targetUser.setAvatar(trimmedAvatar.isEmpty() ? null : trimmedAvatar);
        }

        if (dto.getPassword() != null && !dto.getPassword().isBlank()) {
            if (dto.getPassword().length() < 6 || dto.getPassword().length() > 100) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mật khẩu mới phải từ 6 đến 100 ký tự");
            }
            targetUser.setPassword(passwordEncoder.encode(dto.getPassword()));
        }

        if (dto.getRole() != null && !dto.getRole().isBlank()) {
            Set<Authority> newAuthorities = resolveAuthorities(dto.getRole());
            boolean hasAdminRole = newAuthorities.stream().anyMatch(a -> AuthoritiesConstants.ADMIN.equals(a.getName()));
            if (isSelf && !hasAdminRole) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Admin không thể tự hạ quyền của chính mình");
            }
            targetUser.setAuthorities(newAuthorities);
        }

        if (dto.getActivated() != null) {
            boolean requestedActive = dto.getActivated();
            boolean currentlyActive = Boolean.TRUE.equals(targetUser.getActivated());
            if (!requestedActive && isSelf) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Admin không thể tự vô hiệu hóa tài khoản của chính mình");
            }
            if (currentlyActive && !requestedActive) {
                // Soft-deleting via update triggers the same server ownership transfer/cleanup
                executeServerOwnershipSuccessionAndDeactivate(targetUser);
            } else if (!currentlyActive && requestedActive) {
                targetUser.setActivated(true);
                targetUser.setActivationKey(null);
            }
        }

        User saved = userRepository.saveAndFlush(targetUser);
        clearUserCaches(oldEmail);
        clearUserCaches(saved.getEmail());
        long ownedCount = serverRepository.countByOwnerId(saved.getId());
        return AdminUserResponseDTO.fromUser(saved, ownedCount);
    }

    public AdminSoftDeleteResultDTO softDeleteUser(Long userId) {
        User currentAdmin = requireActiveAdminUser();
        validateUserId(userId);
        if (currentAdmin.getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Admin không thể tự xóa mềm tài khoản của chính mình");
        }

        User targetUser = userRepository.findOneWithAuthoritiesById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy tài khoản người dùng"));

        List<AdminSoftDeleteResultDTO.ServerResolutionItem> resolutions = executeServerOwnershipSuccessionAndDeactivate(targetUser);

        User saved = userRepository.saveAndFlush(targetUser);
        clearUserCaches(saved.getEmail());

        int transferred = (int) resolutions.stream().filter(r -> "TRANSFERRED".equals(r.getAction())).count();
        int deletedEmpty = (int) resolutions.stream().filter(r -> "DELETED_EMPTY".equals(r.getAction())).count();

        long remainingOwned = serverRepository.countByOwnerId(saved.getId());
        return AdminSoftDeleteResultDTO.builder()
                .user(AdminUserResponseDTO.fromUser(saved, remainingOwned))
                .transferredServerCount(transferred)
                .deletedEmptyServerCount(deletedEmpty)
                .serverResolutions(resolutions)
                .build();
    }

    public AdminUserResponseDTO restoreUser(Long userId) {
        requireActiveAdminUser();
        validateUserId(userId);
        User targetUser = userRepository.findOneWithAuthoritiesById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy tài khoản người dùng"));

        targetUser.setActivated(true);
        targetUser.setActivationKey(null);
        User saved = userRepository.saveAndFlush(targetUser);
        clearUserCaches(saved.getEmail());
        long ownedCount = serverRepository.countByOwnerId(saved.getId());
        return AdminUserResponseDTO.fromUser(saved, ownedCount);
    }

    private List<AdminSoftDeleteResultDTO.ServerResolutionItem> executeServerOwnershipSuccessionAndDeactivate(User targetUser) {
        List<Server> ownedServers = serverRepository.findAllByOwnerIdForUpdate(targetUser.getId());
        List<AdminSoftDeleteResultDTO.ServerResolutionItem> resolutions = new ArrayList<>();

        for (Server server : ownedServers) {
            List<ServerMember> members = serverMemberRepository.findAllByServerIdOrderByIdAsc(server.getId());
            List<Long> candidateUserIds = members.stream()
                    .map(ServerMember::getUserId)
                    .filter(uid -> !uid.equals(targetUser.getId()))
                    .distinct()
                    .toList();

            Map<Long, User> activeCandidateMap = candidateUserIds.isEmpty()
                    ? Map.of()
                    : userRepository.findByIdIn(candidateUserIds).stream()
                            .filter(u -> Boolean.TRUE.equals(u.getActivated()))
                            .collect(Collectors.toMap(User::getId, Function.identity()));

            User nextOwner = null;
            for (ServerMember member : members) {
                if (!member.getUserId().equals(targetUser.getId())) {
                    User candidate = activeCandidateMap.get(member.getUserId());
                    if (candidate != null) {
                        nextOwner = candidate;
                        break;
                    }
                }
            }

            if (nextOwner != null) {
                // Transfer server ownership to the earliest-joined active member
                server.setOwnerId(nextOwner.getId());
                serverRepository.saveAndFlush(server);
                serverMemberRepository.deleteByServerIdAndUserId(server.getId(), targetUser.getId());

                resolutions.add(AdminSoftDeleteResultDTO.ServerResolutionItem.builder()
                        .serverId(server.getId())
                        .serverName(server.getName())
                        .action("TRANSFERRED")
                        .newOwnerId(nextOwner.getId())
                        .newOwnerUsername(nextOwner.getUsername())
                        .newOwnerName(nextOwner.getName())
                        .build());
            } else {
                // Server has no other active members -> auto-delete the empty server safely
                if (messageRepository.hasConversationReferencesInServer(server.getId())) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Không thể xóa Server trống [" + server.getName() + "] vì dữ liệu tin nhắn đang liên kết với hội thoại riêng.");
                }
                Long deletedServerId = server.getId();
                String deletedServerName = server.getName();
                messageRepository.deleteChannelMessagesByServerId(deletedServerId);
                channelRepository.deleteAllByServerId(deletedServerId);
                inviteRepository.deleteAllByServerId(deletedServerId);
                serverMemberRepository.deleteAllByServerId(deletedServerId);
                serverRepository.delete(server);
                serverRepository.flush();

                resolutions.add(AdminSoftDeleteResultDTO.ServerResolutionItem.builder()
                        .serverId(deletedServerId)
                        .serverName(deletedServerName)
                        .action("DELETED_EMPTY")
                        .build());
            }
        }

        targetUser.setActivated(false);
        targetUser.setSessionId(null);
        targetUser.setRefreshToken(null);
        return resolutions;
    }

    private User requireActiveAdminUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bạn cần đăng nhập để truy cập quản trị");
        }

        String email = SecurityUtils.getCurrentUserLogin()
                .filter(login -> !login.isBlank())
                .map(login -> login.trim().toLowerCase(Locale.ENGLISH))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập không hợp lệ"));

        User currentUser = userRepository.findOneWithAuthoritiesByEmail(email)
                .filter(user -> Boolean.TRUE.equals(user.getActivated()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập không hợp lệ"));

        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> AuthoritiesConstants.ADMIN.equals(a.getAuthority()))
                || currentUser.getAuthorities().stream()
                        .anyMatch(a -> AuthoritiesConstants.ADMIN.equals(a.getName()));

        if (!isAdmin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chỉ Quản trị viên (Admin) mới có quyền thực hiện thao tác này");
        }
        return currentUser;
    }

    private void validateUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User ID không hợp lệ");
        }
    }

    private String normalizeRoleFilter(String role) {
        if (role == null || role.isBlank()) {
            return null;
        }
        String r = role.strip().toUpperCase(Locale.ENGLISH);
        if ("ADMIN".equals(r) || AuthoritiesConstants.ADMIN.equals(r)) {
            return AuthoritiesConstants.ADMIN;
        }
        if ("USER".equals(r) || AuthoritiesConstants.USER.equals(r)) {
            return AuthoritiesConstants.USER;
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vai trò lọc không hợp lệ (chỉ hỗ trợ ADMIN hoặc USER)");
    }

    private Set<Authority> resolveAuthorities(String rawRole) {
        Set<Authority> authorities = new HashSet<>();
        Authority userAuth = authorityRepository.findById(AuthoritiesConstants.USER)
                .orElseGet(() -> {
                    Authority a = new Authority();
                    a.setName(AuthoritiesConstants.USER);
                    return authorityRepository.save(a);
                });
        authorities.add(userAuth);

        if (rawRole != null && !rawRole.isBlank()) {
            String r = rawRole.strip().toUpperCase(Locale.ENGLISH);
            if ("ADMIN".equals(r) || AuthoritiesConstants.ADMIN.equals(r)) {
                Authority adminAuth = authorityRepository.findById(AuthoritiesConstants.ADMIN)
                        .orElseGet(() -> {
                            Authority a = new Authority();
                            a.setName(AuthoritiesConstants.ADMIN);
                            return authorityRepository.save(a);
                        });
                authorities.add(adminAuth);
            } else if (!"USER".equals(r) && !AuthoritiesConstants.USER.equals(r)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vai trò không hợp lệ (chỉ hỗ trợ ADMIN hoặc USER)");
            }
        }
        return authorities;
    }

    private void clearUserCaches(String email) {
        if (email == null) {
            return;
        }
        var cacheByEmail = cacheManager.getCache(UserRepository.USERS_BY_EMAIL_CACHE);
        if (cacheByEmail != null) {
            cacheByEmail.evict(email.trim().toLowerCase(Locale.ENGLISH));
        }
    }
}

