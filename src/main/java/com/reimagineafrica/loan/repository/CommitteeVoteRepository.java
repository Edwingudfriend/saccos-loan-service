package com.reimagineafrica.loan.repository;

import com.reimagineafrica.loan.entity.CommitteeVoteRecord;
import com.reimagineafrica.loan.enums.CommitteeVote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CommitteeVoteRepository extends JpaRepository<CommitteeVoteRecord, UUID> {

    List<CommitteeVoteRecord> findByLoanApplicationId(UUID loanApplicationId);

    boolean existsByLoanApplicationIdAndCommitteeMemberId(UUID loanApplicationId, UUID committeeMemberId);

    @Query("""
        SELECT COUNT(v) FROM CommitteeVoteRecord v
        WHERE v.loanApplication.id = :loanApplicationId
        AND v.vote = :vote
    """)
    long countByLoanApplicationIdAndVote(UUID loanApplicationId, CommitteeVote vote);
}
