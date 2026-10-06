package com.denhub.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.Length;

import java.io.Serial;
import java.io.Serializable;

@Entity
@Table(name = "channels", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"name", "server_id"}, name = "ux_name_server")
})

public class Channel extends AbstractAuditingEntity<Long> implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @NotNull
    @Column(name = "server_id", nullable = false)
    private Long serverId;
    @NotNull
    @Length(max = 20, min = 2)
    @Column(name = "name", nullable = false, length = 20, columnDefinition = "NVARCHAR(20)")
    private String name;
    int position;

    @Override
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }
}
