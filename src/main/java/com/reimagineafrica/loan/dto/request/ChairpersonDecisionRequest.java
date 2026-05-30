package com.reimagineafrica.loan.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ChairpersonDecisionRequest {
    @NotNull private UUID chairpersonId;
    private boolean approved;
    private String notes;
}
