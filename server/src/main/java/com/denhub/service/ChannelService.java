package com.denhub.service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.denhub.domain.Channel;
import com.denhub.domain.Server;
import com.denhub.domain.User;
import com.denhub.repository.ChannelRepository;
import com.denhub.repository.ServerMemberRepository;
import com.denhub.repository.ServerRepository;
import com.denhub.repository.UserRepository;
import com.denhub.security.SecurityUtils;
import com.denhub.service.dto.ChannelResponseDTO;
import com.denhub.service.dto.CreateChannelDTO;
import com.denhub.web.rest.errors.BadRequestAlertException;

@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor
@Transactional
public class ChannelService {

    ChannelRepository channelRepository;
    ServerRepository serverRepository;
    ServerMemberRepository serverMemberRepository;
    UserRepository userRepository;

    public ChannelResponseDTO createChannel(CreateChannelDTO dto) {
        // 1. Lấy thông tin user đăng nhập hiện tại
        String currentUserEmail = SecurityUtils.getCurrentUserLogin()
                .orElseThrow(() -> new BadRequestAlertException("User not found in security context", "channel", "notloggedin"));

        User currentUser = userRepository.findOneByEmail(currentUserEmail)
                .orElseThrow(() -> new BadRequestAlertException("User not found in database", "channel", "usernotfound"));

        // 2. Kiểm tra server có tồn tại không
        Server server = serverRepository.findById(dto.getServerId())
                .orElseThrow(() -> new BadRequestAlertException("Server not found with id: " + dto.getServerId(), "channel", "servernotfound"));

        // 3. Kiểm tra quyền: Người tạo phải là Server Owner hoặc Thành viên trong Server
        boolean isOwner = server.getOwnerId() != null && server.getOwnerId().equals(currentUser.getId());
        boolean isMember = serverMemberRepository.existsByServerIdAndUserId(dto.getServerId(), currentUser.getId());
        if (!isOwner && !isMember) {
            throw new BadRequestAlertException("You do not have permission to create a channel in this server", "channel", "forbidden");
        }

        // 4. Chuẩn hóa tên channel và kiểm tra trùng tên trong server
        String channelName = dto.getName().trim();
        if (channelRepository.existsByServerIdAndName(dto.getServerId(), channelName)) {
            throw new BadRequestAlertException("Channel with name '" + channelName + "' already exists in this server", "channel", "namealreadyexists");
        }

        // 5. Tự động tính position dựa trên số lượng channel hiện tại trong server
        int currentCount = channelRepository.countByServerId(dto.getServerId());

        // 6. Tạo và lưu Channel
        Channel channel = new Channel();
        channel.setServerId(dto.getServerId());
        channel.setName(channelName);
        channel.setPosition(currentCount);

        channel = channelRepository.save(channel);

        // 7. Chuyển đổi sang Response DTO
        ChannelResponseDTO response = new ChannelResponseDTO();
        response.setId(channel.getId());
        response.setServerId(channel.getServerId());
        response.setName(channel.getName());
        response.setPosition(channel.getPosition());
        response.setCreatedDate(channel.getCreatedDate());

        return response;
    }
}
