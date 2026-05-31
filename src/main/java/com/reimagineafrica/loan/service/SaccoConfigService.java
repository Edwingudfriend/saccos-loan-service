package com.reimagineafrica.loan.service;

import com.reimagineafrica.loan.entity.SaccoConfig;
import com.reimagineafrica.loan.exception.BusinessException;
import com.reimagineafrica.loan.repository.SaccoConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SaccoConfigService {

    private final SaccoConfigRepository saccoConfigRepository;

    // Global defaults from application.yml — used if no per-SACCO config exists
    @Value("${saccos.eligibility.min-membership-months:6}")
    private int defaultMinMembershipMonths;

    @Value("${saccos.eligibility.loan-to-shares-multiplier:3}")
    private int defaultLoanToSharesMultiplier;

    @Value("${saccos.eligibility.min-loan-amount:10000}")
    private BigDecimal defaultMinLoanAmount;

    @Value("${saccos.eligibility.max-loan-amount:10000000}")
    private BigDecimal defaultMaxLoanAmount;

    @Value("${saccos.eligibility.max-active-loans:1}")
    private int defaultMaxActiveLoans;

    @Value("${saccos.guarantor.minimum-required:2}")
    private int defaultMinGuarantors;

    @Value("${saccos.guarantor.max-active-guarantees:3}")
    private int defaultMaxActiveGuarantees;

    @Value("${saccos.committee.quorum-size:3}")
    private int defaultQuorumSize;

    @Value("${saccos.committee.approval-threshold:0.5001}")
    private BigDecimal defaultApprovalThreshold;

    /**
     * Resolve config for a SACCO.
     * Returns per-SACCO config if it exists and is active.
     * Falls back to a default config built from application.yml values.
     */
    @Cacheable(value = "saccoConfig", key = "#saccoCode")
    public SaccoConfig resolveConfig(String saccoCode) {
        if (saccoCode == null || saccoCode.isBlank()) {
            log.warn("No SACCO code provided — using global defaults");
            return buildDefaultConfig();
        }

        return saccoConfigRepository.findBySaccoCodeAndActiveTrue(saccoCode)
                .orElseGet(() -> {
                    log.warn("No active config found for SACCO '{}' — using global defaults", saccoCode);
                    return buildDefaultConfig();
                });
    }

    // ── Admin CRUD ───────────────────────────────────────────────────

    @Transactional
    public SaccoConfig create(SaccoConfig config) {
        if (saccoConfigRepository.existsBySaccoCode(config.getSaccoCode())) {
            throw new BusinessException("Config already exists for SACCO: " + config.getSaccoCode());
        }
        validateConfig(config);
        SaccoConfig saved = saccoConfigRepository.save(config);
        log.info("Created SACCO config for: {}", config.getSaccoCode());
        return saved;
    }

    @Transactional
    @CacheEvict(value = "saccoConfig", key = "#saccoCode")
    public SaccoConfig update(String saccoCode, SaccoConfig updates, String updatedBy) {
        SaccoConfig existing = saccoConfigRepository.findBySaccoCode(saccoCode)
                .orElseThrow(() -> new BusinessException("SACCO config not found: " + saccoCode));

        validateConfig(updates);

        // Update all fields
        existing.setMinMembershipMonths(updates.getMinMembershipMonths());
        existing.setLoanToSharesMultiplier(updates.getLoanToSharesMultiplier());
        existing.setMinLoanAmount(updates.getMinLoanAmount());
        existing.setMaxLoanAmount(updates.getMaxLoanAmount());
        existing.setMaxActiveLoans(updates.getMaxActiveLoans());
        existing.setMinimumGuarantorsRequired(updates.getMinimumGuarantorsRequired());
        existing.setMaxActiveGuaranteesPerMember(updates.getMaxActiveGuaranteesPerMember());
        existing.setLoanOfficerReviewRequired(updates.getLoanOfficerReviewRequired());
        existing.setCommitteeReviewRequired(updates.getCommitteeReviewRequired());
        existing.setChairpersonApprovalRequired(updates.getChairpersonApprovalRequired());
        existing.setCommitteeQuorumSize(updates.getCommitteeQuorumSize());
        existing.setCommitteeApprovalThreshold(updates.getCommitteeApprovalThreshold());
        existing.setEmergencyLoanSkipsCommittee(updates.getEmergencyLoanSkipsCommittee());
        existing.setEmergencyLoanMinGuarantors(updates.getEmergencyLoanMinGuarantors());
        existing.setEmergencyLoanMaxAmount(updates.getEmergencyLoanMaxAmount());
        existing.setSendSmsOnEachStep(updates.getSendSmsOnEachStep());
        existing.setGuarantorConsentExpiryHours(updates.getGuarantorConsentExpiryHours());
        existing.setUpdatedBy(updatedBy);

        SaccoConfig saved = saccoConfigRepository.save(existing);
        log.info("Updated SACCO config for: {} by {}", saccoCode, updatedBy);
        return saved;
    }

    @Transactional
    @CacheEvict(value = "saccoConfig", key = "#saccoCode")
    public void deactivate(String saccoCode, String updatedBy) {
        SaccoConfig config = saccoConfigRepository.findBySaccoCode(saccoCode)
                .orElseThrow(() -> new BusinessException("SACCO config not found: " + saccoCode));
        config.setActive(false);
        config.setUpdatedBy(updatedBy);
        saccoConfigRepository.save(config);
        log.info("Deactivated SACCO config for: {}", saccoCode);
    }

    public SaccoConfig getByCode(String saccoCode) {
        return saccoConfigRepository.findBySaccoCode(saccoCode)
                .orElseThrow(() -> new BusinessException("SACCO config not found: " + saccoCode));
    }

    public List<SaccoConfig> getAll() {
        return saccoConfigRepository.findAll();
    }

    // ── Validation ───────────────────────────────────────────────────

    private void validateConfig(SaccoConfig config) {
        if (config.getMinLoanAmount().compareTo(config.getMaxLoanAmount()) >= 0) {
            throw new BusinessException("Min loan amount must be less than max loan amount.");
        }
        if (config.getCommitteeApprovalThreshold().compareTo(BigDecimal.ZERO) <= 0
                || config.getCommitteeApprovalThreshold().compareTo(BigDecimal.ONE) > 0) {
            throw new BusinessException("Committee approval threshold must be between 0 and 1.");
        }
        if (config.getCommitteeQuorumSize() < 1) {
            throw new BusinessException("Committee quorum must be at least 1.");
        }
        if (config.getMinimumGuarantorsRequired() < 0) {
            throw new BusinessException("Minimum guarantors cannot be negative.");
        }
    }

    // ── Default config from application.yml ─────────────────────────

    private SaccoConfig buildDefaultConfig() {
        return SaccoConfig.builder()
                .saccoCode("DEFAULT")
                .saccoName("Default Configuration")
                .active(true)
                .minMembershipMonths(defaultMinMembershipMonths)
                .loanToSharesMultiplier(BigDecimal.valueOf(defaultLoanToSharesMultiplier))
                .minLoanAmount(defaultMinLoanAmount)
                .maxLoanAmount(defaultMaxLoanAmount)
                .maxActiveLoans(defaultMaxActiveLoans)
                .minimumGuarantorsRequired(defaultMinGuarantors)
                .maxActiveGuaranteesPerMember(defaultMaxActiveGuarantees)
                .loanOfficerReviewRequired(true)
                .committeeReviewRequired(true)
                .chairpersonApprovalRequired(true)
                .committeeQuorumSize(defaultQuorumSize)
                .committeeApprovalThreshold(defaultApprovalThreshold)
                .emergencyLoanSkipsCommittee(false)
                .emergencyLoanMinGuarantors(1)
                .emergencyLoanMaxAmount(new BigDecimal("500000"))
                .sendSmsOnEachStep(true)
                .guarantorConsentExpiryHours(48)
                .build();
    }
}
