package com.reimagineafrica.loan.service;

import com.reimagineafrica.loan.entity.LoanApplication;
import com.reimagineafrica.loan.entity.LoanAuditLog;
import com.reimagineafrica.loan.enums.LoanApplicationStatus;
import com.reimagineafrica.loan.repository.LoanAuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LoanAuditService {

    private final LoanAuditLogRepository auditLogRepository;

    public void log(LoanApplication application,
                    LoanApplicationStatus fromStatus,
                    LoanApplicationStatus toStatus,
                    String action,
                    String performedBy,
                    String notes) {

        LoanAuditLog log = LoanAuditLog.builder()
                .loanApplication(application)
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .action(action)
                .performedBy(performedBy)
                .notes(notes)
                .build();

        auditLogRepository.save(log);
    }
}
