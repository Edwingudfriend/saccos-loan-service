package com.reimagineafrica.loan.repository;

import com.reimagineafrica.loan.entity.LoanGuarantor;
import com.reimagineafrica.loan.enums.GuarantorStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LoanGuarantorRepository extends JpaRepository<LoanGuarantor, UUID> {

    List<LoanGuarantor> findByLoanApplicationId(UUID loanApplicationId);

    List<LoanGuarantor> findByGuarantorMemberIdAndStatus(UUID guarantorMemberId, GuarantorStatus status);

    // Count how many loans this member is currently guaranteeing
    long countByGuarantorMemberIdAndStatus(UUID guarantorMemberId, GuarantorStatus status);
}
