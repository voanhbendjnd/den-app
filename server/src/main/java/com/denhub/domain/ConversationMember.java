package com.denhub.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

@Entity
@Table(name = "conversation_member", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "conversation_id"},
        name = "ux_conversation_member")
})
public class ConversationMember {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @NotNull
    @Column(name = "user_id",  nullable = false)
    private Long userId;
    @NotNull
    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;
    @Column(name = "joined_at")
    private Instant joinedAt;
    @Column(name = "unread_count")
    private int unreadCount;
    private Boolean isMuted;
    private Boolean isDeleted;
    public ConversationMember() {}
    public ConversationMember(Long id, Long userId, Long conversationId, Instant joinedAt, int unreadCount, Boolean isMuted, Boolean isDeleted) {
        this.id = id;
        this.userId = userId;
        this.conversationId = conversationId;
        this.joinedAt = joinedAt;
        this.unreadCount = unreadCount;
        this.isMuted = isMuted;
        this.isDeleted = isDeleted;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getConversationId() {
        return conversationId;
    }

    public void setConversationId(Long conversationId) {
        this.conversationId = conversationId;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(Instant joinedAt) {
        this.joinedAt = joinedAt;
    }

    public int getUnreadCount() {
        return unreadCount;
    }

    public void setUnreadCount(int unreadCount) {
        this.unreadCount = unreadCount;
    }

    public Boolean getMuted() {
        return isMuted;
    }

    public void setMuted(Boolean muted) {
        isMuted = muted;
    }

    public Boolean getDeleted() {
        return isDeleted;
    }

    public void setDeleted(Boolean deleted) {
        isDeleted = deleted;
    }
}
