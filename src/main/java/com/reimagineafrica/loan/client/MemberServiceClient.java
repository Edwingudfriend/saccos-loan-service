package com.reimagineafrica.loan.client;

import com.reimagineafrica.loan.dto.response.MemberFinancialSummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class MemberServiceClient {

    private final WebClient memberWebClient;

    /**
     * Fetch member's financial summary for eligibility checks.
     * Calls member-service: GET /api/members/{id}/financial-summary
     */
    public MemberFinancialSummary getMemberFinancialSummary(UUID memberId) {
        log.info("Fetching financial summary for member {}", memberId);

        return memberWebClient.get()
                .uri("/api/members/" + memberId + "/financial-summary")
                .retrieve()
                .bodyToMono(MemberFinancialSummary.class)
                .block();
    }
}
