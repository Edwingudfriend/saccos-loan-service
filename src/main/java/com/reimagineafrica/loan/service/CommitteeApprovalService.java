package com.reimagineafrica.loan.service;

import com.reimagineafrica.loan.dto.request.CommitteeVoteRequest;
import com.reimagineafrica.loan.dto.request.ChairpersonDecisionRequest;
import com.reimagineafrica.loan.entity.CommitteeVoteRecord;
import com.reimagineafrica.loan.entity.LoanApplication;
import com.reimagineafrica.loan.enums.CommitteeVote;
import com.reimagineafrica.loan.enums.LoanApplicationStatus;
import com.reimagineafrica.loan.event.LoanEventPublisher;
import com.reimagineafrica.loan.exception.BusinessException;
import com.reimagineafrica.loan.repository.CommitteeVoteRepository;
import com.reimagineafrica.loan.repository.LoanApplicationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CommitteeApprovalService {

    private final LoanApplicationRepository loanApplicationRepository;
    private final CommitteeVoteRepository voteRepository;
    private final LoanAuditService auditService;
    private final LoanEventPublisher eventPublisher;

    @Value("${saccos.committee.quorum-size:3}")
    private int quorumSize;  // Votes needed to make a decision

    @Value("${saccos.committee.approval-threshold:0.5}")
    private double approvalThreshold; // >50% approve votes = approved

    // ── Loan Officer Review ──────────────────────────────────────────

    @Transactional
    public void officerApprove(UUID loanApplicationId, UUID officerId, String notes) {
        LoanApplication application = findInStatus(loanApplicationId, LoanApplicationStatus.LOAN_OFFICER_REVIEW);

        application.setAssignedLoanOfficerId(officerId);
        application.setLoanOfficerNotes(notes);
        application.setLoanOfficerReviewedAt(LocalDateTime.now());
        application.setStatus(LoanApplicationStatus.COMMITTEE_REVIEW);

        loanApplicationRepository.save(application);
        auditService.log(application, LoanApplicationStatus.LOAN_OFFICER_REVIEW,
                LoanApplicationStatus.COMMITTEE_REVIEW,
                "OFFICER_APPROVED", officerId.toString(), notes);

        eventPublisher.publishReadyForCommittee(application);
        log.info("Loan officer approved application {}", application.getReferenceNumber());
    }

    @Transactional
    public void officerReject(UUID loanApplicationId, UUID officerId, String reason) {
        LoanApplication application = findInStatus(loanApplicationId, LoanApplicationStatus.LOAN_OFFICER_REVIEW);

        application.setAssignedLoanOfficerId(officerId);
        application.setLoanOfficerNotes(reason);
        application.setLoanOfficerReviewedAt(LocalDateTime.now());
        application.setStatus(LoanApplicationStatus.LOAN_OFFICER_REJECTED);
        application.setRejectionReason(reason);

        loanApplicationRepository.save(application);
        auditService.log(application, LoanApplicationStatus.LOAN_OFFICER_REVIEW,
                LoanApplicationStatus.LOAN_OFFICER_REJECTED,
                "OFFICER_REJECTED", officerId.toString(), reason);

        eventPublisher.publishLoanRejected(application, "Loan Officer");
        log.info("Loan officer rejected application {}: {}", application.getReferenceNumber(), reason);
    }

    // ── Committee Voting ─────────────────────────────────────────────

    @Transactional
    public CommitteeVoteRecord castCommitteeVote(UUID loanApplicationId, CommitteeVoteRequest request) {
        LoanApplication application = findInStatus(loanApplicationId, LoanApplicationStatus.COMMITTEE_REVIEW);

        // One vote per committee member
        if (voteRepository.existsByLoanApplicationIdAndCommitteeMemberId(
                loanApplicationId, request.getCommitteeMemberId())) {
            throw new BusinessException("This committee member has already voted on this application.");
        }

        CommitteeVoteRecord vote = CommitteeVoteRecord.builder()
                .loanApplication(application)
                .committeeMemberId(request.getCommitteeMemberId())
                .committeeMemberName(request.getCommitteeMemberName())
                .vote(request.getVote())
                .comment(request.getComment())
                .build();

        voteRepository.save(vote);
        log.info("Committee member {} voted {} on application {}",
                request.getCommitteeMemberName(), request.getVote(), application.getReferenceNumber());

        // Check if quorum is reached
        evaluateCommitteeResult(application);

        return vote;
    }

    private void evaluateCommitteeResult(LoanApplication application) {
        List<CommitteeVoteRecord> votes = voteRepository.findByLoanApplicationId(application.getId());
        long totalVotes = votes.size();

        if (totalVotes < quorumSize) {
            log.info("Quorum not yet reached for {}. Votes so far: {}/{}",
                    application.getReferenceNumber(), totalVotes, quorumSize);
            return;
        }

        long approveVotes = votes.stream()
                .filter(v -> v.getVote() == CommitteeVote.APPROVE).count();

        double approvalRate = (double) approveVotes / totalVotes;

        application.setCommitteeReviewedAt(LocalDateTime.now());

        if (approvalRate > approvalThreshold) {
            application.setStatus(LoanApplicationStatus.CHAIRPERSON_APPROVAL);
            application.setCommitteeNotes(String.format(
                "Committee approved: %d/%d votes (%.0f%% approval)", approveVotes, totalVotes, approvalRate * 100));

            loanApplicationRepository.save(application);
            auditService.log(application, LoanApplicationStatus.COMMITTEE_REVIEW,
                    LoanApplicationStatus.CHAIRPERSON_APPROVAL,
                    "COMMITTEE_APPROVED", "System",
                    application.getCommitteeNotes());
            eventPublisher.publishReadyForChairperson(application);
            log.info("Committee APPROVED application {}", application.getReferenceNumber());
        } else {
            application.setStatus(LoanApplicationStatus.COMMITTEE_REJECTED);
            application.setRejectionReason(String.format(
                "Committee rejected: %d/%d votes approved (%.0f%% < required %.0f%%)",
                approveVotes, totalVotes, approvalRate * 100, approvalThreshold * 100));

            loanApplicationRepository.save(application);
            auditService.log(application, LoanApplicationStatus.COMMITTEE_REVIEW,
                    LoanApplicationStatus.COMMITTEE_REJECTED,
                    "COMMITTEE_REJECTED", "System", application.getRejectionReason());
            eventPublisher.publishLoanRejected(application, "Committee");
            log.info("Committee REJECTED application {}", application.getReferenceNumber());
        }
    }

    // ── Chairperson Decision ─────────────────────────────────────────

    @Transactional
    public void chairpersonDecide(UUID loanApplicationId, ChairpersonDecisionRequest request) {
        LoanApplication application = findInStatus(loanApplicationId, LoanApplicationStatus.CHAIRPERSON_APPROVAL);

        application.setChairpersonId(request.getChairpersonId());
        application.setChairpersonNotes(request.getNotes());
        application.setChairpersonApprovedAt(LocalDateTime.now());

        if (request.isApproved()) {
            application.setStatus(LoanApplicationStatus.APPROVED);
            loanApplicationRepository.save(application);
            auditService.log(application, LoanApplicationStatus.CHAIRPERSON_APPROVAL,
                    LoanApplicationStatus.APPROVED,
                    "CHAIRPERSON_APPROVED", request.getChairpersonId().toString(), request.getNotes());
            eventPublisher.publishFullyApproved(application);
            log.info("Chairperson APPROVED application {}", application.getReferenceNumber());
        } else {
            application.setStatus(LoanApplicationStatus.CHAIRPERSON_REJECTED);
            application.setRejectionReason(request.getNotes());
            loanApplicationRepository.save(application);
            auditService.log(application, LoanApplicationStatus.CHAIRPERSON_APPROVAL,
                    LoanApplicationStatus.CHAIRPERSON_REJECTED,
                    "CHAIRPERSON_REJECTED", request.getChairpersonId().toString(), request.getNotes());
            eventPublisher.publishLoanRejected(application, "Chairperson");
            log.info("Chairperson REJECTED application {}", application.getReferenceNumber());
        }
    }

    private LoanApplication findInStatus(UUID id, LoanApplicationStatus expectedStatus) {
        LoanApplication app = loanApplicationRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Loan application not found: " + id));
        if (app.getStatus() != expectedStatus) {
            throw new BusinessException(String.format(
                "Application is in '%s' status. Expected '%s'.", app.getStatus(), expectedStatus));
        }
        return app;
    }
}
