package com.myrtle.middleware.services.corporate;

import com.myrtle.middleware.repository.PendingCorporateDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records what has already been created on MyWealth during corporate onboarding.
 *
 * <p>Every write here runs in its own transaction ({@link Propagation#REQUIRES_NEW}) and commits
 * immediately. That is the entire point: the surrounding completion transaction may still roll back,
 * but MyWealth records cannot be un-created, so the ids must outlive that rollback. Without this a
 * retry would create a second corporate client - or, since the username is now taken, be refused
 * outright and leave the company permanently unable to onboard.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CorporateOnboardingCheckpoint {

    private final PendingCorporateDataRepository pendingCorporateDataRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordExternalClientId(String pendingId, String externalClientId) {
        pendingCorporateDataRepository.findById(pendingId).ifPresent(pending -> {
            pending.setExternalClientId(externalClientId);
            pendingCorporateDataRepository.save(pending);
            log.info("Checkpoint | corporate client recorded | pendingId={} | externalClientId={}",
                    pendingId, externalClientId);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordInvestmentId(String pendingId, String investmentId) {
        pendingCorporateDataRepository.findById(pendingId).ifPresent(pending -> {
            pending.setExternalInvestmentId(investmentId);
            pendingCorporateDataRepository.save(pending);
            log.info("Checkpoint | investment account recorded | pendingId={} | investmentId={}",
                    pendingId, investmentId);
        });
    }
}
