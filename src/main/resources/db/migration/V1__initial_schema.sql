-- ================================================================
-- V1: SACCOS Loan Service — Initial Schema
-- ================================================================

CREATE TABLE loan_applications (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reference_number            VARCHAR(30) NOT NULL UNIQUE,

    -- Member info
    member_id                   UUID NOT NULL,
    member_number               VARCHAR(50) NOT NULL,
    member_name                 VARCHAR(200) NOT NULL,
    member_phone                VARCHAR(20) NOT NULL,

    -- Loan details
    loan_type                   VARCHAR(30) NOT NULL,
    applied_amount              NUMERIC(15,2) NOT NULL,
    repayment_period_months     INT NOT NULL,
    interest_rate_per_month     NUMERIC(5,2) NOT NULL,
    purpose                     TEXT,

    -- Eligibility snapshot
    member_share_balance        NUMERIC(15,2),
    member_savings_balance      NUMERIC(15,2),
    max_eligible_amount         NUMERIC(15,2),
    has_active_defaulted_loan   BOOLEAN DEFAULT FALSE,
    membership_months           INT,

    -- Workflow state
    status                      VARCHAR(50) NOT NULL DEFAULT 'SUBMITTED',
    rejection_reason            TEXT,
    eligibility_failure_reason  TEXT,

    -- Officer
    assigned_loan_officer_id    UUID,
    loan_officer_notes          TEXT,
    loan_officer_reviewed_at    TIMESTAMP,

    -- Committee
    committee_reviewed_at       TIMESTAMP,
    committee_notes             TEXT,

    -- Chairperson
    chairperson_id              UUID,
    chairperson_notes           TEXT,
    chairperson_approved_at     TIMESTAMP,

    -- Fineract (NULL until disbursed)
    fineract_client_id          BIGINT,
    fineract_loan_id            BIGINT,
    fineract_disbursement_date  DATE,

    -- Metadata
    created_by                  VARCHAR(200),
    created_at                  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_loan_apps_member_id ON loan_applications(member_id);
CREATE INDEX idx_loan_apps_status ON loan_applications(status);
CREATE INDEX idx_loan_apps_officer ON loan_applications(assigned_loan_officer_id);

-- ----------------------------------------------------------------

CREATE TABLE loan_guarantors (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    loan_application_id         UUID NOT NULL REFERENCES loan_applications(id) ON DELETE CASCADE,

    guarantor_member_id         UUID NOT NULL,
    guarantor_member_number     VARCHAR(50) NOT NULL,
    guarantor_name              VARCHAR(200) NOT NULL,
    guarantor_phone             VARCHAR(20) NOT NULL,
    guarantee_amount            NUMERIC(15,2),
    guarantor_share_balance     NUMERIC(15,2),

    status                      VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    decline_reason              TEXT,

    consent_requested_at        TIMESTAMP,
    consent_responded_at        TIMESTAMP,
    consent_ip_address          VARCHAR(50),

    created_at                  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_guarantors_loan ON loan_guarantors(loan_application_id);
CREATE INDEX idx_guarantors_member ON loan_guarantors(guarantor_member_id);

-- ----------------------------------------------------------------

CREATE TABLE committee_votes (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    loan_application_id         UUID NOT NULL REFERENCES loan_applications(id) ON DELETE CASCADE,

    committee_member_id         UUID NOT NULL,
    committee_member_name       VARCHAR(200) NOT NULL,
    vote                        VARCHAR(20) NOT NULL,
    comment                     TEXT,
    voted_at                    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    UNIQUE (loan_application_id, committee_member_id)
);

CREATE INDEX idx_votes_loan ON committee_votes(loan_application_id);

-- ----------------------------------------------------------------

CREATE TABLE loan_audit_logs (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    loan_application_id         UUID NOT NULL REFERENCES loan_applications(id) ON DELETE CASCADE,

    from_status                 VARCHAR(50),
    to_status                   VARCHAR(50) NOT NULL,
    action                      VARCHAR(100) NOT NULL,
    performed_by                VARCHAR(200),
    notes                       TEXT,
    ip_address                  VARCHAR(50),
    created_at                  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_loan ON loan_audit_logs(loan_application_id);
CREATE INDEX idx_audit_created ON loan_audit_logs(created_at);
