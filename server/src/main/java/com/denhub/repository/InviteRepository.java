package com.denhub.repository;

import com.denhub.domain.Invite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InviteRepository extends JpaRepository<Invite, Long> {

    boolean existsByCode(String code);

    Optional<Invite> findByCode(String code);

    List<Invite> findAllByServerId(Long serverId);
}
