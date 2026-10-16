package com.denhub.repository;

import com.denhub.domain.Friendship;
import com.denhub.service.projection.FriendRequestProjection;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface FriendshipRepository extends JpaRepository<Friendship, Long> {
    @Query(value = "select fs from Friendship fs where fs.userOneId = :userOneId and fs.userTwoId = :userTwoId")
    Optional<Friendship> findByUserOneIdAndUserTwoId(@Param("userOneId") Long userOneId,@Param("userTwoId") Long userTwoId);

    @Query(value = """
        select u.id as senderId, u.name as name, u.avatar as avatar, u.username as username, fs.lastModifiedDate as lastModifiedDate
        from Friendship fs
        join User u
        on u.id = case
            when fs.userOneId = :userId then fs.userTwoId
                else fs.userOneId
                    end
        where (fs.userOneId = :userId or fs.userTwoId = :userId)
        and fs.status = :status
        and (:status <> 'PENDING' or fs.actionUserId <> :userId)
        and (:status <> 'BLOCKED' or fs.actionUserId = :userId)
        and (:cursor is null or fs.lastModifiedDate < :cursor)
        order by fs.lastModifiedDate desc, fs.id desc
    """)
    Slice<FriendRequestProjection> getAllFriendWithStatus(@Param("userId") Long currentUserId,@Param("status") String status, @Param("cursor")Instant cursor, Pageable pageable);
}
