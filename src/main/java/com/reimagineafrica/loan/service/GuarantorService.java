package com.reimagineafrica.loan.service;

import com.reimagineafrica.loan.client.MemberServiceClient;
import com.reimagineafrica.loan.dto.request.AddGuarantorRequest;
import com.reimagineafrica.loan.dto.request.GuarantorConsentRequest;
import com.reimagineafrica.loan.entity.LoanApplication;
import com.reimagineafrica.loan.entity.LoanGuarantor;
import com.reimagineafrica.loan.entity.SaccoConfig;
import com.reimagineafrica.loan.enums.GuarantorStatus;
import com.reimagineafrica.loan.enums.LoanApplicationStatus;
import com.reimagineafrica.loan.enums.LoanType;
import com.reimagineafrica.loan.event.LoanEventPublisher;
import com.reimagineafrica.loan.exception.BusinessException;
import com.reimagineafrica.loan.repository.LoanApplicationRepository;
import com.reimagineafrica.loan.repository.LoanGuarantorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class GuarantorService {

    private final LoanApplicationRepository loanApplicationRepository;
    private final LoanGuarantorRepository loanGuarantorRepository;
    private final MemberServiceClient memberServiceClient;
    private final LoanEventPublisher eventPublisher;
    private final LoanAuditService auditService;
    private final SaccoConfigService saccoConfigService;

    @Transactional
    public LoanGuarantor addGuarantor(UUID loanApplicationId, AddGuarantorRequest request) {
        LoanApplication application = findApplication(loanApplicationId);

        if (application.getStatus() != LoanApplicationStatus.GUARANTOR_SELECTION) {
            throw new BusinessException("Loan is not in GUARANTOR_SELECTION stage.");
        }

        // Resolve per-SACCO config
        SaccoConfig config = saccoConfigService.resolveConfig(application.getSaccoCode());

        // Cannot guarantee own loan
        if (request.getGuarantorMemberId().equals(application.getMemberId())) {
            throw new BusinessException("A member cannot guarantee their own loan.");
        }

        // Check guarantor not already added
        boolean alreadyAdded = application.getGuarantors().stream()
                .anyMatch(g -> g.getGuarantorMemberId().equals(request.getGuarantorMemberId()));
        if (alreadyAdded) {
            throw new BusinessException("This member is already added as a guarantor.");
        }

        // Fetch guarantor details from member-service
        var guarantorData = memberServiceClient.getMemberFinancialSummary(request.getGuarantorMemberId());

        if (guarantorData.isHasDefaultedLoan()) {
            throw new BusinessException("Proposed guarantor has an active defaulted loan and cannot guarantee.");
        }

        // Check max active guarantees — uses per-SACCO config
        long activeGuarantees = loanGuarantorRepository.countByGuarantorMemberIdAndStatus(
                request.getGuarantorMemberId(), GuarantorStatus.CONSENTED);
        if (activeGuarantees >= config.getMaxActiveGuaranteesPerMember()) {
            throw new BusinessException(String.format(
                "Proposed guarantor is already guaranteeing %d loan(s). " +
                "Maximum allowed by %s: %d.",
                activeGuarantees, application.getSaccoCode(),
                config.getMaxActiveGuaranteesPerMember()));
        }

        LoanGuarantor guarantor = LoanGuarantor.builder()
                .loanApplication(application)
                .guarantorMemberId(request.getGuarantorMemberId())
                .guarantorMemberNumber(guarantorData.getMemberNumber())
                .guarantorName(guarantorData.getFullName())
                .guarantorPhone(guarantorData.getPhone())
                .guaranteeAmount(request.getGuaranteeAmount())
                .guarantorShareBalance(guarantorData.getShareBalance())
                .status(GuarantorStatus.PENDING)
                .consentRequestedAt(LocalDateTime.now())
                .build();

        loanGuarantorRepository.save(guarantor);
        eventPublisher.publishGuarantorConsentRequested(application, guarantor);

        log.info("Guarantor {} added to loan {}", guarantorData.getFullName(), application.getReferenceNumber());
        return guarantor;
    }

    @Transactional
    public void recordConsent(UUID loanApplicationId, UUID guarantorId, GuarantorConsentRequest request) {
        LoanApplication application = findApplication(loanApplicationId);
        LoanGuarantor guarantor = loanGuarantorRepository.findById(guarantorId)
                .orElseThrow(() -> new BusinessException("Guarantor not found."));

        if (guarantor.getStatus() != GuarantorStatus.PENDING) {
            throw new BusinessException("Guarantor has already responded.");
        }

        guarantor.setConsentRespondedAt(LocalDateTime.now());

        if (request.isConsenting()) {
            guarantor.setStatus(GuarantorStatus.CONSENTED);
            log.info("Guarantor {} CONSENTED for loan {}", guarantor.getGuarantorName(), application.getReferenceNumber());
        } else {
            guarantor.setStatus(GuarantorStatus.DECLINED);
            guarantor.setDeclineReason(request.getDeclineReason());
            log.info("Guarantor {} DECLINED for loan {}", guarantor.getGuarantorName(), application.getReferenceNumber());
        }

        loanGuarantorRepository.save(guarantor);
        evaluateGuarantorStatus(application);
    }

    @Transactional
    public void submitForGuarantorConsent(UUID loanApplicationId) {
        LoanApplication application = findApplication(loanApplicationId);

        // Use per-SACCO minimum guarantors (emergency loans may need fewer)
        SaccoConfig config = saccoConfigService.resolveConfig(application.getSaccoCode());
        int requiredGuarantors = application.getLoanType() == LoanType.EMERGENCY
                ? config.getEmergencyLoanMinGuarantors()
                : config.getMinimumGuarantorsRequired();

        if (application.getGuarantors().size() < requiredGuarantors) {
            throw new BusinessException(String.format(
                "Minimum %d guarantors required for %s loans in %s. Currently have %d.",
                requiredGuarantors, application.getLoanType(),
                application.getSaccoCode(), application.getGuarantors().size()));
        }

        application.setStatus(LoanApplicationStatus.AWAITING_GUARANTOR_CONSENT);
        loanApplicationRepository.save(application);

        auditService.log(application, LoanApplicationStatus.GUARANTOR_SELECTION,
                LoanApplicationStatus.AWAITING_GUARANTOR_CONSENT,
                "SUBMITTED_FOR_GUARANTOR_CONSENT", application.getCreatedBy(),
                "Sent consent requests to " + application.getGuarantors().size() + " guarantors.");

        application.getGuarantors().forEach(g ->
                eventPublisher.publishGuarantorConsentRequested(application, g));
    }

    private void evaluateGuarantorStatus(LoanApplication application) {
        SaccoConfig config = saccoConfigService.resolveConfig(application.getSaccoCode());
        int requiredGuarantors = application.getLoanType() == LoanType.EMERGENCY
                ? config.getEmergencyLoanMinGuarantors()
                : config.getMinimumGuarantorsRequired();

        if (application.getGuarantors().size() < requiredGuarantors) {
            return;
        }

        if (application.hasAnyGuarantorDeclined()) {
            application.setStatus(LoanApplicationStatus.GUARANTOR_DECLINED);
            auditService.log(application, LoanApplicationStatus.AWAITING_GUARANTOR_CONSENT,
                    LoanApplicationStatus.GUARANTOR_DECLINED,
                    "GUARANTOR_DECLINED", "System", "One or more guarantors declined.");
            eventPublisher.publishGuarantorDeclined(application);
        } else if (application.allGuarantorsConsented()) {
            application.setStatus(LoanApplicationStatus.GUARANTORS_CONFIRMED);
            auditService.log(application, LoanApplicationStatus.AWAITING_GUARANTOR_CONSENT,
                    LoanApplicationStatus.GUARANTORS_CONFIRMED,
                    "ALL_GUARANTORS_CONSENTED", "System", "All guarantors consented.");

            // Check if loan officer review is required for this SACCO
            if (config.getLoanOfficerReviewRequired()) {
                application.setStatus(LoanApplicationStatus.LOAN_OFFICER_REVIEW);
            } else {
                // Skip to committee
                application.setStatus(LoanApplicationStatus.COMMITTEE_REVIEW);
            }
            eventPublisher.publishReadyForOfficerReview(application);
        }

        loanApplicationRepository.save(application);
    }

    private LoanApplication findApplication(UUID id) {
        return loanApplicationRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Loan application not found: " + id));
    }
}
