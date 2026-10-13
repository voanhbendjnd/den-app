package com.denhub.service;

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
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
        User currentUser = getCurrentUser();
        validateServerAccess(dto.getServerId(), currentUser.getId());

        String channelName = dto.getName().trim();
        if (channelRepository.existsByServerIdAndName(dto.getServerId(), channelName)) {
            throw new BadRequestAlertException("Channel with name '" + channelName + "' already exists in this server", "channel", "namealreadyexists");
        }

        int currentCount = channelRepository.countByServerId(dto.getServerId());

        Channel channel = new Channel();
        channel.setServerId(dto.getServerId());
        channel.setName(channelName);
        channel.setPosition(currentCount);

        Channel savedChannel = channelRepository.save(channel);
        return toChannelResponseDTO(savedChannel);
    }

    @Transactional(readOnly = true)
    public List<ChannelResponseDTO> getChannelsByServerId(Long serverId) {
        User currentUser = getCurrentUser();
        validateServerAccess(serverId, currentUser.getId());

        return channelRepository.findAllByServerIdOrderByPositionAsc(serverId)
                .stream()
                .map(this::toChannelResponseDTO)
                .toList();
    }

    // --- Helper Methods ---

    private User getCurrentUser() {
        String currentUserEmail = SecurityUtils.getCurrentUserLogin()
                .orElseThrow(() -> new BadRequestAlertException("User not found in security context", "channel", "notloggedin"));

        return userRepository.findOneByEmail(currentUserEmail)
                .orElseThrow(() -> new BadRequestAlertException("User not found in database", "channel", "usernotfound"));
    }

    private Server validateServerAccess(Long serverId, Long userId) {
        Server server = serverRepository.findById(serverId)
                .orElseThrow(() -> new BadRequestAlertException("Server not found with id: " + serverId, "channel", "servernotfound"));

        boolean isOwner = server.getOwnerId() != null && server.getOwnerId().equals(userId);
        boolean isMember = serverMemberRepository.existsByServerIdAndUserId(serverId, userId);
        if (!isOwner && !isMember) {
            throw new BadRequestAlertException("You do not have permission to access channels in this server", "channel", "forbidden");
        }
        return server;
    }

    private ChannelResponseDTO toChannelResponseDTO(Channel channel) {
        ChannelResponseDTO dto = new ChannelResponseDTO();
        dto.setId(channel.getId());
        dto.setServerId(channel.getServerId());
        dto.setName(channel.getName());
        dto.setPosition(channel.getPosition());
        dto.setCreatedDate(channel.getCreatedDate());
        return dto;
    }
}
