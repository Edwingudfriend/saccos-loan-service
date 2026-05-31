package com.reimagineafrica.loan.service;

import com.reimagineafrica.loan.client.MemberServiceClient;
import com.reimagineafrica.loan.dto.response.MemberEligibilityResult;
import com.reimagineafrica.loan.entity.LoanApplication;
import com.reimagineafrica.loan.entity.SaccoConfig;
import com.reimagineafrica.loan.enums.LoanApplicationStatus;
import com.reimagineafrica.loan.enums.LoanType;
import com.reimagineafrica.loan.repository.LoanApplicationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * SACCOS loan eligibility rules engine.
 * Rules are resolved per-SACCO from SaccoConfig — no hardcoded values.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EligibilityService {

    private final MemberServiceClient memberServiceClient;
    private final LoanApplicationRepository loanApplicationRepository;
    private final LoanAuditService auditService;
    private final SaccoConfigService saccoConfigService;

    @Transactional
    public MemberEligibilityResult checkEligibility(LoanApplication application) {
        log.info("Running eligibility check for loan application {}", application.getReferenceNumber());

        // Resolve per-SACCO config
        SaccoConfig config = saccoConfigService.resolveConfig(application.getSaccoCode());

        // Use emergency loan rules if applicable
        boolean isEmergency = application.getLoanType() == LoanType.EMERGENCY;

        // Fetch current member data from member-service
        var memberData = memberServiceClient.getMemberFinancialSummary(application.getMemberId());

        // Snapshot member data at time of check
        application.setMemberShareBalance(memberData.getShareBalance());
        application.setMemberSavingsBalance(memberData.getSavingsBalance());
        application.setMembershipMonths(memberData.getMembershipMonths());
        application.setHasActiveDefaultedLoan(memberData.isHasDefaultedLoan());

        // 1. Membership duration check
        if (memberData.getMembershipMonths() < config.getMinMembershipMonths()) {
            return fail(application, String.format(
                "Member has been active for only %d months. Minimum required: %d months.",
                memberData.getMembershipMonths(), config.getMinMembershipMonths()
            ));
        }

        // 2. Loan amount bounds
        BigDecimal maxAmount = isEmergency ? config.getEmergencyLoanMaxAmount() : config.getMaxLoanAmount();
        if (application.getAppliedAmount().compareTo(config.getMinLoanAmount()) < 0) {
            return fail(application, String.format(
                "Applied amount %.0f TZS is below minimum loan amount %.0f TZS.",
                application.getAppliedAmount(), config.getMinLoanAmount()
            ));
        }

        if (application.getAppliedAmount().compareTo(maxAmount) > 0) {
            return fail(application, String.format(
                "Applied amount %.0f TZS exceeds maximum allowed %.0f TZS.",
                application.getAppliedAmount(), maxAmount
            ));
        }

        // 3. Share capital rule: max loan = shares × multiplier
        BigDecimal maxByShares = memberData.getShareBalance()
                .multiply(config.getLoanToSharesMultiplier());
        application.setMaxEligibleAmount(maxByShares);

        if (application.getAppliedAmount().compareTo(maxByShares) > 0) {
            return fail(application, String.format(
                "Applied amount %.0f TZS exceeds maximum eligible amount %.0f TZS " +
                "(based on share balance of %.0f TZS × %.1f).",
                application.getAppliedAmount(), maxByShares,
                memberData.getShareBalance(), config.getLoanToSharesMultiplier()
            ));
        }

        // 4. No defaulted loans
        if (memberData.isHasDefaultedLoan()) {
            return fail(application, "Member has an active defaulted loan. Resolve existing default before applying.");
        }

        // 5. Max active loans check
        long activeLoanCount = loanApplicationRepository.findActiveLoansByMember(application.getMemberId()).size();
        if (activeLoanCount >= config.getMaxActiveLoans()) {
            return fail(application, String.format(
                "Member already has %d active loan(s). Maximum allowed: %d.",
                activeLoanCount, config.getMaxActiveLoans()
            ));
        }

        // All checks passed
        log.info("Eligibility check PASSED for application {}", application.getReferenceNumber());
        application.setStatus(LoanApplicationStatus.GUARANTOR_SELECTION);
        auditService.log(application, LoanApplicationStatus.ELIGIBILITY_CHECK,
                LoanApplicationStatus.GUARANTOR_SELECTION, "ELIGIBILITY_PASSED",
                "System", String.format("All eligibility criteria met. Max eligible: %.0f TZS", maxByShares));

        return MemberEligibilityResult.passed(maxByShares);
    }

    private MemberEligibilityResult fail(LoanApplication application, String reason) {
        log.warn("Eligibility check FAILED for application {}: {}", application.getReferenceNumber(), reason);
        application.setStatus(LoanApplicationStatus.ELIGIBILITY_FAILED);
        application.setEligibilityFailureReason(reason);
        auditService.log(application, LoanApplicationStatus.ELIGIBILITY_CHECK,
                LoanApplicationStatus.ELIGIBILITY_FAILED, "ELIGIBILITY_FAILED", "System", reason);
        return MemberEligibilityResult.failed(reason);
    }
}
