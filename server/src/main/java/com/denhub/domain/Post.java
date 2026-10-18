package com.denhub.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.io.Serializable;

@Entity
@Table(name = "posts")
@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Post extends AbstractAuditingEntity<Long> implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    @Column(name = "user_id", nullable = false)
    Long userId;
    @Column(name = "content", columnDefinition = "NVARCHAR(120)")
    String content;
    // public, FRIENDS_ONLY, PRIVATE
    String privacy;
    @Column(name = "like_counts")
    Integer likeCounts;
    
}
