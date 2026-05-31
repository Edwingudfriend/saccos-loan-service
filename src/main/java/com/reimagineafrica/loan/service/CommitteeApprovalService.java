package com.reimagineafrica.loan.service;

import com.reimagineafrica.loan.dto.request.CommitteeVoteRequest;
import com.reimagineafrica.loan.dto.request.ChairpersonDecisionRequest;
import com.reimagineafrica.loan.entity.CommitteeVoteRecord;
import com.reimagineafrica.loan.entity.LoanApplication;
import com.reimagineafrica.loan.entity.SaccoConfig;
import com.reimagineafrica.loan.enums.CommitteeVote;
import com.reimagineafrica.loan.enums.LoanApplicationStatus;
import com.reimagineafrica.loan.enums.LoanType;
import com.reimagineafrica.loan.event.LoanEventPublisher;
import com.reimagineafrica.loan.exception.BusinessException;
import com.reimagineafrica.loan.repository.CommitteeVoteRepository;
import com.reimagineafrica.loan.repository.LoanApplicationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final SaccoConfigService saccoConfigService;

    // ── Loan Officer Review ──────────────────────────────────────────

    @Transactional
    public void officerApprove(UUID loanApplicationId, UUID officerId, String notes) {
        LoanApplication application = findInStatus(loanApplicationId, LoanApplicationStatus.LOAN_OFFICER_REVIEW);
        SaccoConfig config = saccoConfigService.resolveConfig(application.getSaccoCode());

        application.setAssignedLoanOfficerId(officerId);
        application.setLoanOfficerNotes(notes);
        application.setLoanOfficerReviewedAt(LocalDateTime.now());

        // Check if committee is required for this SACCO or this loan type
        boolean skipCommittee = !config.getCommitteeReviewRequired()
                || (application.getLoanType() == LoanType.EMERGENCY
                    && config.getEmergencyLoanSkipsCommittee());

        if (skipCommittee) {
            // Go straight to chairperson if configured
            if (config.getChairpersonApprovalRequired()) {
                application.setStatus(LoanApplicationStatus.CHAIRPERSON_APPROVAL);
                auditService.log(application, LoanApplicationStatus.LOAN_OFFICER_REVIEW,
                        LoanApplicationStatus.CHAIRPERSON_APPROVAL,
                        "OFFICER_APPROVED_SKIP_COMMITTEE", officerId.toString(),
                        "Committee skipped per SACCO config. " + notes);
                eventPublisher.publishReadyForChairperson(application);
            } else {
                // Skip both committee and chairperson — fully approved
                application.setStatus(LoanApplicationStatus.APPROVED);
                auditService.log(application, LoanApplicationStatus.LOAN_OFFICER_REVIEW,
                        LoanApplicationStatus.APPROVED,
                        "OFFICER_APPROVED_AUTO_APPROVED", officerId.toString(),
                        "Both committee and chairperson skipped per SACCO config.");
                eventPublisher.publishFullyApproved(application);
            }
        } else {
            application.setStatus(LoanApplicationStatus.COMMITTEE_REVIEW);
            auditService.log(application, LoanApplicationStatus.LOAN_OFFICER_REVIEW,
                    LoanApplicationStatus.COMMITTEE_REVIEW,
                    "OFFICER_APPROVED", officerId.toString(), notes);
            eventPublisher.publishReadyForCommittee(application);
        }

        loanApplicationRepository.save(application);
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
    }

    // ── Committee Voting ─────────────────────────────────────────────

    @Transactional
    public CommitteeVoteRecord castCommitteeVote(UUID loanApplicationId, CommitteeVoteRequest request) {
        LoanApplication application = findInStatus(loanApplicationId, LoanApplicationStatus.COMMITTEE_REVIEW);

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

        evaluateCommitteeResult(application);
        return vote;
    }

    private void evaluateCommitteeResult(LoanApplication application) {
        // Resolve per-SACCO quorum and threshold
        SaccoConfig config = saccoConfigService.resolveConfig(application.getSaccoCode());

        List<CommitteeVoteRecord> votes = voteRepository.findByLoanApplicationId(application.getId());
        long totalVotes = votes.size();

        if (totalVotes < config.getCommitteeQuorumSize()) {
            log.info("Quorum not yet reached for {}. Votes so far: {}/{}",
                    application.getReferenceNumber(), totalVotes, config.getCommitteeQuorumSize());
            return;
        }

        long approveVotes = votes.stream()
                .filter(v -> v.getVote() == CommitteeVote.APPROVE).count();
        double approvalRate = (double) approveVotes / totalVotes;

        application.setCommitteeReviewedAt(LocalDateTime.now());

        if (approvalRate >= config.getCommitteeApprovalThreshold().doubleValue()) {
            application.setCommitteeNotes(String.format(
                "Committee approved: %d/%d votes (%.0f%% approval, threshold: %.0f%%)",
                approveVotes, totalVotes, approvalRate * 100,
                config.getCommitteeApprovalThreshold().doubleValue() * 100));

            if (config.getChairpersonApprovalRequired()) {
                application.setStatus(LoanApplicationStatus.CHAIRPERSON_APPROVAL);
                loanApplicationRepository.save(application);
                auditService.log(application, LoanApplicationStatus.COMMITTEE_REVIEW,
                        LoanApplicationStatus.CHAIRPERSON_APPROVAL,
                        "COMMITTEE_APPROVED", "System", application.getCommitteeNotes());
                eventPublisher.publishReadyForChairperson(application);
            } else {
                application.setStatus(LoanApplicationStatus.APPROVED);
                loanApplicationRepository.save(application);
                auditService.log(application, LoanApplicationStatus.COMMITTEE_REVIEW,
                        LoanApplicationStatus.APPROVED,
                        "COMMITTEE_APPROVED_AUTO_APPROVED", "System",
                        "Chairperson step skipped per SACCO config.");
                eventPublisher.publishFullyApproved(application);
            }
            log.info("Committee APPROVED application {}", application.getReferenceNumber());
        } else {
            application.setStatus(LoanApplicationStatus.COMMITTEE_REJECTED);
            application.setRejectionReason(String.format(
                "Committee rejected: %d/%d votes approved (%.0f%% < required %.0f%%)",
                approveVotes, totalVotes, approvalRate * 100,
                config.getCommitteeApprovalThreshold().doubleValue() * 100));

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
