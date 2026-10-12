package com.denhub.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.denhub.domain.Authority;
@Repository
public interface AuthorityRepository extends JpaRepository<Authority, String> {
}
