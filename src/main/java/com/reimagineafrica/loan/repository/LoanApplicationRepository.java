package com.reimagineafrica.loan.repository;

import com.reimagineafrica.loan.entity.LoanApplication;
import com.reimagineafrica.loan.enums.LoanApplicationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LoanApplicationRepository extends JpaRepository<LoanApplication, UUID> {

    Optional<LoanApplication> findByReferenceNumber(String referenceNumber);

    Page<LoanApplication> findByMemberId(UUID memberId, Pageable pageable);

    Page<LoanApplication> findByStatus(LoanApplicationStatus status, Pageable pageable);

    Page<LoanApplication> findByAssignedLoanOfficerId(UUID officerId, Pageable pageable);

    // Member's active loans (not withdrawn / rejected)
    @Query("""
        SELECT l FROM LoanApplication l
        WHERE l.memberId = :memberId
        AND l.status NOT IN (
            'WITHDRAWN', 'ELIGIBILITY_FAILED', 'LOAN_OFFICER_REJECTED',
            'COMMITTEE_REJECTED', 'CHAIRPERSON_REJECTED', 'GUARANTOR_DECLINED'
        )
    """)
    List<LoanApplication> findActiveLoansByMember(UUID memberId);

    // Check if member has a defaulted active loan in Fineract
    @Query("""
        SELECT COUNT(l) > 0 FROM LoanApplication l
        WHERE l.memberId = :memberId
        AND l.status = 'ACTIVE'
        AND l.hasActiveDefaultedLoan = true
    """)
    boolean memberHasDefaultedLoan(UUID memberId);

    // Loans pending committee review
    List<LoanApplication> findByStatusOrderByCreatedAtAsc(LoanApplicationStatus status);

    // Loans awaiting guarantor consent — for reminder scheduler
    @Query("""
        SELECT l FROM LoanApplication l
        WHERE l.status = 'AWAITING_GUARANTOR_CONSENT'
    """)
    List<LoanApplication> findAwaitingGuarantorConsent();
}
