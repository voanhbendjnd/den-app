package com.denhub.repository;

import com.denhub.domain.Friendship;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FriendshipRepository extends JpaRepository<Friendship, Long> {
    @Query(value = "select fs from Friendship fs where fs.userOneId = :userOneId and fs.userTwoId = :userTwoId")
    Optional<Friendship> findByUserOneIdAndUserTwoId(@Param("userOneId") Long userOneId,@Param("userTwoId") Long userTwoId);

}
