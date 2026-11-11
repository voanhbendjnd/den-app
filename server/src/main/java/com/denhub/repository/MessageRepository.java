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

    @Query("""
        SELECT COUNT(m) > 0 FROM Message m
        WHERE m.channelId = :channelId
          AND (m.conversationId IS NOT NULL
               OR EXISTS (SELECT d.id FROM Conversation d WHERE d.lastMessageId = m.id))
        """)
    boolean hasConversationReferencesInChannel(@Param("channelId") Long channelId);

    @Modifying(flushAutomatically = true)
    @Query("""
        DELETE FROM Message m WHERE m.conversationId IS NULL
          AND m.channelId = :channelId
        """)
    int deleteByChannelId(@Param("channelId") Long channelId);

    Page<Message> findByConversationIdOrderByCreatedAtAsc(Long conversationId, Pageable pageable);

    java.util.List<Message> findByChannelIdOrderByIdDesc(Long channelId, Pageable pageable);

    java.util.List<Message> findByChannelIdAndIdLessThanOrderByIdDesc(Long channelId, Long before, Pageable pageable);

    @Query("SELECT m FROM Message m WHERE m.channelId=:channel AND m.conversationId IS NULL AND (:before IS NULL OR m.id<:before) AND LOWER(m.content) LIKE :pattern ESCAPE '!' ORDER BY m.id DESC")
    java.util.List<Message> searchChannel(@Param("channel") Long channel, @Param("pattern") String pattern, @Param("before") Long before, Pageable page);

    @Query("SELECT m FROM Message m WHERE m.channelId=:channel AND m.conversationId IS NULL AND m.id IN (SELECT p.messageId FROM MessagePin p) AND (:before IS NULL OR m.id<:before) ORDER BY m.id DESC")
    java.util.List<Message> pinned(@Param("channel") Long channel,@Param("before") Long before, Pageable page);

    @Query("SELECT m FROM Message m WHERE m.conversationId IS NULL AND m.id IN (SELECT s.messageId FROM SavedMessage s WHERE s.userId=:user) AND EXISTS (SELECT c.id FROM Channel c WHERE c.id=m.channelId AND EXISTS (SELECT sm.id FROM ServerMember sm WHERE sm.serverId=c.serverId AND sm.userId=:user)) AND (:before IS NULL OR m.id<:before) ORDER BY m.id DESC")
    java.util.List<Message> saved(@Param("user") Long user,@Param("before") Long before, Pageable page);

    @Query("SELECT u FROM User u WHERE u.id IN (SELECT s.userId FROM SavedMessage s WHERE s.messageId IN :ids)")
    java.util.List<com.denhub.domain.User> savedUsers(@Param("ids") java.util.List<Long> ids);

    @Modifying
    @Query("DELETE FROM MessagePin p WHERE p.messageId IN :ids")
    int removePins(@Param("ids") java.util.List<Long> ids);
    @Modifying
    @Query("DELETE FROM SavedMessage s WHERE s.messageId IN :ids")
    int removeSaved(@Param("ids") java.util.List<Long> ids);
    @Modifying
    @Query("UPDATE Message m SET m.replyToMessageId=NULL WHERE m.replyToMessageId IN :ids")
    int detachReplies(@Param("ids") java.util.List<Long> ids);
    @Modifying
    @Query("DELETE FROM ChannelNotificationPreference p WHERE p.channelId IN :ids")
    int removePreferences(@Param("ids") java.util.List<Long> ids);

    @Query("SELECT m.id FROM Message m WHERE m.channelId IN :channels AND m.conversationId IS NULL")
    java.util.List<Long> messageIds(@Param("channels") java.util.List<Long> channels);
    @Query("SELECT c.id FROM Channel c WHERE c.serverId=:server")
    java.util.List<Long> channelIds(@Param("server") Long server);

    default void cleanupMessages(java.util.List<Long> ids) {
        if (ids.isEmpty()) return;
        removePins(ids); removeSaved(ids); detachReplies(ids);
    }
    @Query("SELECT u FROM User u WHERE u.id IN (SELECT s.userId FROM SavedMessage s WHERE s.messageId IN (SELECT m.id FROM Message m WHERE m.channelId IN :channels))")
    java.util.List<com.denhub.domain.User> savedUsersInChannels(@Param("channels") java.util.List<Long> channels);
    @Modifying
    @Query("DELETE FROM MessagePin p WHERE p.messageId IN (SELECT m.id FROM Message m WHERE m.channelId IN :channels)")
    int removeChannelPins(@Param("channels") java.util.List<Long> channels);
    @Modifying
    @Query("DELETE FROM SavedMessage s WHERE s.messageId IN (SELECT m.id FROM Message m WHERE m.channelId IN :channels)")
    int removeChannelSaved(@Param("channels") java.util.List<Long> channels);
    @Modifying
    @Query("UPDATE Message m SET m.replyToMessageId=NULL WHERE m.replyToMessageId IN (SELECT target.id FROM Message target WHERE target.channelId IN :channels)")
    int detachChannelReplies(@Param("channels") java.util.List<Long> channels);
    default void cleanupChannels(java.util.List<Long> ids) {
        if (ids.isEmpty()) return;
        removeChannelPins(ids); removeChannelSaved(ids); detachChannelReplies(ids); removePreferences(ids);
    }
}
