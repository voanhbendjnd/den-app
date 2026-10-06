package com.denhub.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.Length;

@Entity
@Table(name = "server_member", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"server_id", "user_id"}, name = "ux_server_member")
})
public class ServerMember {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @NotNull
    @Column(name = "server_id",  nullable = false)
    private Long serverId;
    @NotNull
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Length(max = 20, min = 1)
    @Column(name = "nickname", length = 20, nullable = false, columnDefinition = "NVARCHAR(20)")
    private String nickname;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getServerId() {
        return serverId;
    }

    public void setServerId(Long serverId) {
        this.serverId = serverId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }
}
