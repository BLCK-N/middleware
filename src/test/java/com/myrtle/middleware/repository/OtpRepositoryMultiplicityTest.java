package com.myrtle.middleware.repository;

import com.myrtle.middleware.model.UserOtp;
import com.myrtle.middleware.model.converter.EncryptedStringConverter;
import com.myrtle.middleware.utils.FieldEncryptor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OTP rows are created per request and never deleted, so an email accumulates them. This pins what
 * findOtpByEmailIgnoreCase actually does once that happens.
 */
@DataJpaTest
@Import({FieldEncryptor.class, EncryptedStringConverter.class})
@EntityScan(basePackages = "com.myrtle.middleware.model")
@TestPropertySource(properties = {
        "app.encryption.key=Zml4bWUtbG9jYWwtb25seS1rZXktbm90LWZvci1wcm9kIQ==",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=dGVzdC1zZWNyZXQtdGhhdC1pcy1sb25nLWVub3VnaC1mb3ItaG1hYw==",
        "DB_URL=unused",
        "DB_USERNAME=unused",
        "DB_PASSWORD=unused",
        "ENVIRONMENT=test"
})
class OtpRepositoryMultiplicityTest {

    @Autowired private OtpRepository otpRepository;

    private void saveOtp(String reference, boolean verified, LocalDateTime generated) {
        UserOtp otp = new UserOtp();
        otp.setEmail("owner@company.com");
        otp.setOtp("hashed");
        otp.setOtpReference(reference);
        otp.setVerified(verified);
        otp.setTimeGenerated(generated);
        otpRepository.saveAndFlush(otp);
    }

    // The deprecated findOtpByEmailIgnoreCase - which threw IncorrectResultSizeDataAccessException
    // once an email had more than one OTP row - has been removed from OtpRepository entirely.
    // findFirstByEmailIgnoreCaseOrderByTimeGeneratedDesc replaced it; see the test below.

    @Test
    void theReplacementLookupReturnsTheNewestRowInstead() {
        saveOtp("ref-1", true, LocalDateTime.now().minusHours(2));
        saveOtp("ref-2", false, LocalDateTime.now());

        assertThat(otpRepository.findFirstByEmailIgnoreCaseOrderByTimeGeneratedDesc("owner@company.com"))
                .isPresent()
                .get()
                .extracting(UserOtp::getOtpReference)
                .isEqualTo("ref-2");
    }

    @Test
    void allRowsForAnEmailCanBeRetired() {
        saveOtp("ref-1", true, LocalDateTime.now().minusHours(2));
        saveOtp("ref-2", false, LocalDateTime.now());

        assertThat(otpRepository.findAllByEmailIgnoreCase("owner@company.com")).hasSize(2);

        otpRepository.deleteAll(otpRepository.findAllByEmailIgnoreCase("owner@company.com"));

        assertThat(otpRepository.findAllByEmailIgnoreCase("owner@company.com")).isEmpty();
    }
}
