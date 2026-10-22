package com.denhub.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.denhub.domain.Channel;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChannelRepository extends JpaRepository<Channel, Long> {

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM Channel c WHERE c.serverId = :serverId")
    int deleteAllByServerId(@Param("serverId") Long serverId);

    boolean existsByServerIdAndName(Long serverId, String name);

    List<Channel> findAllByServerIdOrderByPositionAsc(Long serverId);

    int countByServerId(Long serverId);

    Optional<Channel> findByIdAndServerId(Long id, Long serverId);

    boolean existsByServerIdAndNameAndIdNot(Long serverId, String name, Long id);
}
