package com.reimagineafrica.loan.dto.request;

import com.reimagineafrica.loan.enums.LoanType;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LoanApplicationRequest {

    @NotNull
    private UUID memberId;

    @NotBlank
    private String memberNumber;

    @NotBlank
    private String memberName;

    @NotBlank
    private String memberPhone;

    @NotNull
    private LoanType loanType;

    @NotNull
    @DecimalMin("10000")
    private BigDecimal appliedAmount;

    @NotNull
    @Min(1) @Max(60)
    private Integer repaymentPeriodMonths;

    @NotNull
    @DecimalMin("0.5") @DecimalMax("5.0")
    private BigDecimal interestRatePerMonth;

    @NotBlank @Size(min = 10, max = 500)
    private String purpose;
}
