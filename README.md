# SACCOS Loan Service

Handles the full SACCOS loan workflow before handing off to Apache Fineract for disbursement and repayment tracking.

## Loan Workflow

```
Member Applies
     ↓
Eligibility Check (automated)
  - Membership ≥ 6 months
  - Loan ≤ 3× share balance
  - No active defaulted loans
  - Within min/max loan limits
     ↓
Guarantor Selection (member picks ≥ 2 guarantors)
     ↓
Awaiting Guarantor Consent (SMS sent to each guarantor)
     ↓
Guarantors Confirmed
     ↓
Loan Officer Review (approve / reject)
     ↓
Committee Review (quorum vote — default 3 members, >50% approve)
     ↓
Chairperson Approval (final sign-off)
     ↓
APPROVED → Disburse to Fineract
     ↓
ACTIVE (Fineract tracks repayments, interest, arrears)
```

## Key Design Decision

`fineractLoanId` is `NULL` until the loan is fully approved through all SACCOS governance stages.  
Fineract is **never called** during the SACCOS workflow — only after chairperson approval.

## API Endpoints

| Method | Path | Role | Action |
|--------|------|------|--------|
| POST | `/api/loans/apply` | MEMBER | Submit application |
| GET | `/api/loans/{ref}` | Any | Get application |
| POST | `/api/loans/{id}/guarantors` | MEMBER | Add guarantor |
| POST | `/api/loans/{id}/guarantors/submit` | MEMBER | Send consent requests |
| POST | `/api/loans/{id}/guarantors/{gid}/consent` | Public (SMS link) | Guarantor consent |
| POST | `/api/loans/{id}/officer/approve` | LOAN_OFFICER | Officer approve |
| POST | `/api/loans/{id}/officer/reject` | LOAN_OFFICER | Officer reject |
| POST | `/api/loans/{id}/committee/vote` | COMMITTEE_MEMBER | Cast vote |
| POST | `/api/loans/{id}/chairperson/decide` | CHAIRPERSON | Final decision |
| POST | `/api/loans/{id}/disburse` | ADMIN | Push to Fineract |

## Configuration

All SACCOS rules are configurable via environment variables:

| Variable | Default | Description |
|----------|---------|-------------|
| `ELIGIBILITY_MIN_MONTHS` | 6 | Minimum membership months |
| `ELIGIBILITY_SHARES_MULTIPLIER` | 3 | Max loan = shares × multiplier |
| `ELIGIBILITY_MIN_LOAN` | 10000 | Minimum loan (TZS) |
| `ELIGIBILITY_MAX_LOAN` | 10000000 | Maximum loan (TZS) |
| `GUARANTOR_MIN_REQUIRED` | 2 | Minimum guarantors |
| `COMMITTEE_QUORUM` | 3 | Votes needed for committee decision |
| `COMMITTEE_THRESHOLD` | 0.5 | Approval rate needed (>50%) |

## Running locally

```bash
docker-compose up -d postgres rabbitmq redis
mvn spring-boot:run
```
