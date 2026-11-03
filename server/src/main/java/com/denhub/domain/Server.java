package com.denhub.domain;

import jakarta.persistence.*;
import com.denhub.domain.enums.ServerVisibility;
import org.hibernate.validator.constraints.Length;

import java.io.Serial;
import java.io.Serializable;

@Entity
@Table(name = "servers")
// Cached enum instances can belong to an old DevTools restart classloader.
@Cacheable(false)
@AttributeOverrides({
    @AttributeOverride(name = "createdBy", column = @Column(name = "created_by", length = 254, updatable = false)),
    @AttributeOverride(name = "lastModifiedBy", column = @Column(name = "last_modified_by", length = 254))
})

public class Server extends AbstractAuditingEntity<Long> implements Serializable{
    @Serial
    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Length(max = 100, min = 1)
    @Column(nullable = false, length = 100, columnDefinition = "NVARCHAR(100)")
    private String name;
    @Column(name = "icon_url", length = 255)
    private String iconUrl;
    @Column(name = "owner_id",  nullable = false)
    private Long ownerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 7, columnDefinition = "NVARCHAR(7)")
    private ServerVisibility visibility = ServerVisibility.PRIVATE;

    public ServerVisibility getVisibility() {
        return visibility;
    }

    public void setVisibility(ServerVisibility visibility) {
        this.visibility = visibility;
    }

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
