package com.reimagineafrica.loan.service;

import com.reimagineafrica.loan.client.MemberServiceClient;
import com.reimagineafrica.loan.dto.response.MemberEligibilityResult;
import com.reimagineafrica.loan.entity.LoanApplication;
import com.reimagineafrica.loan.enums.LoanApplicationStatus;
import com.reimagineafrica.loan.repository.LoanApplicationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * SACCOS loan eligibility rules engine.
 *
 * Rules enforced:
 * 1. Member must be active for at least 6 months
 * 2. Member share balance ≥ (loan amount / 3)  → max loan = 3x shares
 * 3. No active defaulted loans
 * 4. Maximum 1 active loan at a time (configurable)
 * 5. Minimum loan amount = 10,000 TZS
 * 6. Maximum loan amount = configured ceiling
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EligibilityService {

    private final MemberServiceClient memberServiceClient;
    private final LoanApplicationRepository loanApplicationRepository;
    private final LoanAuditService auditService;

    @Value("${saccos.eligibility.min-membership-months:6}")
    private int minMembershipMonths;

    @Value("${saccos.eligibility.loan-to-shares-multiplier:3}")
    private int loanToSharesMultiplier;

    @Value("${saccos.eligibility.min-loan-amount:10000}")
    private BigDecimal minLoanAmount;

    @Value("${saccos.eligibility.max-loan-amount:10000000}")
    private BigDecimal maxLoanAmount;

    @Value("${saccos.eligibility.max-active-loans:1}")
    private int maxActiveLoans;

    @Transactional
    public MemberEligibilityResult checkEligibility(LoanApplication application) {
        log.info("Running eligibility check for loan application {}", application.getReferenceNumber());

        // 1. Fetch current member data from member-service
        var memberData = memberServiceClient.getMemberFinancialSummary(application.getMemberId());

        // Snapshot member data at time of check
        application.setMemberShareBalance(memberData.getShareBalance());
        application.setMemberSavingsBalance(memberData.getSavingsBalance());
        application.setMembershipMonths(memberData.getMembershipMonths());
        application.setHasActiveDefaultedLoan(memberData.isHasDefaultedLoan());

        // 2. Membership duration check
        if (memberData.getMembershipMonths() < minMembershipMonths) {
            return fail(application, String.format(
                "Member has been active for only %d months. Minimum required: %d months.",
                memberData.getMembershipMonths(), minMembershipMonths
            ));
        }

        // 3. Loan amount bounds check
        if (application.getAppliedAmount().compareTo(minLoanAmount) < 0) {
            return fail(application, String.format(
                "Applied amount %.0f TZS is below minimum loan amount %.0f TZS.",
                application.getAppliedAmount(), minLoanAmount
            ));
        }

        if (application.getAppliedAmount().compareTo(maxLoanAmount) > 0) {
            return fail(application, String.format(
                "Applied amount %.0f TZS exceeds maximum allowed %.0f TZS.",
                application.getAppliedAmount(), maxLoanAmount
            ));
        }

        // 4. Share capital rule: max loan = shares × multiplier
        BigDecimal maxByShares = memberData.getShareBalance()
                .multiply(BigDecimal.valueOf(loanToSharesMultiplier));
        application.setMaxEligibleAmount(maxByShares);

        if (application.getAppliedAmount().compareTo(maxByShares) > 0) {
            return fail(application, String.format(
                "Applied amount %.0f TZS exceeds maximum eligible amount %.0f TZS " +
                "(based on share balance of %.0f TZS × %d).",
                application.getAppliedAmount(), maxByShares,
                memberData.getShareBalance(), loanToSharesMultiplier
            ));
        }

        // 5. No defaulted loans
        if (memberData.isHasDefaultedLoan()) {
            return fail(application, "Member has an active defaulted loan. Resolve existing default before applying.");
        }

        // 6. Max active loans check
        long activeLoanCount = loanApplicationRepository.findActiveLoansByMember(application.getMemberId()).size();
        if (activeLoanCount >= maxActiveLoans) {
            return fail(application, String.format(
                "Member already has %d active loan(s). Maximum allowed: %d.",
                activeLoanCount, maxActiveLoans
            ));
        }

        // All checks passed
        log.info("Eligibility check PASSED for application {}", application.getReferenceNumber());
        application.setStatus(LoanApplicationStatus.GUARANTOR_SELECTION);
        auditService.log(application, LoanApplicationStatus.ELIGIBILITY_CHECK,
                LoanApplicationStatus.GUARANTOR_SELECTION, "ELIGIBILITY_PASSED",
                "System", "All eligibility criteria met. Proceeding to guarantor selection.");

        return MemberEligibilityResult.passed(maxByShares);
    }

    private MemberEligibilityResult fail(LoanApplication application, String reason) {
        log.warn("Eligibility check FAILED for application {}: {}", application.getReferenceNumber(), reason);
        application.setStatus(LoanApplicationStatus.ELIGIBILITY_FAILED);
        application.setEligibilityFailureReason(reason);
        auditService.log(application, LoanApplicationStatus.ELIGIBILITY_CHECK,
                LoanApplicationStatus.ELIGIBILITY_FAILED, "ELIGIBILITY_FAILED",
                "System", reason);
        return MemberEligibilityResult.failed(reason);
    }
}
