package com.myrtle.middleware.dtos.request;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The inbound corporate request carries a plaintext PIN and password. They must deserialise from a
 * client request but must never be written back out - while the OUTBOUND MyWealth payload still
 * needs them, or client creation upstream breaks.
 */
class CreateCorporateClientRequestSerializationTest {

    // deliberately not a substring of any other field (the BVN below starts "1234...")
    private static final String PIN = "8675";
    private static final String PASSWORD = "Password123!";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void pinAndPasswordAreNeverSerialisedOut() throws Exception {
        String json = objectMapper.writeValueAsString(sampleRequest());

        assertThat(json).doesNotContain(PIN);
        assertThat(json).doesNotContain(PASSWORD);
        assertThat(json).doesNotContain("\"pin\"");
        assertThat(json).doesNotContain("\"password\"");

        // the rest of the payload is still there
        assertThat(json).contains("Myrtle Test Corporate Client");
    }

    @Test
    void pinAndPasswordStillDeserialiseFromAnIncomingRequest() throws Exception {
        String incoming = """
                {
                  "businessStructure": "MULTI_MEMBER_LLC",
                  "businessName": "Myrtle Test Corporate Client",
                  "pin": "%s",
                  "primaryContact": { "password": "%s" }
                }
                """.formatted(PIN, PASSWORD);

        CreateCorporateClientRequest parsed =
                objectMapper.readValue(incoming, CreateCorporateClientRequest.class);

        assertThat(parsed.pin()).isEqualTo(PIN);
        assertThat(parsed.primaryContact().password()).isEqualTo(PASSWORD);
    }

    @Test
    void outboundMyWealthPayloadStillCarriesTheCredentials() throws Exception {
        CreateCorporateClientRequest.ExternalPayload externalPayload =
                sampleRequest().toExternalRequest("group-id", null);

        String json = objectMapper.writeValueAsString(externalPayload);

        assertThat(json).contains(PASSWORD);
        assertThat(json).contains(PIN);
    }

    // validateProfileFields runs the SAME normalisers toExternalRequest uses, so the credentials
    // step can reject a bad value before /complete would.

    @Test
    void validateProfileFieldsAcceptsRecognisedValuesAndSynonyms() {
        assertThatCode(() -> CreateCorporateClientRequest.validateProfileFields(
                "LLC",          // businessStructure synonym -> MULTI_MEMBER_LLC
                "Mr",           // title synonym -> MR
                "BUSINESS_INCOME", // sourceOfWealth synonym -> BUSINESS
                "MODERATE",     // investmentExperience synonym -> INTERMEDIATE
                "MEDIUM",       // riskTolerance synonym -> MODERATE
                "GROWTH",       // investmentObjective synonym -> AGGRESSIVE_GROWTH
                "5000000", "1000000", "500000"))
                .doesNotThrowAnyException();
    }

    @Test
    void validateProfileFieldsIgnoresBlankValues() {
        assertThatCode(() -> CreateCorporateClientRequest.validateProfileFields(
                null, null, null, null, null, null, null, null, null))
                .doesNotThrowAnyException();
    }

    @Test
    void validateProfileFieldsRejectsAnUnrecognisedInvestmentExperience() {
        assertThatThrownBy(() -> CreateCorporateClientRequest.validateProfileFields(
                null, null, null, "SUPER_EXPERT", null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("investmentExperience");
    }

    @Test
    void validateProfileFieldsRejectsAnUnrecognisedRiskTolerance() {
        assertThatThrownBy(() -> CreateCorporateClientRequest.validateProfileFields(
                null, null, null, null, "YOLO", null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("riskTolerance");
    }

    private CreateCorporateClientRequest sampleRequest() {
        return new CreateCorporateClientRequest(
                "MULTI_MEMBER_LLC",
                "Myrtle Test Corporate Client",
                "corporateclient@example.com",
                "2348012345678",
                "NGN",
                new CreateCorporateClientRequest.AddressPayload(
                        "12 Adeola Odeku Street", "Victoria Island", "Lagos", "Lagos", "100001", "Nigeria"),
                "2020-01-15",
                "BVN",
                "2030-12-31",
                "12345678901",
                "Software Developer",
                "Nigerian",
                PIN,
                new CreateCorporateClientRequest.ContactPayload(
                        "DIRECTOR", "SINGLE", "MALE", "Mr",
                        "David", "Oluwaseun", "Areegbe",
                        "david@example.com", "2348012345678", "2347012345678",
                        new CreateCorporateClientRequest.ContactAddressPayload(
                                "22 Admiralty Way", "Lekki Phase 1", "Lagos", "Lagos", "100001", "Nigeria"),
                        "davidareegbe", true, PASSWORD, "1998-05-20",
                        null, null, "2024-01-01", "NIMC", "Lagos", "Adebayo", "Osun", "Ilesa West",
                        "1000000-5000000", "1000000-5000000", "500000-2000000", "Business Income",
                        "INTERMEDIATE", "MEDIUM", "Capital Growth",
                        "Qube Wave Technologies", "Lagos, Nigeria"),
                null,
                List.of(),
                null
        );
    }
}
