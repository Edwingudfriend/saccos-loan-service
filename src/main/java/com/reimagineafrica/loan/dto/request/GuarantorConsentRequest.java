package com.reimagineafrica.loan.dto.request;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class GuarantorConsentRequest {
    private boolean consenting;
    private String declineReason;
}
