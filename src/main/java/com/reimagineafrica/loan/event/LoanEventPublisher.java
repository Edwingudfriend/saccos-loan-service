package com.reimagineafrica.loan.event;

import com.reimagineafrica.loan.entity.LoanApplication;
import com.reimagineafrica.loan.entity.LoanGuarantor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class LoanEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    private static final String EXCHANGE = "saccos.loan.events";

    public void publishEligibilityFailed(LoanApplication app, String reason) {
        publish("loan.eligibility.failed", Map.of(
                "referenceNumber", app.getReferenceNumber(),
                "memberId", app.getMemberId().toString(),
                "memberPhone", app.getMemberPhone(),
                "memberName", app.getMemberName(),
                "reason", reason
        ));
    }

    public void publishGuarantorConsentRequested(LoanApplication app, LoanGuarantor guarantor) {
        publish("loan.guarantor.consent.requested", Map.of(
                "referenceNumber", app.getReferenceNumber(),
                "guarantorId", guarantor.getId().toString(),
                "guarantorPhone", guarantor.getGuarantorPhone(),
                "guarantorName", guarantor.getGuarantorName(),
                "applicantName", app.getMemberName(),
                "loanAmount", app.getAppliedAmount().toString(),
                "guaranteeAmount", guarantor.getGuaranteeAmount().toString()
        ));
    }

    public void publishGuarantorDeclined(LoanApplication app) {
        publish("loan.guarantor.declined", Map.of(
                "referenceNumber", app.getReferenceNumber(),
                "memberId", app.getMemberId().toString(),
                "memberPhone", app.getMemberPhone()
        ));
    }

    public void publishReadyForOfficerReview(LoanApplication app) {
        publish("loan.officer.review.ready", Map.of(
                "referenceNumber", app.getReferenceNumber(),
                "loanAmount", app.getAppliedAmount().toString(),
                "memberId", app.getMemberId().toString()
        ));
    }

    public void publishReadyForCommittee(LoanApplication app) {
        publish("loan.committee.review.ready", Map.of(
                "referenceNumber", app.getReferenceNumber(),
                "loanAmount", app.getAppliedAmount().toString(),
                "memberName", app.getMemberName()
        ));
    }

    public void publishReadyForChairperson(LoanApplication app) {
        publish("loan.chairperson.approval.ready", Map.of(
                "referenceNumber", app.getReferenceNumber(),
                "loanAmount", app.getAppliedAmount().toString(),
                "memberName", app.getMemberName()
        ));
    }

    public void publishFullyApproved(LoanApplication app) {
        publish("loan.fully.approved", Map.of(
                "referenceNumber", app.getReferenceNumber(),
                "memberId", app.getMemberId().toString(),
                "memberPhone", app.getMemberPhone(),
                "memberName", app.getMemberName(),
                "loanAmount", app.getAppliedAmount().toString()
        ));
    }

    public void publishDisbursed(LoanApplication app) {
        publish("loan.disbursed", Map.of(
                "referenceNumber", app.getReferenceNumber(),
                "memberId", app.getMemberId().toString(),
                "memberPhone", app.getMemberPhone(),
                "memberName", app.getMemberName(),
                "loanAmount", app.getAppliedAmount().toString(),
                "fineractLoanId", app.getFineractLoanId().toString(),
                "disbursementDate", app.getFineractDisbursementDate().toString()
        ));
    }

    public void publishLoanRejected(LoanApplication app, String rejectedBy) {
        publish("loan.rejected", Map.of(
                "referenceNumber", app.getReferenceNumber(),
                "memberId", app.getMemberId().toString(),
                "memberPhone", app.getMemberPhone(),
                "memberName", app.getMemberName(),
                "rejectedBy", rejectedBy,
                "reason", app.getRejectionReason() != null ? app.getRejectionReason() : ""
        ));
    }

    private void publish(String routingKey, Map<String, String> payload) {
        try {
            rabbitTemplate.convertAndSend(EXCHANGE, routingKey, payload);
            log.debug("Published event {} for loan {}", routingKey, payload.get("referenceNumber"));
        } catch (Exception e) {
            log.error("Failed to publish event {}: {}", routingKey, e.getMessage());
        }
    }
}
