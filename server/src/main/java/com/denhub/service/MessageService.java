package com.denhub.service;

import com.denhub.domain.Channel;
import com.denhub.domain.Message;
import com.denhub.domain.User;
import com.denhub.repository.ChannelRepository;
import com.denhub.repository.MessageRepository;
import com.denhub.repository.ServerMemberRepository;
import com.denhub.repository.ServerRepository;
import com.denhub.repository.UserRepository;
import com.denhub.security.SecurityUtils;
import com.denhub.service.dto.CursorPageResponse;
import com.denhub.service.dto.MessageResponse;
import com.denhub.service.dto.SendMessageRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class MessageService {
    private final MessageRepository messageRepository;
    private final ChannelRepository channelRepository;
    private final ServerMemberRepository serverMemberRepository;
    private final UserRepository userRepository;
    private final Validator validator;
    private final ServerRepository serverRepository;

    public MessageService(MessageRepository messageRepository,
                          ChannelRepository channelRepository,
                          ServerMemberRepository serverMemberRepository,
                          UserRepository userRepository,
                          Validator validator,
                          ServerRepository serverRepository) {
        this.messageRepository = messageRepository;
        this.channelRepository = channelRepository;
        this.serverMemberRepository = serverMemberRepository;
        this.userRepository = userRepository;
        this.validator = validator;
        this.serverRepository = serverRepository;
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

        // Serialize channel writes with deletion, including requests that read the channel earlier.
        serverRepository.findByIdForUpdate(channel.getServerId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy Server"));

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

    @Transactional(readOnly = true)
    public CursorPageResponse<MessageResponse> getMessages(Long channelId, Long before, Integer limit) {
        String email = getAuthenticatedEmail();

        if (channelId == null || channelId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ID của Channel không hợp lệ");
        }

        int effectiveLimit = (limit != null) ? limit : 30;
        if (effectiveLimit < 1 || effectiveLimit > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số lượng tin nhắn phải từ 1 đến 100");
        }

        if (before != null && before <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Con trỏ phân trang không hợp lệ");
        }

        User currentUser = userRepository.findOneByEmail(email)
                .filter(user -> Boolean.TRUE.equals(user.getActivated()))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Phiên đăng nhập không hợp lệ"));

        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Không tìm thấy Channel"));

        if (!serverMemberRepository.existsByServerIdAndUserId(channel.getServerId(), currentUser.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Bạn không có quyền xem tin nhắn trong Channel này");
        }

        PageRequest pageRequest = PageRequest.of(0, effectiveLimit + 1);
        List<Message> messages = (before == null)
                ? messageRepository.findByChannelIdOrderByIdDesc(channel.getId(), pageRequest)
                : messageRepository.findByChannelIdAndIdLessThanOrderByIdDesc(channel.getId(), before, pageRequest);

        boolean hasMore = messages.size() > effectiveLimit;
        List<Message> pagedMessages = hasMore ? messages.subList(0, effectiveLimit) : messages;

        Long nextCursor = null;
        if (hasMore && !pagedMessages.isEmpty()) {
            nextCursor = pagedMessages.get(pagedMessages.size() - 1).getId();
        }

        List<MessageResponse> items = pagedMessages.stream()
                .map(msg -> new MessageResponse(
                        msg.getId(),
                        msg.getChannelId(),
                        msg.getAuthorId(),
                        msg.getContent(),
                        msg.getCreatedDate()))
                .toList();

        return new CursorPageResponse<>(items, nextCursor, hasMore, effectiveLimit);
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
