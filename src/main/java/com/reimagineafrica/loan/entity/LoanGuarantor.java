package com.reimagineafrica.loan.entity;

import com.reimagineafrica.loan.enums.GuarantorStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "loan_guarantors")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LoanGuarantor {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_application_id", nullable = false)
    private LoanApplication loanApplication;

    @Column(nullable = false)
    private UUID guarantorMemberId;

    @Column(nullable = false)
    private String guarantorMemberNumber;

    @Column(nullable = false)
    private String guarantorName;

    @Column(nullable = false)
    private String guarantorPhone;

    // Amount this guarantor is liable for
    @Column(precision = 15, scale = 2)
    private BigDecimal guaranteeAmount;

    // Guarantor's own share balance (must be sufficient to guarantee)
    @Column(precision = 15, scale = 2)
    private BigDecimal guarantorShareBalance;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private GuarantorStatus status = GuarantorStatus.PENDING;

    private String declineReason;

    // Consent tracking
    private LocalDateTime consentRequestedAt;
    private LocalDateTime consentRespondedAt;
    private String consentIpAddress;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
