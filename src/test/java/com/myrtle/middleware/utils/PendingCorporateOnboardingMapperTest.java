package com.myrtle.middleware.utils;

import com.myrtle.middleware.dtos.request.CreateCorporateClientRequest;
import com.myrtle.middleware.exceptions.FunctionException;
import com.myrtle.middleware.model.PendingCorporateData;
import com.myrtle.middleware.model.PendingCorporateDirector;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * MyWealth documents contact.birthDate as required on EVERY contact and rejects the whole submission
 * if any one is missing it - confirmed live: a director with no birthDate produced a 400
 * ("contact[].birthDate: must not be null") that blocked the entire onboarding, not just that
 * director.
 *
 * <p>The primary contact carries the company's incorporation date, per MyWealth's own note that
 * birthDate "should hold the incorporation date for corporate accounts" - our primary contact is the
 * account's anchor contact (the only one with login access). A director missing their own birthDate
 * defaults to that same incorporation date rather than being sent as null. Never falls back to the
 * representative's personal date of birth.
 */
class PendingCorporateOnboardingMapperTest {

    private PendingCorporateData validPending() {
        PendingCorporateData pending = new PendingCorporateData();
        pending.setCompanyName("Acme Ventures Limited");
        pending.setEmail("owner@acme.com");
        pending.setPhoneNumber("2348012345678");
        pending.setBvn("12345678901");
        pending.setPin("1234");
        pending.setPassword("Password123!");
        pending.setFirstName("David");
        pending.setLastName("Areegbe");
        // representative's OWN date of birth - deliberately different from the incorporation date,
        // so a test asserting on the incorporation date fails loudly if this leaks through instead
        pending.setDate("1985-03-11");
        pending.setRegistrationDate(OffsetDateTime.parse("2019-04-12T00:00:00Z"));
        pending.setHeadOfficeAddress("22 Admiralty Way");
        pending.setCity("Lagos");
        pending.setState("Lagos");
        return pending;
    }

    private PendingCorporateDirector director(String firstName, String birthDate) {
        PendingCorporateDirector director = new PendingCorporateDirector();
        director.setFirstName(firstName);
        director.setLastName("Obi");
        director.setEmail(firstName.toLowerCase() + "@acme.com");
        director.setPhoneNumber("2348022222222");
        director.setBvn("22345678901");
        director.setBirthDate(birthDate);
        // A real director collected at the /directors step always carries at least one of these -
        // set both here so this fixture doesn't accidentally look like the thin-duplicate case below.
        director.setTitle("MR");
        director.setGender("MALE");
        return director;
    }

    private PendingCorporateDirector directorWithBvn(String firstName, String bvn, boolean beneficialOwner) {
        PendingCorporateDirector director = director(firstName, "1990-06-15");
        director.setBvn(bvn);
        director.setBeneficialOwner(beneficialOwner);
        return director;
    }

    /**
     * Mirrors a real duplicate seen live: a stray second director record for the representative,
     * carrying a different (bad) BVN than the representative's own and missing title and gender
     * entirely - the two signals used to tell it apart from a genuine second director.
     */
    private PendingCorporateDirector thinDuplicateOfRepresentative(String email, String bvn) {
        PendingCorporateDirector director = new PendingCorporateDirector();
        director.setFirstName("David");
        director.setLastName("Areegbe");
        director.setEmail(email);
        director.setPhoneNumber("2348012345678");
        director.setBvn(bvn);
        director.setBirthDate("1985-03-11");
        return director;
    }

    @Test
    void primaryContactBirthDateIsTheIncorporationDateNotTheRepresentativesOwnDob() {
        CreateCorporateClientRequest request =
                PendingCorporateOnboardingMapper.toCreateCorporateClientRequest(validPending());

        assertThat(request.primaryContact().birthDate()).isEqualTo("2019-04-12");
        assertThat(request.primaryContact().birthDate()).isNotEqualTo("1985-03-11");
    }

    @Test
    void theClientLevelAndContactLevelIncorporationDatesAreTheSameValue() {
        CreateCorporateClientRequest request =
                PendingCorporateOnboardingMapper.toCreateCorporateClientRequest(validPending());

        assertThat(request.businessIncDate()).isEqualTo(request.primaryContact().birthDate());
    }

    @Test
    void missingIncorporationDateStillBlocksCreation() {
        PendingCorporateData pending = validPending();
        pending.setRegistrationDate(null);

        assertThatThrownBy(() -> PendingCorporateOnboardingMapper.toCreateCorporateClientRequest(pending))
                .isInstanceOf(FunctionException.class)
                .hasMessageContaining("incorporation date");
    }

