-- ================================================================
-- V2: Per-SACCO configuration table
-- ================================================================

CREATE TABLE sacco_configs (
    id                              BIGSERIAL PRIMARY KEY,
    sacco_code                      VARCHAR(50) NOT NULL UNIQUE,
    sacco_name                      VARCHAR(200) NOT NULL,
    active                          BOOLEAN NOT NULL DEFAULT TRUE,

    -- Eligibility rules
    min_membership_months           INT NOT NULL DEFAULT 6,
    loan_to_shares_multiplier       NUMERIC(5,2) NOT NULL DEFAULT 3.0,
    min_loan_amount                 NUMERIC(15,2) NOT NULL DEFAULT 10000,
    max_loan_amount                 NUMERIC(15,2) NOT NULL DEFAULT 10000000,
    max_active_loans                INT NOT NULL DEFAULT 1,

    -- Guarantor rules
    minimum_guarantors_required     INT NOT NULL DEFAULT 2,
    max_active_guarantees_per_member INT NOT NULL DEFAULT 3,

    -- Workflow steps
    loan_officer_review_required    BOOLEAN NOT NULL DEFAULT TRUE,
    committee_review_required       BOOLEAN NOT NULL DEFAULT TRUE,
    chairperson_approval_required   BOOLEAN NOT NULL DEFAULT TRUE,

    -- Committee rules
    committee_quorum_size           INT NOT NULL DEFAULT 3,
    committee_approval_threshold    NUMERIC(5,4) NOT NULL DEFAULT 0.5001,

    -- Emergency loan overrides
    emergency_loan_skips_committee  BOOLEAN NOT NULL DEFAULT FALSE,
    emergency_loan_min_guarantors   INT NOT NULL DEFAULT 1,
    emergency_loan_max_amount       NUMERIC(15,2) NOT NULL DEFAULT 500000,

    -- Notifications
    send_sms_on_each_step           BOOLEAN NOT NULL DEFAULT TRUE,
    guarantor_consent_expiry_hours  INT NOT NULL DEFAULT 48,

    -- Metadata
    created_at                      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by                      VARCHAR(200)
);

-- Add saccoCode to loan applications
ALTER TABLE loan_applications
    ADD COLUMN sacco_code VARCHAR(50) NOT NULL DEFAULT 'DEFAULT';

CREATE INDEX idx_sacco_configs_code ON sacco_configs(sacco_code);
CREATE INDEX idx_loan_apps_sacco ON loan_applications(sacco_code);

-- Seed default config (matches application.yml defaults)
INSERT INTO sacco_configs (
    sacco_code, sacco_name, active,
    min_membership_months, loan_to_shares_multiplier,
    min_loan_amount, max_loan_amount, max_active_loans,
    minimum_guarantors_required, max_active_guarantees_per_member,
    loan_officer_review_required, committee_review_required, chairperson_approval_required,
    committee_quorum_size, committee_approval_threshold,
    emergency_loan_skips_committee, emergency_loan_min_guarantors, emergency_loan_max_amount,
    send_sms_on_each_step, guarantor_consent_expiry_hours
) VALUES (
    'DEFAULT', 'Default SACCOS Configuration', TRUE,
    6, 3.0,
    10000, 10000000, 1,
    2, 3,
    TRUE, TRUE, TRUE,
    3, 0.5001,
    FALSE, 1, 500000,
    TRUE, 48
);
