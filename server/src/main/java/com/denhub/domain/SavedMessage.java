package com.denhub.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name="saved_messages", uniqueConstraints=@UniqueConstraint(columnNames= {"user_id", "message_id"}))
public class SavedMessage {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @Column(name="user_id", nullable=false)
    private Long userId;
    @Column(name="message_id", nullable=false)
    private Long messageId;
    @Column(name="saved_at", nullable=false)
    private java.time.Instant savedAt;
}
