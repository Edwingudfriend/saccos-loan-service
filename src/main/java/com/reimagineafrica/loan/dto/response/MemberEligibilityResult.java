package com.reimagineafrica.loan.dto.response;

import lombok.*;

import java.math.BigDecimal;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MemberEligibilityResult {
    private boolean passed;
    private String failureReason;
    private BigDecimal maxEligibleAmount;

    public static MemberEligibilityResult passed(BigDecimal maxAmount) {
        return MemberEligibilityResult.builder()
                .passed(true)
                .maxEligibleAmount(maxAmount)
                .build();
    }

    public static MemberEligibilityResult failed(String reason) {
        return MemberEligibilityResult.builder()
                .passed(false)
                .failureReason(reason)
                .build();
    }
}
