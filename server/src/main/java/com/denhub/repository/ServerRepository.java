package com.denhub.repository;

import org.apache.poi.ss.formula.functions.T;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.denhub.domain.Server;

@Repository
public interface ServerRepository extends JpaRepository<Server,Long> {
}
