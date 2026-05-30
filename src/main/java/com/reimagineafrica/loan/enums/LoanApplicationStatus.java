package com.reimagineafrica.loan.enums;

/**
 * Full SACCOS loan workflow states.
 *
 * Flow:
 * DRAFT → SUBMITTED → ELIGIBILITY_CHECK → GUARANTOR_SELECTION →
 * GUARANTORS_CONFIRMED → LOAN_OFFICER_REVIEW → COMMITTEE_REVIEW →
 * CHAIRPERSON_APPROVAL → APPROVED → DISBURSED_TO_FINERACT → ACTIVE
 *
 * Rejection can happen at: ELIGIBILITY_CHECK, LOAN_OFFICER_REVIEW,
 * COMMITTEE_REVIEW, CHAIRPERSON_APPROVAL
 */
public enum LoanApplicationStatus {

    // Member is filling in the application
    DRAFT,

    // Member has submitted — eligibility checks start
    SUBMITTED,

    // System is running eligibility rules (shares, membership, defaults)
    ELIGIBILITY_CHECK,

    // Eligibility failed — application rejected at system level
    ELIGIBILITY_FAILED,

    // Eligible — member is selecting guarantors
    GUARANTOR_SELECTION,

    // Waiting for guarantors to consent
    AWAITING_GUARANTOR_CONSENT,

    // One or more guarantors declined
    GUARANTOR_DECLINED,

    // All guarantors have confirmed
    GUARANTORS_CONFIRMED,

    // Loan officer is reviewing the application
    LOAN_OFFICER_REVIEW,

    // Loan officer rejected
    LOAN_OFFICER_REJECTED,

    // Loan officer approved — going to committee
    COMMITTEE_REVIEW,

    // Committee rejected (quorum voted no)
    COMMITTEE_REJECTED,

    // Committee approved — going to chairperson
    CHAIRPERSON_APPROVAL,

    // Chairperson rejected
    CHAIRPERSON_REJECTED,

    // Fully approved by all levels — ready to disburse
    APPROVED,

    // Being pushed to Fineract
    DISBURSING_TO_FINERACT,

    // Live in Fineract — repayments tracked there
    ACTIVE,

    // Member withdrew the application
    WITHDRAWN
}
