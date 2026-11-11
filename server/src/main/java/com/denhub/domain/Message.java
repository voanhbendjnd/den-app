package com.denhub.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.springframework.data.annotation.CreatedDate;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(name = "messages")
@Getter
@Setter
@Builder
@AttributeOverrides({
        @AttributeOverride(name = "createdBy", column = @Column(name = "created_by", updatable = false, length = 254)),
        @AttributeOverride(name = "lastModifiedBy", column = @Column(name = "last_modified_by", length = 254))
})
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class Message extends AbstractAuditingEntity<Long> implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "channel_id")
    private Long channelId;

    @Column(name = "author_id")
    private Long authorId;

    @Column(name = "sender_id")
    private Long senderId;

    @Column(name = "conversation_id")
    private Long conversationId;

    @Column(name = "type")
    private String type;

    @Column(name = "content", columnDefinition = "NVARCHAR(MAX)")
    private String content;

    @Column(name = "media_url", columnDefinition = "NVARCHAR(1000)")
    private String mediaUrl;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "width")
    private Integer width;

    @Column(name = "height")
    private Integer height;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    @JsonIgnore
    @Builder.Default
    private Instant createdAt = Instant.now();

    public Message() {}

    public Message(Long id, Long channelId, Long authorId, Long senderId, Long conversationId,
                   String type, String content, String mediaUrl, String fileName, Long fileSize,
                   Integer width, Integer height, Instant createdAt) {
        this.id = id;
        this.channelId = channelId;
        this.authorId = authorId;
        this.senderId = senderId;
        this.conversationId = conversationId;
        this.type = type;
        this.content = content;
        this.mediaUrl = mediaUrl;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.width = width;
        this.height = height;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }

    @Override
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSenderId() {
        return senderId != null ? senderId : authorId;
    }

    public Long getAuthorId() {
        return authorId != null ? authorId : senderId;
    }

    public Instant getCreatedAt() {
        if (createdAt != null) {
            return createdAt;
        }
        return getCreatedDate();
    }
}
