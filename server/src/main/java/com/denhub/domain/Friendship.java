package com.denhub.domain;

import jakarta.persistence.*;
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
        @UniqueConstraint(columnNames = {"user_one_id", "user_two_id"}, name = "ux_friendship")
})
public class Friendship extends AbstractAuditingEntity<Long> implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "user_one_id", nullable = false)
    Long userOneId;

    @Column(name = "user_two_id", nullable = false)
    Long userTwoId;

    // enum (PENDING, ACCEPT, BLOCKED)
    @Column(name = "status")
    String status;

    @Column(name = "action_user_id")
    Long actionUserId;
}
