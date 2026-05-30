package com.reimagineafrica.loan.scheduler;

import com.reimagineafrica.loan.event.LoanEventPublisher;
import com.reimagineafrica.loan.repository.LoanApplicationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class LoanWorkflowScheduler {

    private final LoanApplicationRepository loanApplicationRepository;
    private final LoanEventPublisher eventPublisher;

    /**
     * Send reminder to guarantors who haven't responded in 24 hours.
     * Runs every 6 hours.
     */
    @Scheduled(cron = "0 0 */6 * * ?")
    public void sendGuarantorReminders() {
        var pendingLoans = loanApplicationRepository.findAwaitingGuarantorConsent();

        pendingLoans.forEach(loan -> {
            loan.getGuarantors().stream()
                    .filter(g -> g.getStatus() ==
                            com.reimagineafrica.loan.enums.GuarantorStatus.PENDING)
                    .forEach(g -> {
                        log.info("Sending guarantor reminder for loan {} to {}",
                                loan.getReferenceNumber(), g.getGuarantorName());
                        eventPublisher.publishGuarantorConsentRequested(loan, g);
                    });
        });

        log.info("Guarantor reminder check complete. {} loans awaiting consent.", pendingLoans.size());
    }
}
