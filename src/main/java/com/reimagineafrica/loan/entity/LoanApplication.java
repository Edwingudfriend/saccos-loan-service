package com.reimagineafrica.loan.entity;

import com.reimagineafrica.loan.enums.LoanApplicationStatus;
import com.reimagineafrica.loan.enums.LoanType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "loan_applications")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LoanApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String referenceNumber; // e.g. LOAN-2026-00142

    // Member details (from member-service)
    @Column(nullable = false)
    private UUID memberId;

    @Column(nullable = false)
    private String memberNumber; // SACCO member number

    @Column(nullable = false)
    private String memberName;

    @Column(nullable = false)
    private String memberPhone;

    // Loan details
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LoanType loanType;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal appliedAmount;

    @Column(nullable = false)
    private Integer repaymentPeriodMonths;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal interestRatePerMonth; // e.g. 2.0

    private String purpose;

    // SACCOS eligibility snapshot (captured at time of application)
    @Column(precision = 15, scale = 2)
    private BigDecimal memberShareBalance;      // Total shares at time of apply

    @Column(precision = 15, scale = 2)
    private BigDecimal memberSavingsBalance;    // Total savings

    @Column(precision = 15, scale = 2)
    private BigDecimal maxEligibleAmount;       // 3x shares

    private Boolean hasActiveDefaultedLoan;
    private Integer membershipMonths;           // How long member has been active

    // Workflow state
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LoanApplicationStatus status;

    private String rejectionReason;
    private String eligibilityFailureReason;

    // Officer / committee tracking
    private UUID assignedLoanOfficerId;
    private String loanOfficerNotes;
    private LocalDateTime loanOfficerReviewedAt;

    private LocalDateTime committeeReviewedAt;
    private String committeeNotes;

    private UUID chairpersonId;
    private String chairpersonNotes;
    private LocalDateTime chairpersonApprovedAt;

    // Fineract integration — only populated after full approval + disbursement
    private Long fineractClientId;
    private Long fineractLoanId;
    private LocalDate fineractDisbursementDate;

    // Guarantors (min 2 required by default)
    @OneToMany(mappedBy = "loanApplication", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<LoanGuarantor> guarantors = new ArrayList<>();

    // Committee votes
    @OneToMany(mappedBy = "loanApplication", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<CommitteeVoteRecord> committeeVotes = new ArrayList<>();

    // Audit log
    @OneToMany(mappedBy = "loanApplication", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<LoanAuditLog> auditLogs = new ArrayList<>();

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    private String createdBy;

    // Which SACCO this loan belongs to — used to resolve per-SACCO config
    @Column(nullable = false)
    @Builder.Default
    private String saccoCode = "DEFAULT";

    // ── Helper methods ──────────────────────────────────────────────

    public boolean isFullyApproved() {
        return this.status == LoanApplicationStatus.APPROVED;
    }

    public boolean allGuarantorsConsented() {
        if (guarantors.isEmpty()) return false;
        return guarantors.stream()
                .allMatch(g -> g.getStatus() == com.reimagineafrica.loan.enums.GuarantorStatus.CONSENTED);
    }

    public boolean hasAnyGuarantorDeclined() {
        return guarantors.stream()
                .anyMatch(g -> g.getStatus() == com.reimagineafrica.loan.enums.GuarantorStatus.DECLINED);
    }
}
