package com.denhub.repository;

import com.denhub.service.projection.UsernameProjection;
import org.jspecify.annotations.NonNull;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import jakarta.persistence.QueryHint;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.denhub.domain.User;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    String USERS_BY_EMAIL_CACHE = "usersByEmail";
    @Modifying
    @Query(value = "update User u set u.sessionId = :sessionId where u.id = :userId")
    int updateSessionIdById(@Param("userId") Long userId, @Param("sessionId") String sessionId);
    /*
     * Email must be lowed case before call method
     * */
    @EntityGraph(attributePaths = {"authorities"})
    @Cacheable(cacheNames = USERS_BY_EMAIL_CACHE)
    Optional<User> findOneWithAuthoritiesByEmail(String email);

    Optional<User> findOneByEmail(String email);

    // Scalar, non-cacheable read: authentication must see bulk session updates and revocations,
    // not a User retained in Spring's usersByEmail cache or Hibernate's entity caches.
    @Query("select u.sessionId from User u where u.email = :email and u.activated = true")
    @QueryHints(@QueryHint(name = "org.hibernate.cacheable", value = "false"))
    Optional<String> findCurrentSessionIdByEmail(@Param("email") String email);

    List<User> findAllByActivatedIsFalseAndActivationKeyNotNullAndCreatedDateBefore(Instant createdDateBefore);

    @Query(value = "delete from User u where u.id in :userIds")
    @Modifying
    void deleteByIdIn(@Param("userIds") List<Long>userIds);

    @Query(value = "update User u set u.refreshToken = :newRefreshToken where u.id = :userId")
    @Modifying
    int updatedRefreshTokenById(@Param("userId") Long userId, @Param("newRefreshToken") String newRefreshToken);

    Optional<User> findOneByActivationKey(String key);
    Optional<User> findOneByResetKey(String key);

    @Query(value = """
        select u.id as id, u.username as username, u.email as email, u.name as name, u.avatar as avatar
           from User u
               where u.username like concat('',:q,'%')
                 and u.id <> :currentUserId
                 and not exists (
                     select 1 from Friendship fs
                     where ((fs.userOneId = u.id and fs.userTwoId = :currentUserId)
                         or (fs.userOneId = :currentUserId and fs.userTwoId = u.id))
                       and fs.status = 'BLOCKED'
                       and fs.actionUserId = u.id
                 )
    """)
    List<UsernameProjection> searchByUsername(@Param("q") String q, @Param("currentUserId") Long currentUserId);


    List<User> findByIdIn(List<Long> ids);

    boolean existsById(@NonNull Long id);

    @Query(value = "select u.activated from User u where u.id = :userId")
    boolean getActivatedById(@Param("userId") Long userId);

    boolean existsByUsernameIgnoreCaseAndIdNot(String username, Long id);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    Optional<User> findOneByUsernameIgnoreCase(String username);

    @EntityGraph(attributePaths = {"authorities"})
    @Query(value = """
        SELECT DISTINCT u FROM User u
        WHERE (:pattern IS NULL OR LOWER(u.email) LIKE :pattern ESCAPE '!'
               OR LOWER(COALESCE(u.username, '')) LIKE :pattern ESCAPE '!'
               OR LOWER(COALESCE(u.name, '')) LIKE :pattern ESCAPE '!')
          AND (:activated IS NULL OR u.activated = :activated)
          AND (:role IS NULL OR EXISTS (SELECT 1 FROM u.authorities a WHERE a.name = :role))
        """, countQuery = """
        SELECT COUNT(DISTINCT u) FROM User u
        WHERE (:pattern IS NULL OR LOWER(u.email) LIKE :pattern ESCAPE '!'
               OR LOWER(COALESCE(u.username, '')) LIKE :pattern ESCAPE '!'
               OR LOWER(COALESCE(u.name, '')) LIKE :pattern ESCAPE '!')
          AND (:activated IS NULL OR u.activated = :activated)
          AND (:role IS NULL OR EXISTS (SELECT 1 FROM u.authorities a WHERE a.name = :role))
        """)
    org.springframework.data.domain.Page<User> searchAdminUsers(
            @Param("pattern") String pattern,
            @Param("role") String role,
            @Param("activated") Boolean activated,
            org.springframework.data.domain.Pageable pageable);

    @EntityGraph(attributePaths = {"authorities"})
    Optional<User> findOneWithAuthoritiesById(Long id);
}
