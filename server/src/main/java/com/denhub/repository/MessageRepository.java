package com.denhub.repository;

import com.denhub.domain.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    @Query("""
        SELECT COUNT(m) > 0 FROM Message m
        WHERE m.channelId IN (SELECT c.id FROM Channel c WHERE c.serverId = :serverId)
          AND (m.conversationId IS NOT NULL
               OR EXISTS (SELECT d.id FROM Conversation d WHERE d.lastMessageId = m.id))
        """)
    boolean hasConversationReferencesInServer(@Param("serverId") Long serverId);

    @Modifying(flushAutomatically = true)
    @Query("""
        DELETE FROM Message m WHERE m.conversationId IS NULL
          AND m.channelId IN (SELECT c.id FROM Channel c WHERE c.serverId = :serverId)
        """)
    int deleteChannelMessagesByServerId(@Param("serverId") Long serverId);


    Page<Message> findByConversationIdOrderByCreatedAtAsc(Long conversationId, Pageable pageable);

    java.util.List<Message> findByChannelIdOrderByIdDesc(Long channelId, Pageable pageable);

    java.util.List<Message> findByChannelIdAndIdLessThanOrderByIdDesc(Long channelId, Long before, Pageable pageable);
}
