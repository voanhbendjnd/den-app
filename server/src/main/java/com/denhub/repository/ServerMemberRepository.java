package com.denhub.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.denhub.domain.ServerMember;

@Repository
public interface ServerMemberRepository extends JpaRepository<ServerMember,Long> {
    boolean existsByServerIdAndUserId(Long serverId, Long userId);
}
