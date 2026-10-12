package com.denhub.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.denhub.domain.Server;

import java.util.List;

@Repository
public interface ServerRepository extends JpaRepository<Server, Long> {

    @Query("SELECT DISTINCT s FROM Server s WHERE s.ownerId = :userId OR s.id IN (SELECT sm.serverId FROM ServerMember sm WHERE sm.userId = :userId) ORDER BY s.id DESC")
    List<Server> findAllByUserIdOrOwnerId(@Param("userId") Long userId);
}

