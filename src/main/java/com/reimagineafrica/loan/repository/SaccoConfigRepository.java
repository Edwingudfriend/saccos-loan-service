package com.reimagineafrica.loan.repository;

import com.reimagineafrica.loan.entity.SaccoConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SaccoConfigRepository extends JpaRepository<SaccoConfig, Long> {

    Optional<SaccoConfig> findBySaccoCode(String saccoCode);

    Optional<SaccoConfig> findBySaccoCodeAndActiveTrue(String saccoCode);

    boolean existsBySaccoCode(String saccoCode);
}
