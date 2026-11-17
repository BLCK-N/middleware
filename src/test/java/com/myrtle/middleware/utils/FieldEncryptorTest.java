package com.myrtle.middleware.utils;

import com.myrtle.middleware.model.converter.EncryptedStringConverter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FieldEncryptorTest {

    private static final String KEY = "Zml4bWUtbG9jYWwtb25seS1rZXktbm90LWZvci1wcm9kIQ==";

    private final FieldEncryptor encryptor = new FieldEncryptor(KEY);

    @Test
    void roundTripsAValue() {
        String encrypted = encryptor.encrypt("Password123!");

        assertThat(encrypted).startsWith("enc:v1:");
        assertThat(encrypted).doesNotContain("Password123!");
        assertThat(encryptor.decrypt(encrypted)).isEqualTo("Password123!");
    }

    @Test
    void theSameInputEncryptsDifferentlyEachTime() {
        String first = encryptor.encrypt("1234");
        String second = encryptor.encrypt("1234");

        // random IV per value - identical PINs must not produce identical ciphertext
        assertThat(first).isNotEqualTo(second);
        assertThat(encryptor.decrypt(first)).isEqualTo("1234");
        assertThat(encryptor.decrypt(second)).isEqualTo("1234");
    }

    @Test
    void nullAndBlankValuesPassStraightThrough() {
        assertThat(encryptor.encrypt(null)).isNull();
        assertThat(encryptor.encrypt("")).isEmpty();
        assertThat(encryptor.decrypt(null)).isNull();
        assertThat(encryptor.decrypt("")).isEmpty();
    }

    @Test
    void encryptingAnAlreadyEncryptedValueIsANoOp() {
        String once = encryptor.encrypt("1234");
        String twice = encryptor.encrypt(once);

        assertThat(twice).isEqualTo(once);
        assertThat(encryptor.decrypt(twice)).isEqualTo("1234");
    }

    @Test
    void legacyPlaintextRowsAreStillReadable() {
        // rows written before encryption existed carry no version prefix
        assertThat(encryptor.decrypt("plaintext-from-an-old-row")).isEqualTo("plaintext-from-an-old-row");
    }

    @Test
    void aDifferentKeyCannotDecrypt() {
        String encrypted = encryptor.encrypt("Password123!");
        FieldEncryptor otherKey = new FieldEncryptor("a-completely-different-secret");

        assertThatThrownBy(() -> otherKey.decrypt(encrypted))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("key may have changed");
    }

    @Test
    void tamperedCiphertextIsRejectedRatherThanReturningGarbage() {
        String encrypted = encryptor.encrypt("1234");
        String tampered = encrypted.substring(0, encrypted.length() - 4) + "AAAA";

        assertThatThrownBy(() -> encryptor.decrypt(tampered))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aMissingKeyFailsFastAtStartup() {
        assertThatThrownBy(() -> new FieldEncryptor("  "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.encryption.key");
    }

    @Test
    void theJpaConverterEncryptsOnWriteAndDecryptsOnRead() {
        EncryptedStringConverter converter = new EncryptedStringConverter(encryptor);

        String stored = converter.convertToDatabaseColumn("Password123!");

        assertThat(stored).doesNotContain("Password123!");
        assertThat(converter.convertToEntityAttribute(stored)).isEqualTo("Password123!");
    }
}
