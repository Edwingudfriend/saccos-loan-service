package com.reimagineafrica.loan.client;

import com.reimagineafrica.loan.enums.LoanType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class FineractClient {

    private final WebClient fineractWebClient;

    @Value("${fineract.loan-product-id.emergency:1}")
    private Long emergencyProductId;

    @Value("${fineract.loan-product-id.development:2}")
    private Long developmentProductId;

    @Value("${fineract.loan-product-id.education:3}")
    private Long educationProductId;

    @Value("${fineract.loan-product-id.business:4}")
    private Long businessProductId;

    @Value("${fineract.loan-product-id.housing:5}")
    private Long housingProductId;

    private static final DateTimeFormatter FINERACT_DATE = DateTimeFormatter.ofPattern("dd MMMM yyyy");

    /**
     * Get existing Fineract client by external ID (memberId), or create if not found.
     */
    public Long getOrCreateClient(UUID memberId, String fullName, String phone) {
        String externalId = "SACCOS-MEMBER-" + memberId.toString();

        // Try find existing
        try {
            Map response = fineractWebClient.get()
                    .uri("/clients?externalId=" + externalId)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response != null && (Integer) response.get("totalFilteredRecords") > 0) {
                var pageItems = (java.util.List<?>) response.get("pageItems");
                var client = (Map<?, ?>) pageItems.get(0);
                Long clientId = ((Number) client.get("id")).longValue();
                log.info("Found existing Fineract client {} for member {}", clientId, memberId);
                return clientId;
            }
        } catch (Exception e) {
            log.warn("Could not find Fineract client for member {}: {}", memberId, e.getMessage());
        }

        // Create new client
        String[] names = fullName.trim().split(" ", 2);
        Map<String, Object> payload = new HashMap<>();
        payload.put("officeId", 1);
        payload.put("firstname", names[0]);
        payload.put("lastname", names.length > 1 ? names[1] : names[0]);
        payload.put("externalId", externalId);
        payload.put("mobileNo", phone);
        payload.put("active", true);
        payload.put("activationDate", LocalDate.now().format(FINERACT_DATE));
        payload.put("dateFormat", "dd MMMM yyyy");
        payload.put("locale", "en");

        Map response = fineractWebClient.post()
                .uri("/clients")
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        Long clientId = ((Number) response.get("clientId")).longValue();
        log.info("Created Fineract client {} for member {}", clientId, memberId);
        return clientId;
    }

    public Long createLoan(Long clientId, BigDecimal amount, Integer termMonths,
                           BigDecimal interestRate, LoanType loanType, String externalRef) {

        Map<String, Object> payload = new HashMap<>();
        payload.put("clientId", clientId);
        payload.put("productId", resolveProductId(loanType));
        payload.put("principal", amount);
        payload.put("loanTermFrequency", termMonths);
        payload.put("loanTermFrequencyType", 2); // months
        payload.put("numberOfRepayments", termMonths);
        payload.put("repaymentEvery", 1);
        payload.put("repaymentFrequencyType", 2); // months
        payload.put("interestRatePerPeriod", interestRate);
        payload.put("interestType", 0); // declining balance
        payload.put("interestCalculationPeriodType", 1);
        payload.put("amortizationType", 1); // equal installments
        payload.put("transactionProcessingStrategyCode", "mifos-standard-strategy");
        payload.put("expectedDisbursementDate", LocalDate.now().format(FINERACT_DATE));
        payload.put("submittedOnDate", LocalDate.now().format(FINERACT_DATE));
        payload.put("externalId", externalRef);
        payload.put("dateFormat", "dd MMMM yyyy");
        payload.put("locale", "en");

        Map response = fineractWebClient.post()
                .uri("/loans")
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        Long loanId = ((Number) response.get("loanId")).longValue();
        log.info("Created Fineract loan {} for external ref {}", loanId, externalRef);
        return loanId;
    }

    public void approveLoan(Long fineractLoanId, String note) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("approvedOnDate", LocalDate.now().format(FINERACT_DATE));
        payload.put("note", "Approved via SACCOS workflow. Ref: " + note);
        payload.put("dateFormat", "dd MMMM yyyy");
        payload.put("locale", "en");

        fineractWebClient.post()
                .uri("/loans/" + fineractLoanId + "?command=approve")
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        log.info("Approved Fineract loan {}", fineractLoanId);
    }

    public void disburseLoan(Long fineractLoanId, LocalDate date) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("actualDisbursementDate", date.format(FINERACT_DATE));
        payload.put("dateFormat", "dd MMMM yyyy");
        payload.put("locale", "en");

        fineractWebClient.post()
                .uri("/loans/" + fineractLoanId + "?command=disburse")
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        log.info("Disbursed Fineract loan {} on {}", fineractLoanId, date);
    }

    private Long resolveProductId(LoanType loanType) {
        return switch (loanType) {
            case EMERGENCY -> emergencyProductId;
            case EDUCATION -> educationProductId;
            case BUSINESS -> businessProductId;
            case HOUSING -> housingProductId;
            default -> developmentProductId;
        };
    }
}
