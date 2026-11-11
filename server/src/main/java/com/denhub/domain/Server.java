package com.denhub.domain;

import jakarta.persistence.*;
import com.denhub.domain.enums.ServerVisibility;
import lombok.Getter;
import lombok.Setter;
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
@Getter
@Setter
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

    @Getter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 7, columnDefinition = "NVARCHAR(7)")
    private ServerVisibility visibility = ServerVisibility.PRIVATE;



}
