package com.reimagineafrica.loan.entity;

import com.reimagineafrica.loan.enums.LoanApplicationStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "loan_audit_logs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LoanAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_application_id", nullable = false)
    private LoanApplication loanApplication;

    @Enumerated(EnumType.STRING)
    private LoanApplicationStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LoanApplicationStatus toStatus;

    @Column(nullable = false)
    private String action; // e.g. "ELIGIBILITY_PASSED", "GUARANTOR_CONSENTED"

    private String performedBy;
    private String notes;
    private String ipAddress;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
