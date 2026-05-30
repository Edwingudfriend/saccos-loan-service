package com.reimagineafrica.loan.repository;

import com.reimagineafrica.loan.entity.LoanAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LoanAuditLogRepository extends JpaRepository<LoanAuditLog, UUID> {
    List<LoanAuditLog> findByLoanApplicationIdOrderByCreatedAtAsc(UUID loanApplicationId);
}
