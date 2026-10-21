package com.denhub.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.denhub.domain.Server;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

@Repository
public interface ServerRepository extends JpaRepository<Server, Long> {

    // All server-dependent writes take this lock before checking access or mutating data.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Server s WHERE s.id = :id")
    Optional<Server> findByIdForUpdate(@Param("id") Long id);

    @Query("SELECT DISTINCT s FROM Server s WHERE s.ownerId = :userId OR s.id IN (SELECT sm.serverId FROM ServerMember sm WHERE sm.userId = :userId) ORDER BY s.id DESC")
    List<Server> findAllByUserIdOrOwnerId(@Param("userId") Long userId);
}

