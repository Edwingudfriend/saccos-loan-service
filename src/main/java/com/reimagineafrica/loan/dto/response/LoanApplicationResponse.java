package com.reimagineafrica.loan.dto.response;

import com.reimagineafrica.loan.enums.LoanApplicationStatus;
import com.reimagineafrica.loan.enums.LoanType;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LoanApplicationResponse {
    private UUID id;
    private String referenceNumber;
    private UUID memberId;
    private String memberName;
    private LoanType loanType;
    private BigDecimal appliedAmount;
    private BigDecimal maxEligibleAmount;
    private Integer repaymentPeriodMonths;
    private BigDecimal interestRatePerMonth;
    private String purpose;
    private LoanApplicationStatus status;
    private String eligibilityFailureReason;
    private String rejectionReason;
    private Integer guarantorCount;
    private Long fineractLoanId;
    private LocalDate fineractDisbursementDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
