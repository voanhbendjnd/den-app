package com.denhub.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.validator.constraints.Length;
import org.hibernate.annotations.Cache;

import java.io.Serial;
import java.io.Serializable;

@Entity
@Table(name = "servers")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)

public class Server extends AbstractAuditingEntity<Long> implements Serializable{
    @Serial
    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Length(max = 20, min = 1)
    @Column(nullable = false, length = 20, columnDefinition = "NVARCHAR(20)")
    private String name;
    @Column(name = "icon_url", length = 255)
    private String iconUrl;
    @Column(name = "owner_id",  nullable = false)
    private Long ownerId;

    @Override
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getIconUrl() {
        return iconUrl;
    }

    public void setIconUrl(String iconUrl) {
        this.iconUrl = iconUrl;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }
}
