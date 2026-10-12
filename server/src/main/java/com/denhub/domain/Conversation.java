package com.denhub.domain;

import jakarta.persistence.*;
import lombok.Builder;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import java.io.Serializable;

@Entity
@Table(name = "conversations")
@Builder
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class Conversation extends AbstractAuditingEntity<Long> implements Serializable{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    // ConversationType Enum
    private String type;
    @Column(name = "last_message_id")
    private Long lastMessageId;
    public Conversation() {}
    public Conversation(Long id, String type, Long lastMessageId) {
        this.id = id;
        this.type = type;
        this.lastMessageId = lastMessageId;
    }

    @Override
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Long getLastMessageId() {
        return lastMessageId;
    }

    public void setLastMessageId(Long lastMessageId) {
        this.lastMessageId = lastMessageId;
    }
}
