package com.myrtle.middleware.services.corporate;

import com.myrtle.middleware.repository.CorporateClientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class CorporateClientStartupSeeder implements ApplicationRunner {

    private final CorporateClientRepository corporateClientRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int correctedRoles = corporateClientRepository.fixMisspelledCorporateAccountRole();
        int backfilledRoles = corporateClientRepository.backfillNullCorporateAccountRole();
        int backfilledTiers = corporateClientRepository.backfillNullCorporateClientTier();

        if (correctedRoles > 0 || backfilledRoles > 0 || backfilledTiers > 0) {
            log.info(
                    "Corporate client startup backfill complete | correctedRoles={} | backfilledRoles={} | backfilledTiers={}",
                    correctedRoles,
                    backfilledRoles,
                    backfilledTiers
            );
        }
    }
}