    @Test
    void aDirectorsOwnBirthDateIsSentAsGiven() {
        PendingCorporateData pending = validPending();
        pending.replaceDirectors(List.of(director("Ada", "1990-06-15")));

        CreateCorporateClientRequest request =
                PendingCorporateOnboardingMapper.toCreateCorporateClientRequest(pending);

        assertThat(request.directors().get(0).birthDate()).isEqualTo("1990-06-15");
    }

    @Test
    void aDirectorWithNoBirthDateGetsTheIncorporationDateNotNullAndNotTheRepresentatives() {
        PendingCorporateData pending = validPending();
        pending.replaceDirectors(List.of(director("John", null)));

        CreateCorporateClientRequest request =
                PendingCorporateOnboardingMapper.toCreateCorporateClientRequest(pending);

        String sent = request.directors().get(0).birthDate();
        // MyWealth 400s the whole submission if any contact.birthDate is absent - confirmed live -
        // so this must never be null.
        assertThat(sent).isEqualTo("2019-04-12");
        assertThat(sent).isNotEqualTo("1985-03-11"); // representative's own DOB - never used
    }

    @Test
    void creationDoesNotFailWhenADirectorHasNoBirthDate() {
        PendingCorporateData pending = validPending();
        pending.replaceDirectors(List.of(director("John", null)));

        CreateCorporateClientRequest request =
                PendingCorporateOnboardingMapper.toCreateCorporateClientRequest(pending);

        assertThat(request.directors()).hasSize(1);
    }

    @Test
    void anUnparseableDirectorBirthDateFallsBackToTheIncorporationDate() {
        PendingCorporateData pending = validPending();
        pending.replaceDirectors(List.of(director("Ada", "not-a-date")));

        CreateCorporateClientRequest request =
                PendingCorporateOnboardingMapper.toCreateCorporateClientRequest(pending);

        assertThat(request.directors().get(0).birthDate()).isEqualTo("2019-04-12");
    }

    @Test
    void multipleDirectorsEachKeepTheirOwnDateAndOnlyMissingOnesDefault() {
        PendingCorporateData pending = validPending();
        pending.replaceDirectors(List.of(
                director("Ada", "1990-06-15"),
                director("John", null),
                director("Jack", "1978-11-02")
        ));

        CreateCorporateClientRequest request =
                PendingCorporateOnboardingMapper.toCreateCorporateClientRequest(pending);

        assertThat(request.directors()).extracting(CreateCorporateClientRequest.ContactPayload::birthDate)
                .containsExactly("1990-06-15", "2019-04-12", "1978-11-02");
    }

    @Test
    void primaryContactUsernameIsAlwaysTheirEmailRegardlessOfAnyStoredUsername() {
        PendingCorporateData pending = validPending();
        pending.setUsername("some-other-handle");

        CreateCorporateClientRequest request =
                PendingCorporateOnboardingMapper.toCreateCorporateClientRequest(pending);

        assertThat(request.primaryContact().username()).isEqualTo("owner@acme.com");
    }

    @Test
    void aDirectorRecordThatIsActuallyTheRepresentativeIsNotResentAsASecondCorpDirector() {
        PendingCorporateData pending = validPending();
        // Same BVN as the representative (validPending() sets "12345678901") - this director record
        // is the representative themselves, re-collected during the directors step.
        pending.replaceDirectors(List.of(directorWithBvn("David", "12345678901", false)));

        CreateCorporateClientRequest request =
                PendingCorporateOnboardingMapper.toCreateCorporateClientRequest(pending);

        // MyWealth rejects the whole submission as a duplicate director if the same BVN appears
        // twice under CORP_DIRECTOR - the representative is already the primary contact.
        assertThat(request.directors()).isEmpty();
    }

    @Test
    void aThinDuplicateOfTheRepresentativeWithAMismatchedBvnIsStillDiscarded() {
        PendingCorporateData pending = validPending();
        // Same email as the representative but a DIFFERENT (bad/placeholder) BVN than
        // validPending()'s "12345678901" - the BVN match alone would miss this one, so title and
        // gender being both absent is what identifies it as a stray duplicate, not a real director.
        pending.replaceDirectors(List.of(thinDuplicateOfRepresentative("owner@acme.com", "99999999999")));

        CreateCorporateClientRequest request =
                PendingCorporateOnboardingMapper.toCreateCorporateClientRequest(pending);

        assertThat(request.directors()).isEmpty();
    }

