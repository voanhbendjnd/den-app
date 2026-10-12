package com.denhub.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.denhub.domain.Message;

@Repository
public interface MessageRepository extends JpaRepository<Message,Long> {
}
