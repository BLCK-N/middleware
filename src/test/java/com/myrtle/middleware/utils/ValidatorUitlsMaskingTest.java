package com.myrtle.middleware.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Masking output is not only logged - it is persisted to corporate_clients.contacts_payload, which
 * carries the primary contact's password. Anything that escapes the mask is stored in plaintext
 * permanently, so these cases matter more than log hygiene.
 */
class ValidatorUitlsMaskingTest {

    private final ValidatorUitls validatorUitls = new ValidatorUitls(null);

    @Test
    void masksAPlainPassword() {
        String masked = validatorUitls.maskSensitiveFields("{\"password\":\"Secret123\"}");

        assertThat(masked).doesNotContain("Secret123");
    }

    @Test
    void masksAPlainPin() {
        String masked = validatorUitls.maskSensitiveFields("{\"pin\":\"1234\"}");

        assertThat(masked).doesNotContain("1234");
    }

    @Test
    void masksAPasswordContainingAnEscapedQuote() {
        // legal input: the password field allows any 8-64 characters
        String json = "{\"password\":\"pa\\\"ssword123\",\"email\":\"a@b.com\"}";

        String masked = validatorUitls.maskSensitiveFields(json);

        assertThat(masked).doesNotContain("ssword123");
    }

    @Test
    void masksAPasswordContainingABackslash() {
        // NB: assert on a distinctive value - the field name "password" contains "word"
        String json = "{\"password\":\"pass\\\\Secret9\",\"email\":\"a@b.com\"}";

        String masked = validatorUitls.maskSensitiveFields(json);

        assertThat(masked).doesNotContain("Secret9");
    }

    @Test
    void masksEveryContactInAnArray() {
        String json = "[{\"password\":\"FirstSecret\"},{\"password\":\"SecondSecret\"}]";

        String masked = validatorUitls.maskSensitiveFields(json);

        assertThat(masked).doesNotContain("FirstSecret").doesNotContain("SecondSecret");
    }

    @Test
    void leavesOtherFieldsIntact() {
        String json = "{\"password\":\"Secret123\",\"email\":\"owner@company.com\"}";

        String masked = validatorUitls.maskSensitiveFields(json);

        assertThat(masked).contains("owner@company.com");
    }

    @Test
    void handlesNullAndAbsentValues() {
        assertThat(validatorUitls.maskSensitiveFields("{\"password\":null}")).doesNotContain("Secret");
        assertThat(validatorUitls.maskSensitiveFields("{\"email\":\"a@b.com\"}")).contains("a@b.com");
    }
}
