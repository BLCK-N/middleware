package com.myrtle.middleware.dtos.request;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PatchClientRequestSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void omitsNullsAndSendsPhonesAndPostCodesAsStrings() throws Exception {
        PatchClientRequest request = new PatchClientRequest();
        request.setEmail("david@example.com");
        request.setNotificationEmail("david@example.com");
        request.setMobileNo("+2348012345678");

        PatchClientRequest.Address address = new PatchClientRequest.Address();
        address.setType("PRIMARY");
        address.setAddressLine1("22 Admiralty Way");
        address.setPostCode("100001");
        address.setCountry("NG");

        PatchClientRequest.Contact contact = new PatchClientRequest.Contact();
        contact.setId("01a05d51-233a-7d10-acbf-98e38c95594e");
        contact.setRole("INDV_OWNER");
        contact.setFirstName("David");
        contact.setLastName("Areegbe");
        contact.setEmail("david@example.com");
        contact.setMobileNo("+2348012345678");
        contact.setFinIdNo("12345678901");
        contact.setAddress(address);

        request.setAddress(List.of(address));
        request.setContact(List.of(contact));

        JsonNode json = mapper.readTree(mapper.writeValueAsString(request));

        assertThat(json.has("officeNo")).isFalse();
        assertThat(json.has("clientType")).isFalse();
        assertThat(json.has("kycTierId")).isFalse();
        assertThat(json.has("notificationEnabled")).isFalse();
        assertThat(json.get("mobileNo").isTextual()).isTrue();
        assertThat(json.get("mobileNo").asText()).isEqualTo("+2348012345678");
        assertThat(json.get("address").get(0).get("postCode").isTextual()).isTrue();
        assertThat(json.get("address").get(0).get("postCode").asText()).isEqualTo("100001");
        assertThat(json.get("contact").get(0).has("officeNo")).isFalse();
        assertThat(json.get("contact").get(0).has("birthDate")).isFalse();
        assertThat(json.get("contact").get(0).get("mobileNo").isTextual()).isTrue();
        assertThat(json.get("contact").get(0).get("finIdNo").asText()).isEqualTo("12345678901");
    }
}
