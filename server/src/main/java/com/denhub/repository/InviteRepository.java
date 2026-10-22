package com.denhub.repository;

import com.denhub.domain.Invite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InviteRepository extends JpaRepository<Invite, Long> {

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM Invite i WHERE i.serverId = :serverId")
    int deleteAllByServerId(@Param("serverId") Long serverId);

    boolean existsByCode(String code);

    Optional<Invite> findByCode(String code);

    List<Invite> findAllByServerId(Long serverId);
}
