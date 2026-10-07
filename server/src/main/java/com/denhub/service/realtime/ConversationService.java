package com.denhub.service.realtime;

import com.denhub.domain.Conversation;
import com.denhub.domain.ConversationMember;
import com.denhub.domain.Message;
import com.denhub.domain.User;
import com.denhub.domain.enums.ConversationType;
import com.denhub.domain.enums.MessageType;
import com.denhub.repository.ConversationMemberRepository;
import com.denhub.repository.ConversationRepository;
import com.denhub.repository.MessageRepository;
import com.denhub.repository.UserRepository;
import com.denhub.service.errors.BadRequestResourceException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class ConversationService {
    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository conversationMemberRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate simpMessagingTemplate;

    public ConversationService(ConversationRepository conversationRepository,
                               ConversationMemberRepository conversationMemberRepository,
                               MessageRepository messageRepository,
                               UserRepository userRepository,
                               SimpMessagingTemplate simpMessagingTemplate) {
        this.conversationRepository = conversationRepository;
        this.conversationMemberRepository = conversationMemberRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.simpMessagingTemplate = simpMessagingTemplate;
    }

    /*
     * Process and send message (TEXT or IMAGE) between 2 users
     */
    @Transactional
    public SendMessageRes processAndSendMessage(SendMessageReq req, Principal principal) {
        if (principal == null || principal.getName() == null) {
            throw new BadRequestResourceException("Unauthorized user", "conversationManagement", "unauthorized");
        }
        Long senderId = this.checkNumber(principal.getName());
        User senderUser = userRepository.findById(senderId)
                .orElseThrow(() -> new BadRequestResourceException("Sender not found", "conversationManagement", "senderNotFound"));

        Conversation conversation;
        if (req.getConversationId() != null) {
            conversation = conversationRepository.findById(req.getConversationId())
                    .orElseThrow(() -> new BadRequestResourceException("Conversation not found", "conversationManagement", "idnotfound"));

            boolean isMember = conversationMemberRepository.existsByConversationIdAndUserId(conversation.getId(), senderId);
            if (!isMember) {
                throw new BadRequestResourceException("User is not member of this conversation", "conversationManagement", "notMember");
            }
        } else {
            if (req.getTargetUserId() == null) {
                throw new BadRequestResourceException("Target user ID is required when conversationId is null", "conversationManagement", "targetRequired");
            }
            if (!userRepository.existsById(req.getTargetUserId())) {
                throw new BadRequestResourceException("Target user not found", "conversationManagement", "targetNotFound");
            }
            conversation = this.getOrCreateDirectConversation(senderId, req.getTargetUserId());
        }

        String messageType = (req.getType() != null && !req.getType().trim().isEmpty())
                ? req.getType().toUpperCase()
                : MessageType.TEXT.name();

        Message saveMessage = Message.builder()
                .senderId(senderId)
                .conversationId(conversation.getId())
                .type(messageType)
                .content(req.getContent())
                .mediaUrl(req.getMediaUrl())
                .createdAt(Instant.now())
                .build();

        if (req.getMetadata() != null) {
            saveMessage.setFileName(req.getMetadata().getFileName());
            saveMessage.setFileSize(req.getMetadata().getFileSize());
            saveMessage.setWidth(req.getMetadata().getWidth());
            saveMessage.setHeight(req.getMetadata().getHeight());
        }

        saveMessage = messageRepository.save(saveMessage);

        conversation.setLastMessageId(saveMessage.getId());
        conversationRepository.save(conversation);

        SendMessageRes res = toRes(saveMessage, conversation.getId(), senderUser);

        // Broadcast real-time message via STOMP WebSocket
        simpMessagingTemplate.convertAndSend("/topic/conversations/" + conversation.getId(), res);

        return res;
    }

    /*
     * Get or create direct 1-to-1 conversation
     */
    private Conversation getOrCreateDirectConversation(Long senderId, Long targetId) {
        Optional<Conversation> existingConv = conversationRepository.findDirectConversationBetweenUsers(
                ConversationType.DIRECT.name(), senderId, targetId);
        if (existingConv.isPresent()) {
            return existingConv.get();
        }

        Conversation conversation = new Conversation();
        conversation.setType(ConversationType.DIRECT.name());
        conversation = conversationRepository.save(conversation);

        ConversationMember sender = new ConversationMember();
        sender.setUserId(senderId);
        sender.setConversationId(conversation.getId());
        sender.setJoinedAt(Instant.now());

        ConversationMember target = new ConversationMember();
        target.setUserId(targetId);
        target.setConversationId(conversation.getId());
        target.setJoinedAt(Instant.now());

        conversationMemberRepository.saveAll(List.of(sender, target));
        return conversation;
    }

    private Long checkNumber(String id) {
        try {
            return Long.parseLong(id);
        } catch (Exception e) {
            throw new BadRequestResourceException("Invalid number", "conversationManagement", "numberinvalid");
        }
    }

    private SendMessageRes toRes(Message saveMessage, Long conversationId, User senderUser) {
        SendMessageRes.Sender senderDto = SendMessageRes.Sender.builder()
                .userId(senderUser.getId())
                .name(senderUser.getName() != null ? senderUser.getName() : senderUser.getEmail())
                .avatar(senderUser.getAvatar() != null ? senderUser.getAvatar() : null)
                .build();

        SendMessageRes.Metadata metadataDto = null;
        if (saveMessage.getFileName() != null || saveMessage.getWidth() != null || saveMessage.getFileSize() != null) {
            metadataDto = SendMessageRes.Metadata.builder()
                    .fileName(saveMessage.getFileName())
                    .fileSize(saveMessage.getFileSize() != null ? saveMessage.getFileSize() : 0L)
                    .width(saveMessage.getWidth() != null ? saveMessage.getWidth() : 0)
                    .height(saveMessage.getHeight() != null ? saveMessage.getHeight() : 0)
                    .build();
        }

        return SendMessageRes.builder()
                .messageId(saveMessage.getId())
                .conversationId(conversationId)
                .sender(senderDto)
                .type(saveMessage.getType())
                .content(saveMessage.getContent())
                .mediaUrl(saveMessage.getMediaUrl())
                .metadata(metadataDto)
                .status("SENT")
                .createdAt(saveMessage.getCreatedAt())
                .build();
    }
}

