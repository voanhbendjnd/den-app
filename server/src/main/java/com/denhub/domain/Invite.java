package com.denhub.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(name = "invites", uniqueConstraints = {
    @UniqueConstraint(name = "uq_invites_code", columnNames = {"code"})
})
@Getter
@Setter
public class Invite implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "server_id", nullable = false)
    private Long serverId;

    @NotNull
    @Size(max = 32)
    @Column(name = "code", length = 32, nullable = false, unique = true)
    private String code;

    @NotNull
    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @NotNull
    @Size(max = 20)
    @Column(name = "status", length = 20, nullable = false)
    private String status;

    @NotNull
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
