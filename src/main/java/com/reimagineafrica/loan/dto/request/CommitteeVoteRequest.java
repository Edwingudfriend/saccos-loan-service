package com.reimagineafrica.loan.dto.request;

import com.reimagineafrica.loan.enums.CommitteeVote;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CommitteeVoteRequest {
    @NotNull private UUID committeeMemberId;
    @NotNull private String committeeMemberName;
    @NotNull private CommitteeVote vote;
    private String comment;
}
