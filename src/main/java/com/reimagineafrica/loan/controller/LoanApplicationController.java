package com.reimagineafrica.loan.controller;

import com.reimagineafrica.loan.dto.request.*;
import com.reimagineafrica.loan.dto.response.LoanApplicationResponse;
import com.reimagineafrica.loan.enums.LoanApplicationStatus;
import com.reimagineafrica.loan.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/loans")
@RequiredArgsConstructor
public class LoanApplicationController {

    private final LoanApplicationService loanApplicationService;
    private final GuarantorService guarantorService;
    private final CommitteeApprovalService committeeApprovalService;
    private final FineractDisbursementService fineractDisbursementService;

    // ── Member endpoints ─────────────────────────────────────────────

    @PostMapping("/apply")
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<LoanApplicationResponse> apply(
            @Valid @RequestBody LoanApplicationRequest request,
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(loanApplicationService.apply(request, userId));
    }

    @GetMapping("/{referenceNumber}")
    public ResponseEntity<LoanApplicationResponse> getByReference(
            @PathVariable String referenceNumber) {
        return ResponseEntity.ok(loanApplicationService.getByReference(referenceNumber));
    }

    @GetMapping("/member/{memberId}")
    @PreAuthorize("hasAnyRole('MEMBER', 'LOAN_OFFICER', 'ADMIN')")
    public ResponseEntity<Page<LoanApplicationResponse>> getByMember(
            @PathVariable UUID memberId, Pageable pageable) {
        return ResponseEntity.ok(loanApplicationService.getByMember(memberId, pageable));
    }

    @DeleteMapping("/{loanApplicationId}/withdraw")
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<Void> withdraw(
            @PathVariable UUID loanApplicationId,
            @RequestHeader("X-Member-Id") UUID memberId) {
        loanApplicationService.withdraw(loanApplicationId, memberId);
        return ResponseEntity.noContent().build();
    }

    // ── Guarantor endpoints ──────────────────────────────────────────

    @PostMapping("/{loanApplicationId}/guarantors")
    @PreAuthorize("hasAnyRole('MEMBER', 'LOAN_OFFICER')")
    public ResponseEntity<?> addGuarantor(
            @PathVariable UUID loanApplicationId,
            @Valid @RequestBody AddGuarantorRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(guarantorService.addGuarantor(loanApplicationId, request));
    }

    @PostMapping("/{loanApplicationId}/guarantors/submit")
    @PreAuthorize("hasAnyRole('MEMBER', 'LOAN_OFFICER')")
    public ResponseEntity<Void> submitForGuarantorConsent(
            @PathVariable UUID loanApplicationId) {
        guarantorService.submitForGuarantorConsent(loanApplicationId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{loanApplicationId}/guarantors/{guarantorId}/consent")
    public ResponseEntity<Void> recordGuarantorConsent(
            @PathVariable UUID loanApplicationId,
            @PathVariable UUID guarantorId,
            @RequestBody GuarantorConsentRequest request) {
        guarantorService.recordConsent(loanApplicationId, guarantorId, request);
        return ResponseEntity.ok().build();
    }

    // ── Loan Officer endpoints ───────────────────────────────────────

    @PostMapping("/{loanApplicationId}/officer/approve")
    @PreAuthorize("hasRole('LOAN_OFFICER')")
    public ResponseEntity<Void> officerApprove(
            @PathVariable UUID loanApplicationId,
            @RequestHeader("X-User-Id") UUID officerId,
            @RequestParam(required = false) String notes) {
        committeeApprovalService.officerApprove(loanApplicationId, officerId, notes);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{loanApplicationId}/officer/reject")
    @PreAuthorize("hasRole('LOAN_OFFICER')")
    public ResponseEntity<Void> officerReject(
            @PathVariable UUID loanApplicationId,
            @RequestHeader("X-User-Id") UUID officerId,
            @RequestParam String reason) {
        committeeApprovalService.officerReject(loanApplicationId, officerId, reason);
        return ResponseEntity.ok().build();
    }

    // ── Committee endpoints ──────────────────────────────────────────

    @PostMapping("/{loanApplicationId}/committee/vote")
    @PreAuthorize("hasRole('COMMITTEE_MEMBER')")
    public ResponseEntity<?> committeeVote(
            @PathVariable UUID loanApplicationId,
            @Valid @RequestBody CommitteeVoteRequest request) {
        return ResponseEntity.ok(committeeApprovalService.castCommitteeVote(loanApplicationId, request));
    }

    // ── Chairperson endpoints ────────────────────────────────────────

    @PostMapping("/{loanApplicationId}/chairperson/decide")
    @PreAuthorize("hasRole('CHAIRPERSON')")
    public ResponseEntity<Void> chairpersonDecide(
            @PathVariable UUID loanApplicationId,
            @Valid @RequestBody ChairpersonDecisionRequest request) {
        committeeApprovalService.chairpersonDecide(loanApplicationId, request);
        return ResponseEntity.ok().build();
    }

    // ── Admin / Operations endpoints ─────────────────────────────────

    @PostMapping("/{loanApplicationId}/disburse")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATIONS')")
    public ResponseEntity<Void> disburseToFineract(
            @PathVariable UUID loanApplicationId) {
        fineractDisbursementService.disburseToFineract(loanApplicationId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/status/{status}")
    @PreAuthorize("hasAnyRole('LOAN_OFFICER', 'COMMITTEE_MEMBER', 'CHAIRPERSON', 'ADMIN')")
    public ResponseEntity<Page<LoanApplicationResponse>> getByStatus(
            @PathVariable LoanApplicationStatus status, Pageable pageable) {
        return ResponseEntity.ok(loanApplicationService.getByStatus(status, pageable));
    }
}
