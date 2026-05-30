package com.reimagineafrica.loan.service;

import com.reimagineafrica.loan.dto.request.LoanApplicationRequest;
import com.reimagineafrica.loan.dto.response.LoanApplicationResponse;
import com.reimagineafrica.loan.dto.response.MemberEligibilityResult;
import com.reimagineafrica.loan.entity.LoanApplication;
import com.reimagineafrica.loan.enums.LoanApplicationStatus;
import com.reimagineafrica.loan.event.LoanEventPublisher;
import com.reimagineafrica.loan.exception.BusinessException;
import com.reimagineafrica.loan.repository.LoanApplicationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoanApplicationService {

    private final LoanApplicationRepository loanApplicationRepository;
    private final EligibilityService eligibilityService;
    private final LoanAuditService auditService;
    private final LoanEventPublisher eventPublisher;

    // Simple sequence counter (use DB sequence in production)
    private final AtomicInteger sequence = new AtomicInteger(1);

    @Transactional
    public LoanApplicationResponse apply(LoanApplicationRequest request, String createdBy) {
        // Build application in DRAFT state
        LoanApplication application = LoanApplication.builder()
                .referenceNumber(generateReferenceNumber())
                .memberId(request.getMemberId())
                .memberNumber(request.getMemberNumber())
                .memberName(request.getMemberName())
                .memberPhone(request.getMemberPhone())
                .loanType(request.getLoanType())
                .appliedAmount(request.getAppliedAmount())
                .repaymentPeriodMonths(request.getRepaymentPeriodMonths())
                .interestRatePerMonth(request.getInterestRatePerMonth())
                .purpose(request.getPurpose())
                .status(LoanApplicationStatus.SUBMITTED)
                .createdBy(createdBy)
                .build();

        loanApplicationRepository.save(application);
        auditService.log(application, null, LoanApplicationStatus.SUBMITTED,
                "APPLICATION_SUBMITTED", createdBy, "Loan application submitted by member.");

        log.info("Loan application {} submitted by member {}", application.getReferenceNumber(), request.getMemberNumber());

        // Run eligibility checks immediately
        application.setStatus(LoanApplicationStatus.ELIGIBILITY_CHECK);
        MemberEligibilityResult eligibility = eligibilityService.checkEligibility(application);
        loanApplicationRepository.save(application);

        if (!eligibility.isPassed()) {
            eventPublisher.publishEligibilityFailed(application, eligibility.getFailureReason());
        }

        return toResponse(application, eligibility);
    }

    @Transactional
    public void withdraw(UUID loanApplicationId, UUID memberId) {
        LoanApplication application = loanApplicationRepository.findById(loanApplicationId)
                .orElseThrow(() -> new BusinessException("Application not found."));

        if (!application.getMemberId().equals(memberId)) {
            throw new BusinessException("Only the applicant can withdraw this application.");
        }

        if (application.getStatus() == LoanApplicationStatus.ACTIVE
                || application.getStatus() == LoanApplicationStatus.DISBURSING_TO_FINERACT) {
            throw new BusinessException("Cannot withdraw an already disbursed loan.");
        }

        application.setStatus(LoanApplicationStatus.WITHDRAWN);
        loanApplicationRepository.save(application);
        auditService.log(application, application.getStatus(), LoanApplicationStatus.WITHDRAWN,
                "WITHDRAWN", memberId.toString(), "Withdrawn by member.");
    }

    public LoanApplicationResponse getByReference(String referenceNumber) {
        LoanApplication application = loanApplicationRepository.findByReferenceNumber(referenceNumber)
                .orElseThrow(() -> new BusinessException("Loan application not found: " + referenceNumber));
        return toResponse(application, null);
    }

    public Page<LoanApplicationResponse> getByMember(UUID memberId, Pageable pageable) {
        return loanApplicationRepository.findByMemberId(memberId, pageable)
                .map(a -> toResponse(a, null));
    }

    public Page<LoanApplicationResponse> getByStatus(LoanApplicationStatus status, Pageable pageable) {
        return loanApplicationRepository.findByStatus(status, pageable)
                .map(a -> toResponse(a, null));
    }

    private String generateReferenceNumber() {
        return String.format("LOAN-%d-%05d", Year.now().getValue(), sequence.getAndIncrement());
    }

    private LoanApplicationResponse toResponse(LoanApplication app, MemberEligibilityResult eligibility) {
        return LoanApplicationResponse.builder()
                .id(app.getId())
                .referenceNumber(app.getReferenceNumber())
                .memberId(app.getMemberId())
                .memberName(app.getMemberName())
                .loanType(app.getLoanType())
                .appliedAmount(app.getAppliedAmount())
                .maxEligibleAmount(app.getMaxEligibleAmount())
                .repaymentPeriodMonths(app.getRepaymentPeriodMonths())
                .interestRatePerMonth(app.getInterestRatePerMonth())
                .purpose(app.getPurpose())
                .status(app.getStatus())
                .eligibilityFailureReason(app.getEligibilityFailureReason())
                .rejectionReason(app.getRejectionReason())
                .guarantorCount(app.getGuarantors().size())
                .fineractLoanId(app.getFineractLoanId())
                .fineractDisbursementDate(app.getFineractDisbursementDate())
                .createdAt(app.getCreatedAt())
                .updatedAt(app.getUpdatedAt())
                .build();
    }
}
