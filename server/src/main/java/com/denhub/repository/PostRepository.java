package com.denhub.repository;

import com.denhub.domain.Post;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface PostRepository extends JpaRepository<Post, Long> {

    @Query(value = """
        select p from Post p
            where (
                (p.userId in :friendIds and p.privacy in ('PUBLIC', 'FRIENDS_ONLY'))
                or (p.userId = :currentUserId)
            )
            and (:cursorDate is null or p.createdDate < :cursorDate or (p.createdDate = :cursorDate and p.id < :cursorId))
            order by p.createdDate desc, p.id desc
    """)
    Slice<Post> getAllPostForFeed(
            @Param("friendIds") List<Long> friendIds,
            @Param("currentUserId") Long currentUserId,
            @Param("cursorDate") Instant cursorDate,
            @Param("cursorId") Long cursorId,
            Pageable pageable);

    @Query(value = """
        select p from Post p
            where p.userId in :friendIds
                and p.privacy in ('PUBLIC', 'FRIENDS_ONLY')
                and (:cursorDate is null or p.createdDate < :cursorDate or (p.createdDate = :cursorDate and p.id < :cursorId))
            order by p.createdDate desc, p.id desc
    """)
    Slice<Post> getAllPublicByFriendIds(
            @Param("friendIds") List<Long> friendIds,
            @Param("cursorDate") Instant cursorDate,
            @Param("cursorId") Long cursorId,
            Pageable pageable);
}
