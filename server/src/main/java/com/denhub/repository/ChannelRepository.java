package com.denhub.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.denhub.domain.Channel;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChannelRepository extends JpaRepository<Channel, Long> {

    boolean existsByServerIdAndName(Long serverId, String name);

    List<Channel> findAllByServerIdOrderByPositionAsc(Long serverId);

    int countByServerId(Long serverId);

    Optional<Channel> findByIdAndServerId(Long id, Long serverId);
}
