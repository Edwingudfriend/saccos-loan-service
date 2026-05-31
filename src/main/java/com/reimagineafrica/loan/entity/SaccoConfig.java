package com.reimagineafrica.loan.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Per-SACCO configuration for loan workflow rules.
 *
 * Each SACCO onboarded gets one row here.
 * Rules are resolved at runtime — no code change or redeploy needed
 * when a SACCO changes their policy.
 */
@Entity
@Table(name = "sacco_configs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SaccoConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String saccoCode;           // e.g. "KISASA", "MWANANCHI"

    @Column(nullable = false)
    private String saccoName;           // Display name

    @Column(nullable = false)
    private boolean active;

    // ── Eligibility Rules ────────────────────────────────────────────

    @Column(nullable = false)
    @Builder.Default
    private Integer minMembershipMonths = 6;

    @Column(nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal loanToSharesMultiplier = new BigDecimal("3.0");
    // Max loan = member shares × this multiplier

    @Column(nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal minLoanAmount = new BigDecimal("10000");

    @Column(nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal maxLoanAmount = new BigDecimal("10000000");

    @Column(nullable = false)
    @Builder.Default
    private Integer maxActiveLoans = 1;

    // ── Guarantor Rules ──────────────────────────────────────────────

    @Column(nullable = false)
    @Builder.Default
    private Integer minimumGuarantorsRequired = 2;

    @Column(nullable = false)
    @Builder.Default
    private Integer maxActiveGuaranteesPerMember = 3;
    // How many loans one member can guarantee at once

    // ── Workflow Steps ───────────────────────────────────────────────
    // Allows small SACCOs to skip steps they don't use

    @Column(nullable = false)
    @Builder.Default
    private Boolean loanOfficerReviewRequired = true;

    @Column(nullable = false)
    @Builder.Default
    private Boolean committeeReviewRequired = true;

    @Column(nullable = false)
    @Builder.Default
    private Boolean chairpersonApprovalRequired = true;

    // ── Committee Rules ──────────────────────────────────────────────

    @Column(nullable = false)
    @Builder.Default
    private Integer committeeQuorumSize = 3;
    // Minimum votes needed before a decision is made

    @Column(nullable = false, precision = 5, scale = 4)
    @Builder.Default
    private BigDecimal committeeApprovalThreshold = new BigDecimal("0.5001");
    // >50% approve votes = approved

    // ── Loan Type Overrides ──────────────────────────────────────────
    // Emergency loans often have relaxed rules

    @Column(nullable = false)
    @Builder.Default
    private Boolean emergencyLoanSkipsCommittee = false;

    @Column(nullable = false)
    @Builder.Default
    private Integer emergencyLoanMinGuarantors = 1;

    @Column(nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal emergencyLoanMaxAmount = new BigDecimal("500000");

    // ── Notifications ────────────────────────────────────────────────

    @Column(nullable = false)
    @Builder.Default
    private Boolean sendSmsOnEachStep = true;

    @Column(nullable = false)
    @Builder.Default
    private Integer guarantorConsentExpiryHours = 48;
    // Auto-expire guarantor consent requests after N hours

    // ── Metadata ─────────────────────────────────────────────────────

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    private String updatedBy;
}