    @Test
    void aThinDuplicateThatIsAlsoFlaggedBeneficialOwnerStillOnlyProducesTheBeneficialOwnerContact() {
        PendingCorporateData pending = validPending();
        PendingCorporateDirector duplicate = thinDuplicateOfRepresentative("owner@acme.com", "99999999999");
        duplicate.setBeneficialOwner(true);
        pending.replaceDirectors(List.of(duplicate));

        CreateCorporateClientRequest request =
                PendingCorporateOnboardingMapper.toCreateCorporateClientRequest(pending);

        assertThat(request.directors()).extracting(CreateCorporateClientRequest.ContactPayload::role)
                .containsExactly("BENEFICIAL_OWNER");
    }

    @Test
    void aRepresentativeFlaggedAsBeneficialOwnerProducesExactlyOneExtraContact() {
        PendingCorporateData pending = validPending();
        pending.replaceDirectors(List.of(directorWithBvn("David", "12345678901", true)));

        CreateCorporateClientRequest request =
                PendingCorporateOnboardingMapper.toCreateCorporateClientRequest(pending);

        // Exactly one BENEFICIAL_OWNER contact - never a duplicate CORP_DIRECTOR alongside it.
        assertThat(request.directors()).hasSize(1);
        assertThat(request.directors().get(0).role()).isEqualTo("BENEFICIAL_OWNER");
    }

    @Test
    void aDistinctDirectorWhoIsAlsoABeneficialOwnerStillProducesBothContacts() {
        PendingCorporateData pending = validPending();
        // Different BVN from the representative - a genuinely separate person.
        pending.replaceDirectors(List.of(directorWithBvn("Ada", "22345678901", true)));

        CreateCorporateClientRequest request =
                PendingCorporateOnboardingMapper.toCreateCorporateClientRequest(pending);

        assertThat(request.directors()).extracting(CreateCorporateClientRequest.ContactPayload::role)
                .containsExactly("CORP_DIRECTOR", "BENEFICIAL_OWNER");
    }

    @Test
    void thePrimaryContactsUsernameIsNeverPrefixed() {
        CreateCorporateClientRequest request =
                PendingCorporateOnboardingMapper.toCreateCorporateClientRequest(validPending());

        assertThat(request.primaryContact().username()).isEqualTo("owner@acme.com");
    }

    // The username uniqueness rule is applied when the payload is finalised for MyWealth
    // (toExternalRequest), because that is where the same person can end up on two contacts - a
    // director also flagged as beneficial owner, or the primary contact duplicated as the
    // beneficial owner when nobody else is one. Only the login contact keeps the raw email.

    private List<String> externalUsernames(PendingCorporateData pending) {
        return PendingCorporateOnboardingMapper.toCreateCorporateClientRequest(pending)
                .toExternalRequest("group-id", null)
                .contact().stream()
                .map(CreateCorporateClientRequest.ExternalContactPayload::username)
                .toList();
    }

    @Test
    void thePrimaryContactDuplicatedAsBeneficialOwnerGetsADistinctUsername() {
        // No directors and none flagged as beneficial owner - toExternalRequest duplicates the
        // primary contact as the beneficial owner. This is the exact live collision: both contacts
        // carried the same raw email as username.
        List<String> usernames = externalUsernames(validPending());

        assertThat(usernames).containsExactly("owner@acme.com", "beneficial-owner-owner@acme.com");
    }

    @Test
    void aDirectorWhoIsAlsoABeneficialOwnerGetsTwoDistinctUsernamesNotAConflict() {
        PendingCorporateData pending = validPending();
        // Same person, same email, on both their CORP_DIRECTOR and BENEFICIAL_OWNER contacts.
        pending.replaceDirectors(List.of(directorWithBvn("Ada", "22345678901", true)));

        // primary (login) keeps its email; the director's two roles each get a distinct prefix.
        assertThat(externalUsernames(pending))
                .containsExactly("owner@acme.com", "corp-director-ada@acme.com", "beneficial-owner-ada@acme.com");
    }

    @Test
    void twoDistinctDirectorsSharingAnEmailStillCollide() {
        PendingCorporateData pending = validPending();
        PendingCorporateDirector second = director("Jack", "1978-11-02");
        second.setEmail("ada@acme.com"); // shared company address, different people
        second.setBvn("33345678901");
        pending.replaceDirectors(List.of(director("Ada", "1990-06-15"), second));

        // Same role, same email - the role prefix alone can't disambiguate these, so this remains a
        // known gap: two genuinely distinct directors sharing an email still collide upstream. The
        // trailing beneficial-owner entry is the primary contact's BO copy (no director was flagged
        // as one), and it stays distinct.
        assertThat(externalUsernames(pending))
                .containsExactly(
                        "owner@acme.com",
                        "corp-director-ada@acme.com",
                        "corp-director-ada@acme.com",
                        "beneficial-owner-owner@acme.com");
    }
}
