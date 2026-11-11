package com.denhub.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name="message_pins", uniqueConstraints=@UniqueConstraint(columnNames="message_id"))
public class MessagePin {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @Column(name="message_id", nullable=false)
    private Long messageId;
    @Column(name="pinned_by", nullable=false)
    private Long pinnedBy;
    @Column(name="pinned_at", nullable=false)
    private java.time.Instant pinnedAt;
}
