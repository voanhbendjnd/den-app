package com.denhub.repository;

import com.denhub.domain.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    @Query("""
        select c from Conversation c
        join ConversationMember cm1 on c.id = cm1.conversationId
        join ConversationMember cm2 on c.id = cm2.conversationId
        where c.type = :type
          and cm1.userId = :userId1
          and cm2.userId = :userId2
    """)
    Optional<Conversation> findDirectConversationBetweenUsers(
        @Param("type") String type,
        @Param("userId1") Long user1,
        @Param("userId2") Long user2
    );
}

