package com.denhub.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name="channel_notification_preferences", uniqueConstraints=@UniqueConstraint(columnNames= {"user_id", "channel_id"}))
public class ChannelNotificationPreference {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @Column(name="user_id", nullable=false)
    private Long userId;
    @Column(name="channel_id", nullable=false)
    private Long channelId;
    @Column(name="muted", nullable=false)
    private Boolean muted;
}
