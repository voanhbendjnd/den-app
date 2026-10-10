package com.denhub.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.io.Serial;
import java.io.Serializable;

@Entity
@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
@Table(name = "friendships", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "friend_id"} , name = "ux_friendship"
        )
})
public class Friendship extends AbstractAuditingEntity<Long> implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    Long id;
    @Column(name = "user_one_id", nullable = false)
    Long userOneId;
    @Column(name = "user_two_id", nullable = false)
    Long userTwoId;
    // enum (PENDING, ACCEPT, BLOCKED)
    String status;
    Long actionUserId;
}
