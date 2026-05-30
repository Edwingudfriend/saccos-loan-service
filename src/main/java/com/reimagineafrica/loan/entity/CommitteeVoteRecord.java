package com.reimagineafrica.loan.entity;

import com.reimagineafrica.loan.enums.CommitteeVote;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "committee_votes")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CommitteeVoteRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_application_id", nullable = false)
    private LoanApplication loanApplication;

    @Column(nullable = false)
    private UUID committeeMemberId;

    @Column(nullable = false)
    private String committeeMemberName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CommitteeVote vote;

    private String comment;

    @CreationTimestamp
    private LocalDateTime votedAt;
}
