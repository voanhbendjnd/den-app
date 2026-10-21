package com.denhub.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.denhub.domain.ServerMember;

import java.util.List;

@Repository
public interface ServerMemberRepository extends JpaRepository<ServerMember, Long> {
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM ServerMember m WHERE m.serverId = :serverId")
    int deleteAllByServerId(@Param("serverId") Long serverId);
    boolean existsByServerIdAndUserId(Long serverId, Long userId);
    List<ServerMember> findAllByServerId(Long serverId);
}
