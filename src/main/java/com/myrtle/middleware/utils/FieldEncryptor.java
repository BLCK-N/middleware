package com.myrtle.middleware.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Reversible encryption for values that must be recoverable in plaintext later - specifically the
 * corporate onboarding PIN and password, which MyWealth requires verbatim at client creation and so
 * cannot be hashed.
 *
 * <p>AES-256-GCM, random 12-byte IV per value, stored as {@code enc:v1:base64(iv || ciphertext+tag)}.
 * The version prefix lets {@link #decrypt(String)} pass through values that were written before
 * encryption existed, so legacy rows keep working.
 *
 * <p>This is encryption at rest, not a substitute for hashing. Anything holding the key can recover
 * the plaintext - which is exactly why user-login passwords are still bcrypt-hashed elsewhere.
 */
@Slf4j
@Component
public class FieldEncryptor {

    private static final String PREFIX = "enc:v1:";
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final SecretKey secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public FieldEncryptor(@Value("${app.encryption.key}") String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalStateException(
                    "app.encryption.key is not configured. Set FIELD_ENCRYPTION_KEY to a strong random secret.");
        }
        this.secretKey = deriveKey(key);
    }

    /**
     * Accepts either a base64-encoded 32-byte key or an arbitrary passphrase, normalising both to a
     * 256-bit key via SHA-256 so the property format never causes a startup failure.
     */
    private SecretKey deriveKey(String key) {
        try {
            byte[] raw;
            try {
                byte[] decoded = Base64.getDecoder().decode(key.trim());
                raw = decoded.length == 32 ? decoded : sha256(key);
            } catch (IllegalArgumentException notBase64) {
                raw = sha256(key);
            }
            return new SecretKeySpec(raw, ALGORITHM);
        } catch (Exception e) {
            throw new IllegalStateException("Could not derive the field encryption key", e);
        }
    }

    private byte[] sha256(String value) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * @return the encrypted envelope, or the input unchanged when null/blank or already encrypted
     */
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty() || isEncrypted(plaintext)) {
            return plaintext;
        }

        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);

            return PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("Could not encrypt value", e);
        }
    }

    /**
     * @return the plaintext; values without the version prefix are returned as-is (rows written
     * before encryption was introduced)
     */
    public String decrypt(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        if (!isEncrypted(value)) {
            log.warn("Reading an unencrypted legacy value | length={}", value.length());
            return value;
        }

        try {
            byte[] combined = Base64.getDecoder().decode(value.substring(PREFIX.length()));

            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);

            byte[] ciphertext = new byte[combined.length - IV_LENGTH];
            System.arraycopy(combined, IV_LENGTH, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));

            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Could not decrypt value - the encryption key may have changed", e);
        }
    }

    public boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }
}
