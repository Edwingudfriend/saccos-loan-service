package com.reimagineafrica.loan.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AddGuarantorRequest {
    @NotNull private UUID guarantorMemberId;
    @NotNull private BigDecimal guaranteeAmount;
}
