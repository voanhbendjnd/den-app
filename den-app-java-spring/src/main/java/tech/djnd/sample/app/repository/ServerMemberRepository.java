package tech.djnd.sample.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tech.djnd.sample.app.domain.ServerMember;

@Repository
public interface ServerMemberRepository extends JpaRepository<ServerMember,Long> {
}
