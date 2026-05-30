package com.reimagineafrica.loan.service;

import com.reimagineafrica.loan.client.FineractClient;
import com.reimagineafrica.loan.entity.LoanApplication;
import com.reimagineafrica.loan.enums.LoanApplicationStatus;
import com.reimagineafrica.loan.event.LoanEventPublisher;
import com.reimagineafrica.loan.exception.BusinessException;
import com.reimagineafrica.loan.repository.LoanApplicationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Handles disbursement to Fineract AFTER full SACCOS approval.
 *
 * INVARIANT: fineractLoanId is NULL until this service runs.
 * This ensures Fineract is never touched before all SACCOS governance is complete.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FineractDisbursementService {

    private final FineractClient fineractClient;
    private final LoanApplicationRepository loanApplicationRepository;
    private final LoanAuditService auditService;
    private final LoanEventPublisher eventPublisher;

    @Transactional
    public void disburseToFineract(UUID loanApplicationId) {
        LoanApplication application = loanApplicationRepository.findById(loanApplicationId)
                .orElseThrow(() -> new BusinessException("Application not found: " + loanApplicationId));

        if (application.getStatus() != LoanApplicationStatus.APPROVED) {
            throw new BusinessException("Loan must be fully APPROVED before disbursement. Current status: " + application.getStatus());
        }

        if (application.getFineractLoanId() != null) {
            throw new BusinessException("Loan has already been disbursed to Fineract. Fineract loan ID: " + application.getFineractLoanId());
        }

        application.setStatus(LoanApplicationStatus.DISBURSING_TO_FINERACT);
        loanApplicationRepository.save(application);

        try {
            log.info("Disbursing loan {} to Fineract...", application.getReferenceNumber());

            // Step 1: Ensure client exists in Fineract
            Long fineractClientId = fineractClient.getOrCreateClient(
                    application.getMemberId(),
                    application.getMemberName(),
                    application.getMemberPhone()
            );

            // Step 2: Create loan in Fineract
            Long fineractLoanId = fineractClient.createLoan(
                    fineractClientId,
                    application.getAppliedAmount(),
                    application.getRepaymentPeriodMonths(),
                    application.getInterestRatePerMonth(),
                    application.getLoanType(),
                    application.getReferenceNumber() // External reference
            );

            // Step 3: Approve loan in Fineract
            fineractClient.approveLoan(fineractLoanId, application.getReferenceNumber());

            // Step 4: Disburse
            LocalDate disbursementDate = LocalDate.now();
            fineractClient.disburseLoan(fineractLoanId, disbursementDate);

            // Step 5: Save Fineract IDs
            application.setFineractClientId(fineractClientId);
            application.setFineractLoanId(fineractLoanId);
            application.setFineractDisbursementDate(disbursementDate);
            application.setStatus(LoanApplicationStatus.ACTIVE);

            loanApplicationRepository.save(application);

            auditService.log(application, LoanApplicationStatus.DISBURSING_TO_FINERACT,
                    LoanApplicationStatus.ACTIVE,
                    "DISBURSED_TO_FINERACT", "System",
                    String.format("Fineract clientId=%d, loanId=%d, disbursed on %s",
                            fineractClientId, fineractLoanId, disbursementDate));

            eventPublisher.publishDisbursed(application);
            log.info("Loan {} successfully disbursed. Fineract Loan ID: {}",
                    application.getReferenceNumber(), fineractLoanId);

        } catch (Exception e) {
            // Roll back to APPROVED so it can be retried
            application.setStatus(LoanApplicationStatus.APPROVED);
            loanApplicationRepository.save(application);
            log.error("Fineract disbursement failed for {}: {}", application.getReferenceNumber(), e.getMessage(), e);
            throw new BusinessException("Fineract disbursement failed: " + e.getMessage());
        }
    }
}
