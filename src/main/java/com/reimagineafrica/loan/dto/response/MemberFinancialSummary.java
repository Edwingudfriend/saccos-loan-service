package com.reimagineafrica.loan.dto.response;

import lombok.*;

import java.math.BigDecimal;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MemberFinancialSummary {
    private String memberNumber;
    private String fullName;
    private String phone;
    private BigDecimal shareBalance;
    private BigDecimal savingsBalance;
    private Integer membershipMonths;
    private boolean hasDefaultedLoan;
    private Long fineractClientId;
}
