package com.denhub.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.denhub.domain.Server;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

@Repository
public interface ServerRepository extends JpaRepository<Server, Long> {

    @Query(value = """
        SELECT s FROM Server s
        WHERE s.visibility = com.denhub.domain.enums.ServerVisibility.PUBLIC
          AND s.ownerId <> :userId
          AND NOT EXISTS (SELECT m.id FROM ServerMember m WHERE m.serverId = s.id AND m.userId = :userId)
          AND LOWER(s.name) LIKE :pattern ESCAPE '!'
        ORDER BY CASE WHEN LOWER(s.name) = :term THEN 0
                      WHEN LOWER(s.name) LIKE :prefix ESCAPE '!' THEN 1 ELSE 2 END,
                 s.id DESC
        """, countQuery = """
        SELECT COUNT(s) FROM Server s
        WHERE s.visibility = com.denhub.domain.enums.ServerVisibility.PUBLIC
          AND s.ownerId <> :userId
          AND NOT EXISTS (SELECT m.id FROM ServerMember m WHERE m.serverId = s.id AND m.userId = :userId)
          AND LOWER(s.name) LIKE :pattern ESCAPE '!'
        """)
    Page<Server> discover(@Param("userId") Long userId, @Param("term") String term, @Param("pattern") String pattern,
                          @Param("prefix") String prefix, Pageable pageable);

    // All server-dependent writes take this lock before checking access or mutating data.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Server s WHERE s.id = :id")
    Optional<Server> findByIdForUpdate(@Param("id") Long id);

    @Query("SELECT DISTINCT s FROM Server s WHERE s.ownerId = :userId OR s.id IN (SELECT sm.serverId FROM ServerMember sm WHERE sm.userId = :userId) ORDER BY s.id DESC")
    List<Server> findAllByUserIdOrOwnerId(@Param("userId") Long userId);
}

