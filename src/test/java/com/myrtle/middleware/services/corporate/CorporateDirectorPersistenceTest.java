package com.myrtle.middleware.services.corporate;

import com.myrtle.middleware.model.CorporateClient;
import com.myrtle.middleware.model.CorporateDirector;
import com.myrtle.middleware.model.PendingCorporateData;
import com.myrtle.middleware.model.PendingCorporateDirector;
import com.myrtle.middleware.repository.CorporateClientRepository;
import com.myrtle.middleware.repository.CorporateDirectorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The staged record cascades to its directors when it is deleted at completion, so these must be
 * copied to permanent records first or the account is left with no queryable director list.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CorporateDirectorPersistenceTest {

    @Mock private CorporateClientRepository corporateClientRepository;
    @Mock private CorporateDirectorRepository corporateDirectorRepository;

    private CorporateAccountService corporateAccountService;

    @BeforeEach
    void setUp() {
        corporateAccountService = new CorporateAccountService(
                null, corporateClientRepository, null, null, null, null, null, null, null, null,
                corporateDirectorRepository, null, null, null, null, null, null, null, null,
                org.mockito.Mockito.mock(com.myrtle.middleware.services.refer.ReferralService.class)
        );
        when(corporateClientRepository.save(any(CorporateClient.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private CorporateClient corporateClient() {
        CorporateClient client = new CorporateClient();
        client.setId("corp-id");
        return client;
    }

    private PendingCorporateDirector stagedDirector(String firstName, boolean beneficialOwner) {
        PendingCorporateDirector director = new PendingCorporateDirector();
        director.setFirstName(firstName);
        director.setLastName("Obi");
        director.setEmail(firstName.toLowerCase() + "@company.com");
        director.setPhoneNumber("2348012345678");
        director.setBvn("12345678901");
        director.setRole("CORP_DIRECTOR");
        director.setBeneficialOwner(beneficialOwner);
        director.setBirthDate("1985-03-11");
        director.setCity("Lagos");
        return director;
    }

    private PendingCorporateData pendingWith(PendingCorporateDirector... directors) {
        PendingCorporateData pending = new PendingCorporateData();
        pending.setId("pending-id");
        pending.setEmail("owner@company.com");
        pending.setCompanyName("Acme Limited");
        pending.replaceDirectors(List.of(directors));
        return pending;
    }

    @Test
    void directorsArePromotedToPermanentRecords() {
        corporateAccountService.applyPendingOnboardingData(
                corporateClient(), pendingWith(stagedDirector("Ada", true), stagedDirector("John", false)));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CorporateDirector>> captor = ArgumentCaptor.forClass(List.class);
        verify(corporateDirectorRepository).saveAll(captor.capture());

        List<CorporateDirector> saved = captor.getValue();
        assertThat(saved).hasSize(2);
        assertThat(saved).allSatisfy(d -> assertThat(d.getCorporateClient().getId()).isEqualTo("corp-id"));
        assertThat(saved).extracting(CorporateDirector::getFirstName).containsExactly("Ada", "John");
    }

    @Test
    void everyCollectedFieldIsCarriedAcross() {
        corporateAccountService.applyPendingOnboardingData(
                corporateClient(), pendingWith(stagedDirector("Ada", true)));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CorporateDirector>> captor = ArgumentCaptor.forClass(List.class);
        verify(corporateDirectorRepository).saveAll(captor.capture());

        CorporateDirector saved = captor.getValue().getFirst();
        assertThat(saved.getFirstName()).isEqualTo("Ada");
        assertThat(saved.getLastName()).isEqualTo("Obi");
        assertThat(saved.getEmail()).isEqualTo("ada@company.com");
        assertThat(saved.getPhoneNumber()).isEqualTo("2348012345678");
        assertThat(saved.getBvn()).isEqualTo("12345678901");
        assertThat(saved.getRole()).isEqualTo("CORP_DIRECTOR");
        assertThat(saved.isBeneficialOwner()).isTrue();
        assertThat(saved.getBirthDate()).isEqualTo("1985-03-11");
        assertThat(saved.getCity()).isEqualTo("Lagos");
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void aRetryDoesNotDuplicateDirectorsAlreadyRecorded() {
        when(corporateDirectorRepository.existsByCorporateClientId("corp-id")).thenReturn(true);

        corporateAccountService.applyPendingOnboardingData(
                corporateClient(), pendingWith(stagedDirector("Ada", true)));

        verify(corporateDirectorRepository, never()).saveAll(any());
    }

    @Test
    void anOnboardingWithNoDirectorsDoesNotFail() {
        corporateAccountService.applyPendingOnboardingData(corporateClient(), pendingWith());

        verify(corporateDirectorRepository, never()).saveAll(any());
    }
}
