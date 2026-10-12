package com.denhub.service;

import com.denhub.domain.Channel;
import com.denhub.domain.Message;
import com.denhub.domain.User;
import com.denhub.repository.ChannelRepository;
import com.denhub.repository.MessageRepository;
import com.denhub.repository.ServerMemberRepository;
import com.denhub.repository.UserRepository;
import com.denhub.security.SecurityUtils;
import com.denhub.service.dto.MessageResponse;
import com.denhub.service.dto.SendMessageRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.Set;

@Service
public class MessageService {
    private final MessageRepository messageRepository;
    private final ChannelRepository channelRepository;
    private final ServerMemberRepository serverMemberRepository;
    private final UserRepository userRepository;
    private final Validator validator;

    public MessageService(MessageRepository messageRepository,
                          ChannelRepository channelRepository,
                          ServerMemberRepository serverMemberRepository,
                          UserRepository userRepository,
                          Validator validator) {
        this.messageRepository = messageRepository;
        this.channelRepository = channelRepository;
        this.serverMemberRepository = serverMemberRepository;
        this.userRepository = userRepository;
        this.validator = validator;
    }

    @Transactional
    public MessageResponse sendMessage(Long channelId, SendMessageRequest request) {
        String email = getAuthenticatedEmail();
        validateRequest(channelId, request);

        User sender = userRepository.findOneByEmail(email)
                .filter(user -> Boolean.TRUE.equals(user.getActivated()))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Phiên đăng nhập không hợp lệ"));

        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Không tìm thấy Channel"));

        if (!serverMemberRepository.existsByServerIdAndUserId(channel.getServerId(), sender.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Bạn không có quyền gửi tin nhắn trong Channel này");
        }

        Message message = new Message();
        message.setChannelId(channel.getId());
        message.setAuthorId(sender.getId());
        message.setContent(request.content());

        Message savedMessage = messageRepository.save(message);
        return new MessageResponse(
                savedMessage.getId(),
                savedMessage.getChannelId(),
                savedMessage.getAuthorId(),
                savedMessage.getContent(),
                savedMessage.getCreatedDate());
    }

    private String getAuthenticatedEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bạn cần đăng nhập để gửi tin nhắn");
        }

        return SecurityUtils.getCurrentUserLogin()
                .filter(login -> !login.isBlank())
                .map(login -> login.trim().toLowerCase(Locale.ENGLISH))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Phiên đăng nhập không hợp lệ"));
    }

    private void validateRequest(Long channelId, SendMessageRequest request) {
        if (channelId == null || request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Yêu cầu gửi tin nhắn không hợp lệ");
        }

        Set<ConstraintViolation<SendMessageRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, violations.iterator().next().getMessage());
        }
    }
}
